package com.mustafaqasimov.ordertracker.websocket;

import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.entity.User;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.StatusChangeSource;
import com.mustafaqasimov.ordertracker.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventPublisher")
class OrderEventPublisherTest {

    @Mock OrderWebSocketHandler handler;
    @InjectMocks OrderEventPublisher publisher;

    private User owner;
    private Order order;

    @BeforeEach
    void setUp() {
        owner = TestFixtures.user(7L, "user@test.local");
        order = TestFixtures.order(1L, owner, OrderStatus.PENDING);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("outside a transaction the event goes out immediately")
    void broadcastsImmediatelyWithoutTransaction() {
        publisher.orderCreated(order);

        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);
        verify(handler).broadcast(event.capture(), eq(7L));

        OrderEvent value = event.getValue();
        assertThat(value.type()).isEqualTo(OrderEvent.TYPE_CREATED);
        assertThat(value.orderId()).isEqualTo(1L);
        assertThat(value.orderNumber()).isEqualTo("ORD-20260101-ABCD1234");
        assertThat(value.customerEmail()).isEqualTo("user@test.local");
        assertThat(value.fromStatus()).isNull();
        assertThat(value.toStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(value.totalAmount()).isEqualByComparingTo("59.98");
        assertThat(value.currency()).isEqualTo("USD");
        assertThat(value.source()).isEqualTo(StatusChangeSource.USER);
        assertThat(value.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("statusChanged carries both the old and the new status")
    void statusChangedCarriesTransition() {
        publisher.statusChanged(order, OrderStatus.PENDING, OrderStatus.PAID,
                StatusChangeSource.WEBHOOK_PAYMENT, "Payment webhook");

        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);
        verify(handler).broadcast(event.capture(), eq(7L));

        assertThat(event.getValue().type()).isEqualTo(OrderEvent.TYPE_STATUS_CHANGED);
        assertThat(event.getValue().fromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(event.getValue().toStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(event.getValue().source()).isEqualTo(StatusChangeSource.WEBHOOK_PAYMENT);
        assertThat(event.getValue().note()).isEqualTo("Payment webhook");
    }

    @Test
    @DisplayName("inside a transaction nothing is sent before the commit")
    void waitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.statusChanged(order, OrderStatus.PENDING, OrderStatus.PAID,
                StatusChangeSource.ADMIN, "note");

        verifyNoInteractions(handler);

        List<TransactionSynchronization> registered =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(registered).hasSize(1);

        registered.getFirst().afterCommit();

        verify(handler).broadcast(any(OrderEvent.class), eq(7L));
    }

    @Test
    @DisplayName("a rolled-back transaction never pushes the event")
    void rollbackPushesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.orderCreated(order);
        // no afterCommit() call — this models a rollback
        TransactionSynchronizationManager.clearSynchronization();

        verifyNoInteractions(handler);
    }

    @Test
    @DisplayName("a broken socket never breaks the caller")
    void broadcastFailureIsSwallowed() {
        doThrow(new IllegalStateException("socket gone"))
                .when(handler).broadcast(any(OrderEvent.class), any());

        assertThatCode(() -> publisher.orderCreated(order)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an order with no user resolves to a null owner instead of blowing up")
    void nullOwnerIsTolerated() {
        order.setUser(null);

        publisher.orderCreated(order);

        verify(handler).broadcast(any(OrderEvent.class), eq((Long) null));
    }
}
