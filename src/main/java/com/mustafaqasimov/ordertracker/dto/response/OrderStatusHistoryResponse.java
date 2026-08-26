package com.mustafaqasimov.ordertracker.dto.response;

import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.StatusChangeSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Single status transition record")
public class OrderStatusHistoryResponse {
    @Schema(description = "Status history ID", example = "1")
    Long id;
    @Schema(description = "Previous status", example = "PENDING")
    OrderStatus fromStatus;
    @Schema(description = "New status", example = "CONFIRMED")
    OrderStatus toStatus;
    @Schema(description = "Source of the status change", example = "USER")
    StatusChangeSource source;
    @Schema(description = "Note about the status change", example = "Order confirmed by user")
    String note;
    @Schema(description = "Timestamp of the status change", example = "2023-01-01T12:00:00Z")
    LocalDateTime changedAt;
}
