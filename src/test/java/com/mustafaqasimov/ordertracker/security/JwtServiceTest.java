package com.mustafaqasimov.ordertracker.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.mustafaqasimov.ordertracker.entity.User;
import com.mustafaqasimov.ordertracker.enums.Role;
import com.mustafaqasimov.ordertracker.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, "ordertracker", 15, 7);
        user = TestFixtures.user(7L, "user@test.local");
    }

    @Test
    @DisplayName("an access token carries the user id, e-mail and role")
    void accessTokenClaims() {
        DecodedJWT decoded = jwtService.verifyToken(jwtService.generateAccessToken(user));

        assertThat(decoded.getSubject()).isEqualTo("7");
        assertThat(decoded.getIssuer()).isEqualTo("ordertracker");
        assertThat(decoded.getClaim("email").asString()).isEqualTo("user@test.local");
        assertThat(decoded.getClaim("role").asString()).isEqualTo(Role.ROLE_USER.name());
    }

    @Test
    @DisplayName("an admin token carries ROLE_ADMIN")
    void adminRoleIsCarried() {
        DecodedJWT decoded = jwtService.verifyToken(
                jwtService.generateAccessToken(TestFixtures.admin(1L)));

        assertThat(decoded.getClaim("role").asString()).isEqualTo(Role.ROLE_ADMIN.name());
    }

    @Test
    @DisplayName("a refresh token carries no role claim, only the subject")
    void refreshTokenIsMinimal() {
        DecodedJWT decoded = jwtService.verifyToken(jwtService.generateRefreshToken(user));

        assertThat(decoded.getSubject()).isEqualTo("7");
        assertThat(decoded.getClaim("role").isMissing()).isTrue();
        assertThat(decoded.getClaim("email").isMissing()).isTrue();
    }

    @Test
    @DisplayName("the refresh token outlives the access token")
    void expiryWindows() {
        Date access = jwtService.verifyToken(jwtService.generateAccessToken(user)).getExpiresAt();
        Date refresh = jwtService.verifyToken(jwtService.generateRefreshToken(user)).getExpiresAt();

        assertThat(access).isAfter(new Date());
        assertThat(refresh).isAfter(access);
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void foreignSignatureIsRejected() {
        String foreign = new JwtService("a-completely-different-secret", "ordertracker", 15, 7)
                .generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.verifyToken(foreign))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    @DisplayName("a token from another issuer is rejected")
    void foreignIssuerIsRejected() {
        String foreign = new JwtService(SECRET, "someone-else", 15, 7).generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.verifyToken(foreign))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    @DisplayName("an already expired token is rejected")
    void expiredTokenIsRejected() {
        String expired = new JwtService(SECRET, "ordertracker", -1, 7).generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.verifyToken(expired))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    @DisplayName("garbage is rejected rather than parsed")
    void garbageIsRejected() {
        assertThatThrownBy(() -> jwtService.verifyToken("not-a-jwt"))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    @DisplayName("two tokens for the same user still verify independently")
    void tokensAreIndependent() {
        String first = jwtService.generateAccessToken(user);
        String second = jwtService.generateAccessToken(user);

        assertThat(jwtService.verifyToken(first).getSubject()).isEqualTo("7");
        assertThat(jwtService.verifyToken(second).getSubject()).isEqualTo("7");
    }
}
