package com.mustafaqasimov.ordertracker.websocket;

import com.mustafaqasimov.ordertracker.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
@RequiredArgsConstructor
public class OrderWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    /** sessionId -> session. Concurrent because sends come from request/async threads. */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket connected: user={} role={} (total={})",
                userIdOf(session), roleOf(session), sessions.size());
        send(session, Map.of(
                "type", "CONNECTED",
                "message", "Subscribed to live order updates"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket disconnected: user={} status={} (total={})",
                userIdOf(session), status.getCode(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // The channel is push-only; a client "ping" just gets a "pong" so it can keep-alive.
        if ("ping".equalsIgnoreCase(message.getPayload().trim())) {
            send(session, Map.of("type", "PONG"));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error for user {}: {}", userIdOf(session), exception.getMessage());
        sessions.remove(session.getId());
    }


    public void broadcast(OrderEvent event, Long ownerId) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("Could not serialise order event {}: {}", event.orderNumber(), e.getMessage());
            return;
        }

        int delivered = 0;
        for (WebSocketSession session : sessions.values()) {
            if (!isRecipient(session, ownerId)) {
                continue;
            }
            if (sendRaw(session, payload)) {
                delivered++;
            }
        }
        log.debug("Order event {} for {} delivered to {} session(s)",
                event.type(), event.orderNumber(), delivered);
    }

    private boolean isRecipient(WebSocketSession session, Long ownerId) {
        if (SecurityUtils.ROLE_ADMIN.equals(roleOf(session))) {
            return true;
        }
        Long userId = userIdOf(session);
        return userId != null && userId.equals(ownerId);
    }

    private void send(WebSocketSession session, Map<String, ?> body) {
        try {
            sendRaw(session, objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            log.debug("Could not serialise control frame: {}", e.getMessage());
        }
    }

    private boolean sendRaw(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            sessions.remove(session.getId());
            return false;
        }
        try {
            // A WebSocketSession is not thread-safe for concurrent sends.
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping session {}: {}", session.getId(), e.getMessage());
            sessions.remove(session.getId());
            return false;
        }
    }

    private Long userIdOf(WebSocketSession session) {
        return (Long) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
    }

    private String roleOf(WebSocketSession session) {
        return (String) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_ROLE);
    }
}
