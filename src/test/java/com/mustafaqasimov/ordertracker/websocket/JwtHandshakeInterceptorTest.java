package com.mustafaqasimov.ordertracker.websocket;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.mustafaqasimov.ordertracker.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtHandshakeInterceptor")
class JwtHandshakeInterceptorTest {

    @Mock JwtService jwtService;
    @InjectMocks JwtHandshakeInterceptor interceptor;

    private final ServerHttpResponse response = mock(ServerHttpResponse.class);

    private ServerHttpRequest requestTo(String uri) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }

    private DecodedJWT decoded(String subject, String role) {
        DecodedJWT jwt = mock(DecodedJWT.class);
        Claim claim = mock(Claim.class);
        doReturn(subject).when(jwt).getSubject();
        doReturn(role).when(claim).asString();
        doReturn(claim).when(jwt).getClaim("role");
        return jwt;
    }

    @Test
    @DisplayName("a valid token opens the socket and stores the identity")
    void validToken() {
        DecodedJWT jwt = decoded("7", "ROLE_ADMIN");
        when(jwtService.verifyToken("good-token")).thenReturn(jwt);
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(
                requestTo("http://localhost:8080/ws/orders?token=good-token"),
                response, null, attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes)
                .containsEntry(JwtHandshakeInterceptor.ATTR_USER_ID, 7L)
                .containsEntry(JwtHandshakeInterceptor.ATTR_ROLE, "ROLE_ADMIN");
    }

    @Test
    @DisplayName("no token at all is rejected")
    void missingToken() {
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(
                requestTo("http://localhost:8080/ws/orders"), response, null, attributes);

        assertThat(allowed).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("an empty token is rejected")
    void blankToken() {
        boolean allowed = interceptor.beforeHandshake(
                requestTo("http://localhost:8080/ws/orders?token="), response, null, new HashMap<>());

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("a token that fails verification is rejected")
    void invalidToken() {
        when(jwtService.verifyToken(anyString()))
                .thenThrow(new JWTVerificationException("bad signature"));
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(
                requestTo("http://localhost:8080/ws/orders?token=forged"), response, null, attributes);

        assertThat(allowed).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("a token whose subject is not a user id is rejected")
    void nonNumericSubject() {
        DecodedJWT jwt = decoded("not-a-number", "ROLE_USER");
        when(jwtService.verifyToken("weird")).thenReturn(jwt);

        boolean allowed = interceptor.beforeHandshake(
                requestTo("http://localhost:8080/ws/orders?token=weird"), response, null, new HashMap<>());

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("afterHandshake does nothing and never throws")
    void afterHandshakeIsInert() {
        interceptor.afterHandshake(requestTo("http://localhost:8080/ws/orders"), response, null, null);
    }
}
