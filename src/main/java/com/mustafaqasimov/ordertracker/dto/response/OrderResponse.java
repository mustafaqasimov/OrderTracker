package com.mustafaqasimov.ordertracker.dto.response;

import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Order response DTO")
public class OrderResponse {
    Long id;
    String orderNumber;
    Long userId;
    String customerEmail;
    OrderStatus status;
    BigDecimal totalAmount;
    String currency;
    String shippingAddress;
    String paymentReference;
    String shipmentTrackingNumber;
    List<OrderItemResponse> items;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
