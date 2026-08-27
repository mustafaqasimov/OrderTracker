package com.mustafaqasimov.ordertracker.websocket;

import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.StatusChangeSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;


public record OrderEvent(
        String type,
        Long orderId,
        String orderNumber,
        String customerEmail,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        BigDecimal totalAmount,
        String currency,
        StatusChangeSource source,
        String note,
        LocalDateTime timestamp
) {

    public static final String TYPE_CREATED = "ORDER_CREATED";
    public static final String TYPE_STATUS_CHANGED = "ORDER_STATUS_CHANGED";
}
