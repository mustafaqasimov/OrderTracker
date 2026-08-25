package com.mustafaqasimov.ordertracker.service;

import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@ordertracker.local}")
    private String fromAddress;


    @Async("emailExecutor")
    @Retryable(
            retryFor = {org.springframework.mail.MailException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendOrderStatusChangedEmail(Order order, OrderStatus previous, OrderStatus current) {
        String to = order.getCustomerEmail();
        String subject = "Order " + order.getOrderNumber() + " — status: " + current;
        String body = buildBody(order, previous, current);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        log.info("Sending status-change email to {} for order {} ({} -> {})",
                to, order.getOrderNumber(), previous, current);
        mailSender.send(message);
        log.info("Email sent to {} for order {}", to, order.getOrderNumber());
    }

    @Recover
    public void recoverEmail(org.springframework.mail.MailException ex,
                             Order order, OrderStatus previous, OrderStatus current) {
        log.error("Failed to send status-change email to {} for order {} after retries ({} -> {}): {}",
                order.getCustomerEmail(), order.getOrderNumber(), previous, current, ex.getMessage());
    }

    private String buildBody(Order order, OrderStatus previous, OrderStatus current) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello,\n\n");
        sb.append("Your order ").append(order.getOrderNumber())
                .append(" has been updated.\n\n");
        if (previous != null) {
            sb.append("Previous status: ").append(previous).append("\n");
        }
        sb.append("Current status:  ").append(current).append("\n");
        sb.append("Total: ").append(order.getTotalAmount())
                .append(" ").append(order.getCurrency()).append("\n\n");

        switch (current) {
            case PAID -> sb.append("We have received your payment. Thank you!\n");
            case PAYMENT_FAILED -> sb.append("Unfortunately your payment did not go through. ")
                    .append("Please try again or contact support.\n");
            case SHIPPED -> {
                sb.append("Your order has been shipped.\n");
                if (order.getShipmentTrackingNumber() != null) {
                    sb.append("Tracking number: ").append(order.getShipmentTrackingNumber()).append("\n");
                }
            }
            case DELIVERED -> sb.append("Your order has been delivered. Enjoy!\n");
            case CANCELLED -> sb.append("Your order has been cancelled.\n");
            case REFUNDED -> sb.append("Your order has been refunded.\n");
            default -> { /* nothing extra */ }
        }

        sb.append("\n— OrderTracker");
        return sb.toString();
    }
}
