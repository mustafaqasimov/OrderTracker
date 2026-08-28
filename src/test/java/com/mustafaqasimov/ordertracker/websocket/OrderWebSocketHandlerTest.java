package com.mustafaqasimov.ordertracker.websocket;

import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.Role;
import com.mustafaqasimov.ordertracker.enums.StatusChangeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderWebSocketHandler")
class OrderWebSocketHandlerTest {

    private static final String PAYLOAD = "{\"type\":\"ORDER_STATUS_CHANGED\"}";

    @Mock ObjectMapper objectMapper;

    OrderWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderWebSocketHandler(objectMapper);
        when(objectMapper.writeValueAsString(any())).thenReturn(PAYLOAD);
    }

    private WebSocketSession session(String id, Long userId, Role role) {
        WebSocketSession session = org.mockito.Mockito.mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.ATTR_USER_ID, userId);
        attributes.put(JwtHandshakeInterceptor.ATTR_ROLE, role.name());
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private OrderEvent event() {
        return new OrderEvent(OrderEvent.TYPE_STATUS_CHANGED, 1L, "ORD-1", "user@test.local",
                OrderStatus.PENDING, OrderStatus.PAID, new BigDecimal("10.00"), "USD",
                StatusChangeSource.WEBHOOK_PAYMENT, "note", LocalDateTime.now());
    }

    @Test
    @DisplayName("a new connection is greeted with a CONNECTED frame")
    void greetsOnConnect() throws IOException {
        WebSocketSession session = session("s1", 7L, Role.ROLE_USER);

        handler.afterConnectionEstablished(session);

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("the owner of the order receives the event")
    void ownerReceives() throws IOException {
        WebSocketSession owner = session("s1", 7L, Role.ROLE_USER);
        handler.afterConnectionEstablished(owner);
        org.mockito.Mockito.clearInvocations(owner);

        handler.broadcast(event(), 7L);

        verify(owner).sendMessage(new TextMessage(PAYLOAD));
    }

    @Test
    @DisplayName("another user never sees somebody else's order")
    void strangerDoesNotReceive() throws IOException {
        WebSocketSession stranger = session("s2", 99L, Role.ROLE_USER);
        handler.afterConnectionEstablished(stranger);
        org.mockito.Mockito.clearInvocations(stranger);

        handler.broadcast(event(), 7L);

        verify(stranger, never()).sendMessage(any());
    }

    @Test
    @DisplayName("an admin receives every order's events")
    void adminReceivesEverything() throws IOException {
        WebSocketSession admin = session("s3", 1L, Role.ROLE_ADMIN);
        handler.afterConnectionEstablished(admin);
        org.mockito.Mockito.clearInvocations(admin);

        handler.broadcast(event(), 7L);

        verify(admin).sendMessage(new TextMessage(PAYLOAD));
    }

    @Test
    @DisplayName("a closed connection stops receiving")
    void closedSessionIsDropped() throws IOException {
        WebSocketSession session = session("s1", 7L, Role.ROLE_USER);
        handler.afterConnectionEstablished(session);
        org.mockito.Mockito.clearInvocations(session);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        handler.broadcast(event(), 7L);

        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("a session that reports itself closed is skipped and forgotten")
    void notOpenSessionIsSkipped() throws IOException {
        WebSocketSession session = session("s1", 7L, Role.ROLE_USER);
        handler.afterConnectionEstablished(session);
        org.mockito.Mockito.clearInvocations(session);
        when(session.isOpen()).thenReturn(false);

        handler.broadcast(event(), 7L);
        handler.broadcast(event(), 7L);

        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("an IOException on send drops that session instead of failing the broadcast")
    void sendFailureDropsSession() throws IOException {
        WebSocketSession broken = session("s1", 7L, Role.ROLE_USER);
        WebSocketSession healthy = session("s2", 7L, Role.ROLE_USER);
        handler.afterConnectionEstablished(broken);
        handler.afterConnectionEstablished(healthy);
        org.mockito.Mockito.clearInvocations(broken, healthy);
        doThrow(new IOException("pipe closed")).when(broken).sendMessage(any());

        assertThatCode(() -> handler.broadcast(event(), 7L)).doesNotThrowAnyException();

        verify(healthy).sendMessage(new TextMessage(PAYLOAD));

        // the broken one was evicted, so a second broadcast does not touch it again
        org.mockito.Mockito.clearInvocations(broken);
        handler.broadcast(event(), 7L);
        verify(broken, never()).sendMessage(any());
    }

    @Test
    @DisplayName("a serialisation failure is contained, no session is written to")
    void serialisationFailureIsContained() throws IOException {
        WebSocketSession session = session("s1", 7L, Role.ROLE_USER);
        handler.afterConnectionEstablished(session);
        org.mockito.Mockito.clearInvocations(session);
        when(objectMapper.writeValueAsString(any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> handler.broadcast(event(), 7L)).doesNotThrowAnyException();

        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("a client ping is answered")
    void pingIsAnswered() throws Exception {
        WebSocketSession session = session("s1", 7L, Role.ROLE_USER);
        handler.afterConnectionEstablished(session);
        org.mockito.Mockito.clearInvocations(session);

        handler.handleTextMessage(session, new TextMessage(" PING "));

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("any other client message is ignored")
    void otherMessagesAreIgnored() throws Exception {
        WebSocketSession session = session("s1", 7L, Role.ROLE_USER);
        handler.afterConnectionEstablished(session);
        org.mockito.Mockito.clearInvocations(session);

        handler.handleTextMessage(session, new TextMessage("{\"hack\":true}"));

        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("a transport error drops the session")
    void transportErrorDropsSession() throws IOException {
        WebSocketSession session = session("s1", 7L, Role.ROLE_USER);
        handler.afterConnectionEstablished(session);
        org.mockito.Mockito.clearInvocations(session);

        handler.handleTransportError(session, new IOException("reset"));
        handler.broadcast(event(), 7L);

        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("broadcasting with nobody connected is harmless")
    void noSessions() {
        assertThat(handler).isNotNull();
        assertThatCode(() -> handler.broadcast(event(), 7L)).doesNotThrowAnyException();
    }
}
