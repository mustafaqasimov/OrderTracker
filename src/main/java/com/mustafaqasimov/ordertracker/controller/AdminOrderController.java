package com.mustafaqasimov.ordertracker.controller;

import com.mustafaqasimov.ordertracker.dto.response.DashboardResponse;
import com.mustafaqasimov.ordertracker.dto.response.OrderResponse;
import com.mustafaqasimov.ordertracker.dto.response.OrderStatusHistoryResponse;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin — Orders", description = "Administrative order endpoints")
public class AdminOrderController {

    private final OrderService orderService;

    @Operation(summary = "List all orders (optionally filter by status)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> listAll(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(orderService.listAllOrders(status, pageable));
    }

    @Operation(summary = "Get any order by id")
    @GetMapping("/{id}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order retrieved successfully")
    })
    public ResponseEntity<OrderResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getMyOrder(id)); // admin also passes the ownership check
    }

    @Operation(summary = "Force a status change on an order")
    @PatchMapping("/{id}/status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order status updated successfully")
    })
    public ResponseEntity<OrderResponse> changeStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(orderService.adminChangeStatus(id, status, note));
    }

    @Operation(summary = "Get status history for an order", description = "Retrieve the status history for a specific order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status history retrieved successfully")
    })
    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderStatusHistoryResponse>> history(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderHistoryForAdmin(id));
    }

    @Operation(summary = "Get dashboard statistics", description = "Retrieve dashboard statistics")
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(orderService.getDashboardStatistics());
    }
}
