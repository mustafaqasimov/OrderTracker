package com.mustafaqasimov.ordertracker.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Create order request DTO")
public class CreateOrderRequest {

    @Schema(example = "123 Main Street, Baku, Azerbaijan")
    @NotBlank(message = "Shipping address is required")
    @Size(max = 500)
    String shippingAddress;

    @Schema(example = "USD")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
    String currency;

    @Valid
    @NotEmpty(message = "At least one item is required")
    List<OrderItemRequest> items;
}
