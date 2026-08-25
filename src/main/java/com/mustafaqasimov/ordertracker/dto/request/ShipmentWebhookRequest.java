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
@Schema(description = "Payload sent by the shipping carrier")
public class ShipmentWebhookRequest {

    @Schema(example = "shipment.shipped",
            description = "One of: shipment.shipped, shipment.delivered")
    @NotBlank
    String eventType;

    @Schema(example = "ORD-20260101-0001")
    @NotBlank
    String orderNumber;

    @Schema(example = "TRK-99887766")
    @NotBlank
    String trackingNumber;
}
