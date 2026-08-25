package com.mustafaqasimov.ordertracker.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Payload sent by the payment gateway")
public class PaymentWebhookRequest {

    @Schema(example = "payment.succeeded",
            description = "One of: payment.succeeded, payment.failed, payment.refunded")
    @NotBlank
    String eventType;

    @Schema(example = "ORD-20260101-0001",
            description = "Our internal order number that was sent to the gateway")
    @NotBlank
    String orderNumber;

    @Schema(example = "pi_3Nabc123DEF",
            description = "External gateway reference (payment intent id)")
    @NotBlank
    String paymentReference;

    @Schema(example = "Card declined")
    String failureReason;
}
