package com.mustafaqasimov.ordertracker.support;

import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.entity.OrderItem;
import com.mustafaqasimov.ordertracker.entity.User;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.Role;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static User user(Long id, String email) {
        User user = User.builder()
                .fullName("Test User")
                .email(email)
                .password("$2a$10$encoded")
                .role(Role.ROLE_USER)
                .build();
        user.setId(id);
        return user;
    }

    public static User admin(Long id) {
        User user = user(id, "admin@ordertracker.local");
        user.setRole(Role.ROLE_ADMIN);
        return user;
    }

    public static Order order(Long id, User owner, OrderStatus status) {
        Order order = Order.builder()
                .orderNumber("ORD-20260101-ABCD1234")
                .user(owner)
                .customerEmail(owner.getEmail())
                .status(status)
                .currency("USD")
                .shippingAddress("123 Main Street, Baku")
                .totalAmount(new BigDecimal("59.98"))
                .items(new ArrayList<>())
                .build();
        order.setId(id);
        order.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return order;
    }

    public static OrderItem item(String name, int qty, String unitPrice) {
        BigDecimal price = new BigDecimal(unitPrice);
        return OrderItem.builder()
                .productName(name)
                .productSku("SKU-" + name.toUpperCase())
                .quantity(qty)
                .unitPrice(price)
                .subtotal(price.multiply(BigDecimal.valueOf(qty)))
                .build();
    }

    public static void authenticate(Long userId, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority(role.name()))));
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
