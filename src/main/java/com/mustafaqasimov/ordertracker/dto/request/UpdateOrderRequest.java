package com.mustafaqasimov.ordertracker.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Update order request DTO — allowed only while order is PENDING")
public class UpdateOrderRequest {

    @Schema(example = "New Address, Baku")
    @Size(max = 500)
    String shippingAddress;

    @Valid
    List<OrderItemRequest> items;
}
