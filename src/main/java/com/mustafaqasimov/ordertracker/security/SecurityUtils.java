package com.mustafaqasimov.ordertracker.security;

import com.mustafaqasimov.ordertracker.exception.error.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private SecurityUtils() {
    }

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        if (principal instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new UnauthorizedException("Not authenticated");
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (ROLE_ADMIN.equals(a.getAuthority())) return true;
        }
        return false;
    }
}
