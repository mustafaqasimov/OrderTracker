package com.mustafaqasimov.ordertracker.service;

import com.mustafaqasimov.ordertracker.dto.request.PaymentWebhookRequest;
import com.mustafaqasimov.ordertracker.dto.request.ShipmentWebhookRequest;
import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.entity.WebhookLog;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.WebhookSource;
import com.mustafaqasimov.ordertracker.exception.error.InvalidOperationException;
import com.mustafaqasimov.ordertracker.exception.error.ResourceNotFoundException;
import com.mustafaqasimov.ordertracker.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WebhookService")
class WebhookServiceTest {

    private static final Long LOG_ID = 42L;

    @Mock WebhookLogService webhookLogService;
    @Mock OrderService orderService;
    @Mock ObjectMapper objectMapper;

    @InjectMocks WebhookService webhookService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = TestFixtures.order(1L, TestFixtures.user(7L, "user@test.local"), OrderStatus.PENDING);

        WebhookLog log = WebhookLog.builder().build();
        log.setId(LOG_ID);
        when(webhookLogService.saveIncoming(any(), any(), any(), any())).thenReturn(log);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    private PaymentWebhookRequest payment(String eventType) {
        return new PaymentWebhookRequest(eventType, "ORD-1", "pi_123", null);
    }

    private ShipmentWebhookRequest shipment(String eventType) {
        return new ShipmentWebhookRequest(eventType, "ORD-1", "TRK-9");
    }

    @ParameterizedTest(name = "{0} maps to {1}")
    @CsvSource({
            "payment.succeeded,PAID",
            "payment.failed,PAYMENT_FAILED",
            "payment.refunded,REFUNDED"
    })
    @DisplayName("each payment event maps to the right target status")
    void paymentEventMapping(String eventType, OrderStatus expected) {
        when(orderService.applyPaymentEvent(anyString(), anyString(), any(), anyString()))
                .thenReturn(order);

        webhookService.handlePaymentWebhook(payment(eventType));

        verify(orderService).applyPaymentEvent(eq("ORD-1"), eq("pi_123"), eq(expected), anyString());
    }

    @ParameterizedTest(name = "{0} maps to {1}")
    @CsvSource({
            "shipment.shipped,SHIPPED",
            "shipment.delivered,DELIVERED"
    })
    @DisplayName("each shipment event maps to the right target status")
    void shipmentEventMapping(String eventType, OrderStatus expected) {
        when(orderService.applyShipmentEvent(anyString(), anyString(), any(), anyString()))
                .thenReturn(order);

        webhookService.handleShipmentWebhook(shipment(eventType));

        verify(orderService).applyShipmentEvent(eq("ORD-1"), eq("TRK-9"), eq(expected), anyString());
    }

    @Test
    @DisplayName("the incoming call is logged before it is processed and marked processed after")
    void logsIncomingThenProcessed() {
        when(orderService.applyPaymentEvent(anyString(), anyString(), any(), anyString()))
                .thenReturn(order);

        webhookService.handlePaymentWebhook(payment("payment.succeeded"));

        verify(webhookLogService).saveIncoming(
                eq(WebhookSource.PAYMENT), eq("payment.succeeded"), eq("pi_123"), eq("{}"));
        verify(webhookLogService).markProcessed(LOG_ID, order);
        verify(webhookLogService, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("the failure reason is carried into the note")
    void failureReasonIsIncludedInTheNote() {
        when(orderService.applyPaymentEvent(anyString(), anyString(), any(), anyString()))
                .thenReturn(order);

        webhookService.handlePaymentWebhook(
                new PaymentWebhookRequest("payment.failed", "ORD-1", "pi_123", "Card declined"));

        verify(orderService).applyPaymentEvent(anyString(), anyString(),
                eq(OrderStatus.PAYMENT_FAILED), contains("Card declined"));
    }

    @Test
    @DisplayName("an unknown payment event type is rejected and the log entry marked failed")
    void unknownPaymentEvent() {
        assertThatThrownBy(() -> webhookService.handlePaymentWebhook(payment("payment.exploded")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("payment.exploded");

        verify(webhookLogService).markFailed(eq(LOG_ID), contains("payment.exploded"));
        verify(orderService, never()).applyPaymentEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an unknown shipment event type is rejected and the log entry marked failed")
    void unknownShipmentEvent() {
        assertThatThrownBy(() -> webhookService.handleShipmentWebhook(shipment("shipment.lost")))
                .isInstanceOf(InvalidOperationException.class);

        verify(webhookLogService).markFailed(eq(LOG_ID), contains("shipment.lost"));
        verify(orderService, never()).applyShipmentEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a processing failure is recorded and re-thrown to the caller")
    void processingFailureIsRecordedAndRethrown() {
        when(orderService.applyPaymentEvent(anyString(), anyString(), any(), anyString()))
                .thenThrow(new ResourceNotFoundException("Order not found for orderNumber ORD-1"));

        assertThatThrownBy(() -> webhookService.handlePaymentWebhook(payment("payment.succeeded")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(webhookLogService).markFailed(eq(LOG_ID), contains("ORD-1"));
        verify(webhookLogService, never()).markProcessed(any(), any());
    }

    @Test
    @DisplayName("a payload that cannot be serialised still gets logged, as a plain string")
    void serialisationFailureFallsBackToToString() {
        when(objectMapper.writeValueAsString(any())).thenThrow(new IllegalStateException("no"));
        when(orderService.applyPaymentEvent(anyString(), anyString(), any(), anyString()))
                .thenReturn(order);

        webhookService.handlePaymentWebhook(payment("payment.succeeded"));

        verify(webhookLogService).saveIncoming(eq(WebhookSource.PAYMENT), anyString(), anyString(),
                contains("PaymentWebhookRequest"));
    }

    @Test
    @DisplayName("the async variants delegate to the synchronous handlers")
    void asyncVariantsDelegate() {
        when(orderService.applyPaymentEvent(anyString(), anyString(), any(), anyString()))
                .thenReturn(order);
        when(orderService.applyShipmentEvent(anyString(), anyString(), any(), anyString()))
                .thenReturn(order);

        webhookService.handlePaymentWebhookAsync(payment("payment.succeeded"));
        webhookService.handleShipmentWebhookAsync(shipment("shipment.shipped"));

        verify(orderService).applyPaymentEvent(anyString(), anyString(), eq(OrderStatus.PAID), anyString());
        verify(orderService).applyShipmentEvent(anyString(), anyString(), eq(OrderStatus.SHIPPED), anyString());
        assertThat(order).isNotNull();
    }
}
