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
    Long id;
    OrderStatus fromStatus;
    OrderStatus toStatus;
    StatusChangeSource source;
    String note;
    LocalDateTime changedAt;
}
