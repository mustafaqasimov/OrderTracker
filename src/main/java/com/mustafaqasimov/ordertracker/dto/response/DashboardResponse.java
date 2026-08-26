package com.mustafaqasimov.ordertracker.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@Schema(description = "Dashboard response DTO")
public class DashboardResponse {
    @Schema(description = "Total number of orders", example = "100")
    long totalOrders;
    @Schema(description = "Number of pending orders", example = "20")
    long pendingOrders;
    @Schema(description = "Number of paid orders", example = "50")
    long paidOrders;
    @Schema(description = "Number of delivered orders", example = "25")
    long deliveredOrders;
    @Schema(description = "Number of cancelled orders", example = "5")
    long cancelledOrders;
}
