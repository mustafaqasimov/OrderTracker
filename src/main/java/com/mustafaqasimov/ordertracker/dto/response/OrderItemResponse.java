package com.mustafaqasimov.ordertracker.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Order item response DTO")
public class OrderItemResponse {
    Long id;
    String productName;
    String productSku;
    Integer quantity;
    BigDecimal unitPrice;
    BigDecimal subtotal;
}
