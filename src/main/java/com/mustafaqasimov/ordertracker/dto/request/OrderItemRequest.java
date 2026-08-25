package com.mustafaqasimov.ordertracker.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Order item request DTO")
public class OrderItemRequest {

    @Schema(example = "Wireless Mouse")
    @NotBlank(message = "Product name is required")
    @Size(max = 200)
    String productName;

    @Schema(example = "SKU-WM-001")
    @Size(max = 80)
    String productSku;

    @Schema(example = "2")
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    Integer quantity;

    @Schema(example = "29.99")
    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Unit price must be positive")
    BigDecimal unitPrice;
}
