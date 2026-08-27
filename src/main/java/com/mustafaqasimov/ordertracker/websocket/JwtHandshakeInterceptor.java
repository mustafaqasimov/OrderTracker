package com.mustafaqasimov.ordertracker.websocket;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.mustafaqasimov.ordertracker.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_ROLE = "role";

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        List<String> tokens = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().get("token");

        if (tokens == null || tokens.isEmpty() || tokens.getFirst().isBlank()) {
            log.debug("WebSocket handshake rejected: no token");
            return false;
        }

        try {
            DecodedJWT decoded = jwtService.verifyToken(tokens.getFirst());
            attributes.put(ATTR_USER_ID, Long.parseLong(decoded.getSubject()));
            attributes.put(ATTR_ROLE, decoded.getClaim("role").asString());
            return true;
        } catch (JWTVerificationException | NumberFormatException e) {
            log.debug("WebSocket handshake rejected: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }
}
