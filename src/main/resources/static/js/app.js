/* OrderTracker — live order tracking (vanilla JS, no libraries) */
(() => {
    "use strict";

    const API = "";                      // same origin
    const TOKEN_KEY = "ot.accessToken";
    const NAME_KEY = "ot.fullName";

    let socket = null;
    let reconnectDelay = 1000;           // exponential backoff
    let reconnectTimer = null;
    let pingTimer = null;
    let manualClose = false;

    const $ = (id) => document.getElementById(id);
    const el = {
        loginView: $("loginView"), appView: $("appView"),
        loginForm: $("loginForm"), loginError: $("loginError"),
        email: $("email"), password: $("password"),
        registerForm: $("registerForm"),
        tabLogin: $("tabLogin"), tabRegister: $("tabRegister"),
        wsStatus: $("wsStatus"), whoami: $("whoami"), logoutBtn: $("logoutBtn"),
        orders: $("orders"), feed: $("feed"),
        refreshBtn: $("refreshBtn"), clearFeedBtn: $("clearFeedBtn"),
        orderForm: $("orderForm"), orderMsg: $("orderMsg"),
    };

    const token = () => localStorage.getItem(TOKEN_KEY);

    /* ---------------- HTTP ---------------- */

    async function api(path, options = {}) {
        const res = await fetch(API + path, {
            ...options,
            headers: {
                "Content-Type": "application/json",
                ...(token() ? { Authorization: "Bearer " + token() } : {}),
                ...(options.headers || {}),
            },
        });
        if (res.status === 401 || res.status === 403) {
            logout();
            throw new Error("Session expired — please sign in again");
        }
        const body = res.status === 204 ? null : await res.json().catch(() => null);
        if (!res.ok) {
            throw new Error((body && (body.message || body.error)) || "Request failed: " + res.status);
        }
        return body;
    }

    /* ---------------- auth ---------------- */

    function showTab(which) {
        const isLogin = which === "login";
        el.tabLogin.classList.toggle("tab-active", isLogin);
        el.tabRegister.classList.toggle("tab-active", !isLogin);
        el.loginForm.classList.toggle("hidden", !isLogin);
        el.registerForm.classList.toggle("hidden", isLogin);
        el.loginError.classList.add("hidden");
    }

    el.tabLogin.addEventListener("click", () => showTab("login"));
    el.tabRegister.addEventListener("click", () => showTab("register"));

    function authError(message) {
        el.loginError.className = "error";
        el.loginError.textContent = message;
    }

    async function signIn(email, password) {
        const data = await api("/api/auth/login", {
            method: "POST",
            body: JSON.stringify({ email: email, password: password }),
        });
        localStorage.setItem(TOKEN_KEY, data.accessToken);
        localStorage.setItem(NAME_KEY, data.fullName || email);
        enterApp();
    }

    el.loginForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        el.loginError.classList.add("hidden");
        try {
            await signIn(el.email.value, el.password.value);
        } catch (err) {
            authError(err.message);
        }
    });

    el.registerForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        el.loginError.classList.add("hidden");

        const email = $("regEmail").value;
        const password = $("regPassword").value;

        if (password !== $("regPassword2").value) {
            authError("Passwords do not match");
            return;
        }
        try {
            // /api/auth/register returns 201 with no body, so sign in right after
            await api("/api/auth/register", {
                method: "POST",
                body: JSON.stringify({
                    fullName: $("regFullName").value,
                    email: email,
                    password: password,
                }),
            });
            el.registerForm.reset();
            await signIn(email, password);
        } catch (err) {
            authError(err.message);
        }
    });

    el.logoutBtn.addEventListener("click", () => logout());

    function logout() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(NAME_KEY);
        closeSocket();
        el.appView.classList.add("hidden");
        el.loginView.classList.remove("hidden");
        el.whoami.classList.add("hidden");
        el.logoutBtn.classList.add("hidden");
        showTab("login");
        setWsStatus("off", "Disconnected");
    }

    function enterApp() {
        el.loginView.classList.add("hidden");
        el.appView.classList.remove("hidden");
        el.whoami.textContent = localStorage.getItem(NAME_KEY) || "";
        el.whoami.classList.remove("hidden");
        el.logoutBtn.classList.remove("hidden");
        loadOrders();
        connectSocket();
    }

    /* ---------------- WebSocket ---------------- */

    function connectSocket() {
        if (!token()) return;
        closeSocket();
        manualClose = false;

        const scheme = location.protocol === "https:" ? "wss" : "ws";
        // Browsers cannot set headers on a WebSocket handshake, so the JWT rides in the query string.
        const url = scheme + "://" + location.host + "/ws/orders?token=" + encodeURIComponent(token());

        setWsStatus("off", "Connecting…");
        socket = new WebSocket(url);

        socket.onopen = () => {
            reconnectDelay = 1000;
            setWsStatus("on", "Live");
            pingTimer = setInterval(() => {
                if (socket && socket.readyState === WebSocket.OPEN) socket.send("ping");
            }, 30000);
        };

        socket.onmessage = (evt) => {
            let msg;
            try { msg = JSON.parse(evt.data); } catch (e) { return; }
            if (msg.type === "PONG" || msg.type === "CONNECTED") return;
            handleOrderEvent(msg);
        };

        socket.onclose = () => {
            clearInterval(pingTimer);
            if (manualClose || !token()) { setWsStatus("off", "Disconnected"); return; }
            setWsStatus("err", "Dropped — retrying in " + Math.round(reconnectDelay / 1000) + "s");
            reconnectTimer = setTimeout(connectSocket, reconnectDelay);
            reconnectDelay = Math.min(reconnectDelay * 2, 30000);
        };

        socket.onerror = () => setWsStatus("err", "Error");
    }

    function closeSocket() {
        manualClose = true;
        clearTimeout(reconnectTimer);
        clearInterval(pingTimer);
        if (socket) { socket.onclose = null; socket.close(); socket = null; }
    }

    function setWsStatus(kind, text) {
        el.wsStatus.className = "pill pill-" + kind;
        el.wsStatus.textContent = text;
    }

    function handleOrderEvent(evt) {
        pushFeed(evt);
        if (evt.type === "ORDER_CREATED") {
            loadOrders();                       // reload so the new row shows up
        } else {
            updateOrderRow(evt);                // swap the badge in place
        }
    }

    /* ---------------- render ---------------- */

    async function loadOrders() {
        try {
            const page = await api("/api/orders?page=0&size=20&sort=createdAt,desc");
            renderOrders(page.content || []);
        } catch (err) {
            el.orders.innerHTML = '<p class="error">' + escapeHtml(err.message) + "</p>";
        }
    }

    function renderOrders(list) {
        if (!list.length) {
            el.orders.innerHTML = '<p class="muted">No orders yet.</p>';
            return;
        }
        el.orders.innerHTML = list.map((o) => `
            <div class="order" data-order-id="${o.id}">
                <div>
                    <div class="order-no">${escapeHtml(o.orderNumber)}</div>
                    <div class="order-meta">${fmtMoney(o.totalAmount)} ${escapeHtml(o.currency || "")} · ${fmtDate(o.createdAt)}</div>
                </div>
                <span class="badge badge-${escapeHtml(o.status)}">${escapeHtml(o.status)}</span>
            </div>`).join("");
    }

    function updateOrderRow(evt) {
        const row = el.orders.querySelector('[data-order-id="' + evt.orderId + '"]');
        if (!row) { loadOrders(); return; }
        const badge = row.querySelector(".badge");
        badge.className = "badge badge-" + evt.toStatus;
        badge.textContent = evt.toStatus;
        row.classList.add("flash");
        setTimeout(() => row.classList.remove("flash"), 1500);
    }

    function pushFeed(evt) {
        const empty = el.feed.querySelector(".feed-empty");
        if (empty) empty.remove();

        const transition = evt.fromStatus
            ? `<span class="badge badge-${evt.fromStatus}">${evt.fromStatus}</span>
               <span class="arrow">&rarr;</span>
               <span class="badge badge-${evt.toStatus}">${evt.toStatus}</span>`
            : `<span class="badge badge-${evt.toStatus}">${evt.toStatus}</span>`;

        const li = document.createElement("li");
        li.innerHTML = `
            <span class="feed-time">${fmtTime(evt.timestamp)}</span>
            <div class="feed-order">${escapeHtml(evt.orderNumber || "")}</div>
            <div>${transition}</div>
            <div class="muted">${escapeHtml(evt.source || "")}${evt.note ? " · " + escapeHtml(evt.note) : ""}</div>`;
        el.feed.prepend(li);

        while (el.feed.children.length > 50) el.feed.lastElementChild.remove();
    }

    /* ---------------- test order ---------------- */

    el.orderForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        el.orderMsg.className = "hidden";
        try {
            const order = await api("/api/orders", {
                method: "POST",
                body: JSON.stringify({
                    shippingAddress: $("shippingAddress").value,
                    currency: "USD",
                    items: [{
                        productName: $("productName").value,
                        productSku: "SKU-DEMO",
                        quantity: Number($("quantity").value),
                        unitPrice: Number($("unitPrice").value),
                    }],
                }),
            });
            el.orderMsg.className = "success";
            el.orderMsg.textContent = "Created: " + order.orderNumber;
        } catch (err) {
            el.orderMsg.className = "error";
            el.orderMsg.textContent = err.message;
        }
    });

    el.refreshBtn.addEventListener("click", loadOrders);
    el.clearFeedBtn.addEventListener("click", () => {
        el.feed.innerHTML = '<li class="feed-empty muted">No events yet.</li>';
    });

    /* ---------------- helpers ---------------- */

    const escapeHtml = (s) => String(s == null ? "" : s).replace(/[&<>"']/g,
        (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

    const fmtMoney = (v) => (v == null ? "—" : Number(v).toFixed(2));
    const fmtDate = (s) => (s ? String(s).replace("T", " ").slice(0, 16) : "");
    const fmtTime = (s) => (s ? String(s).slice(11, 19) : new Date().toTimeString().slice(0, 8));

    // already signed in? go straight to the dashboard
    if (token()) enterApp();

    window.addEventListener("beforeunload", closeSocket);
})();
