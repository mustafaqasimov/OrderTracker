package com.mustafaqasimov.ordertracker.security;

import com.mustafaqasimov.ordertracker.enums.Role;
import com.mustafaqasimov.ordertracker.exception.error.UnauthorizedException;
import com.mustafaqasimov.ordertracker.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecurityUtils")
class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a Long principal is returned as the current user id")
    void longPrincipal() {
        TestFixtures.authenticate(7L, Role.ROLE_USER);

        assertThat(SecurityUtils.currentUserId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("a numeric String principal is parsed into the user id")
    void stringPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThat(SecurityUtils.currentUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("no authentication at all is unauthorized")
    void noAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(SecurityUtils::currentUserId)
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("a principal that is not a user id is unauthorized")
    void unusablePrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(SecurityUtils::currentUserId)
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("isAdmin is true only for ROLE_ADMIN")
    void isAdmin() {
        TestFixtures.authenticate(1L, Role.ROLE_ADMIN);
        assertThat(SecurityUtils.isAdmin()).isTrue();

        TestFixtures.authenticate(7L, Role.ROLE_USER);
        assertThat(SecurityUtils.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("isAdmin is false rather than throwing when nobody is authenticated")
    void isAdminWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        assertThat(SecurityUtils.isAdmin()).isFalse();
    }
}
