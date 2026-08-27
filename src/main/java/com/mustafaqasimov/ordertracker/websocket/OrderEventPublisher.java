package com.mustafaqasimov.ordertracker.websocket;

import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.StatusChangeSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * Builds order events and hands them to the socket handler <b>after the transaction commits</b>,
 * so a client that reacts by re-fetching the order never reads pre-commit state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final OrderWebSocketHandler handler;

    public void orderCreated(Order order) {
        publish(new OrderEvent(
                OrderEvent.TYPE_CREATED,
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerEmail(),
                null,
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                StatusChangeSource.USER,
                "Order created",
                LocalDateTime.now()
        ), ownerIdOf(order));
    }

    public void statusChanged(Order order, OrderStatus from, OrderStatus to,
                              StatusChangeSource source, String note) {
        publish(new OrderEvent(
                OrderEvent.TYPE_STATUS_CHANGED,
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerEmail(),
                from,
                to,
                order.getTotalAmount(),
                order.getCurrency(),
                source,
                note,
                LocalDateTime.now()
        ), ownerIdOf(order));
    }

    private void publish(OrderEvent event, Long ownerId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeBroadcast(event, ownerId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeBroadcast(event, ownerId);
            }
        });
    }

    private void safeBroadcast(OrderEvent event, Long ownerId) {
        try {
            handler.broadcast(event, ownerId);
        } catch (Exception e) {
            // A broken socket must never fail the business transaction.
            log.warn("WebSocket broadcast failed for order {}: {}", event.orderNumber(), e.getMessage());
        }
    }

    private Long ownerIdOf(Order order) {
        return order.getUser() != null ? order.getUser().getId() : null;
    }
}
