package com.mustafaqasimov.ordertracker.service;

import tools.jackson.databind.ObjectMapper;
import com.mustafaqasimov.ordertracker.dto.request.PaymentWebhookRequest;
import com.mustafaqasimov.ordertracker.dto.request.ShipmentWebhookRequest;
import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.entity.WebhookLog;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.WebhookSource;
import com.mustafaqasimov.ordertracker.exception.error.InvalidOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookLogService webhookLogService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    // ------- PAYMENT -------

    public void handlePaymentWebhook(PaymentWebhookRequest request) {
        WebhookLog entry = webhookLogService.saveIncoming(
                WebhookSource.PAYMENT,
                request.getEventType(),
                request.getPaymentReference(),
                toJson(request));

        try {
            OrderStatus target = mapPaymentEventToStatus(request.getEventType());
            String note = "Payment webhook: " + request.getEventType()
                    + (request.getFailureReason() != null ? " — " + request.getFailureReason() : "");

            Order order = orderService.applyPaymentEvent(
                    request.getOrderNumber(), request.getPaymentReference(), target, note);

            webhookLogService.markProcessed(entry.getId(), order);
            log.info("Payment webhook processed (log id={}, order={})",
                    entry.getId(), request.getOrderNumber());
        } catch (Exception ex) {
            webhookLogService.markFailed(entry.getId(), ex.getMessage());
            throw ex;
        }
    }

    @Async("emailExecutor")
    public void handlePaymentWebhookAsync(PaymentWebhookRequest request) {
        handlePaymentWebhook(request);
    }

    // ------- SHIPMENT -------

    public void handleShipmentWebhook(ShipmentWebhookRequest request) {
        WebhookLog entry = webhookLogService.saveIncoming(
                WebhookSource.SHIPMENT,
                request.getEventType(),
                request.getTrackingNumber(),
                toJson(request));

        try {
            OrderStatus target = mapShipmentEventToStatus(request.getEventType());
            String note = "Shipment webhook: " + request.getEventType();

            Order order = orderService.applyShipmentEvent(
                    request.getOrderNumber(), request.getTrackingNumber(), target, note);

            webhookLogService.markProcessed(entry.getId(), order);
            log.info("Shipment webhook processed (log id={}, order={})",
                    entry.getId(), request.getOrderNumber());
        } catch (Exception ex) {
            webhookLogService.markFailed(entry.getId(), ex.getMessage());
            throw ex;
        }
    }

    @Async("emailExecutor")
    public void handleShipmentWebhookAsync(ShipmentWebhookRequest request) {
        handleShipmentWebhook(request);
    }

    // ------- helpers -------

    private OrderStatus mapPaymentEventToStatus(String eventType) {
        return switch (eventType) {
            case "payment.succeeded" -> OrderStatus.PAID;
            case "payment.failed" -> OrderStatus.PAYMENT_FAILED;
            case "payment.refunded" -> OrderStatus.REFUNDED;
            default -> throw new InvalidOperationException("Unknown payment event type: " + eventType);
        };
    }

    private OrderStatus mapShipmentEventToStatus(String eventType) {
        return switch (eventType) {
            case "shipment.shipped" -> OrderStatus.SHIPPED;
            case "shipment.delivered" -> OrderStatus.DELIVERED;
            default -> throw new InvalidOperationException("Unknown shipment event type: " + eventType);
        };
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
