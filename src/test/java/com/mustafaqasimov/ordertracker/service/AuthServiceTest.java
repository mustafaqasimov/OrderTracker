package com.mustafaqasimov.ordertracker.service;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.mustafaqasimov.ordertracker.dto.request.LoginRequest;
import com.mustafaqasimov.ordertracker.dto.request.RegisterRequest;
import com.mustafaqasimov.ordertracker.dto.response.AuthResponse;
import com.mustafaqasimov.ordertracker.entity.User;
import com.mustafaqasimov.ordertracker.exception.error.InvalidCredentialsException;
import com.mustafaqasimov.ordertracker.exception.error.ResourceAlreadyExistsException;
import com.mustafaqasimov.ordertracker.mapper.UserMapper;
import com.mustafaqasimov.ordertracker.repository.UserRepository;
import com.mustafaqasimov.ordertracker.security.JwtService;
import com.mustafaqasimov.ordertracker.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserMapper userMapper;

    @InjectMocks AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = TestFixtures.user(7L, "user@test.local");
    }

    private RegisterRequest registerRequest() {
        return new RegisterRequest("Test User", "user@test.local", "Passw0rd123");
    }

    @Test
    @DisplayName("register hashes the password before it reaches the repository")
    void registerHashesPassword() {
        when(userRepository.existsByEmail("user@test.local")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd123")).thenReturn("hashed");
        when(userMapper.toEntity(any(), anyString())).thenReturn(user);

        authService.register(registerRequest());

        verify(passwordEncoder).encode("Passw0rd123");
        verify(userMapper).toEntity(any(RegisterRequest.class), org.mockito.Mockito.eq("hashed"));
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("register refuses an e-mail that is already taken")
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("user@test.local")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("login issues both tokens for correct credentials")
    void loginIssuesTokens() {
        AuthResponse expected = AuthResponse.builder().accessToken("a").refreshToken("r").build();
        when(userRepository.findByEmail("user@test.local")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("Passw0rd123", user.getPassword())).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("a");
        when(jwtService.generateRefreshToken(user)).thenReturn("r");
        when(userMapper.toAuthResponse(user, "a", "r")).thenReturn(expected);

        AuthResponse response = authService.login(new LoginRequest("user@test.local", "Passw0rd123"));

        assertThat(response).isSameAs(expected);
    }

    @Test
    @DisplayName("an unknown e-mail and a wrong password fail the same way")
    void loginFailuresAreIndistinguishable() {
        when(userRepository.findByEmail("nobody@test.local")).thenReturn(java.util.Optional.empty());
        when(userRepository.findByEmail("user@test.local")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@test.local", "x")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.local", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("refresh mints a brand new pair of tokens")
    void refreshRotatesTokens() {
        DecodedJWT decoded = mock(DecodedJWT.class);
        AuthResponse expected = AuthResponse.builder().accessToken("a2").refreshToken("r2").build();
        when(jwtService.verifyToken("old-refresh")).thenReturn(decoded);
        when(decoded.getSubject()).thenReturn("7");
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("a2");
        when(jwtService.generateRefreshToken(user)).thenReturn("r2");
        when(userMapper.toAuthResponse(user, "a2", "r2")).thenReturn(expected);

        assertThat(authService.refreshToken("old-refresh")).isSameAs(expected);
    }

    @Test
    @DisplayName("an invalid refresh token is reported as invalid credentials")
    void refreshRejectsBadToken() {
        when(jwtService.verifyToken("forged")).thenThrow(new JWTVerificationException("bad"));

        assertThatThrownBy(() -> authService.refreshToken("forged"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Please login again");
    }

    @Test
    @DisplayName("a refresh token for a deleted user is rejected too")
    void refreshRejectsDeletedUser() {
        DecodedJWT decoded = mock(DecodedJWT.class);
        when(jwtService.verifyToken("orphan")).thenReturn(decoded);
        when(decoded.getSubject()).thenReturn("7");
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken("orphan"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
