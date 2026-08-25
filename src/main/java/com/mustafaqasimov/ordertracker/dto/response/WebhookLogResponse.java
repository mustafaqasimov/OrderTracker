package com.mustafaqasimov.ordertracker.dto.response;

import com.mustafaqasimov.ordertracker.enums.WebhookLogStatus;
import com.mustafaqasimov.ordertracker.enums.WebhookSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Webhook log entry")
public class WebhookLogResponse {
    Long id;
    WebhookSource source;
    String eventType;
    String externalReference;
    String payload;
    WebhookLogStatus status;
    String errorMessage;
    LocalDateTime receivedAt;
    LocalDateTime processedAt;
    Long orderId;
}
