package com.mustafaqasimov.ordertracker.controller;

import com.mustafaqasimov.ordertracker.dto.request.CreateOrderRequest;
import com.mustafaqasimov.ordertracker.dto.request.UpdateOrderRequest;
import com.mustafaqasimov.ordertracker.dto.response.OrderResponse;
import com.mustafaqasimov.ordertracker.dto.response.OrderStatusHistoryResponse;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management endpoints for authenticated users")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Create a new order")
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List my orders (optionally filter by status)")
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> myOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(orderService.listMyOrders(status, pageable));
    }

    @Operation(summary = "Get one of my orders by id")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getMyOrder(id));
    }

    @Operation(summary = "Update a PENDING order (address and/or items)")
    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateOrderRequest request) {
        return ResponseEntity.ok(orderService.updateOrder(id, request));
    }

    @Operation(summary = "Cancel an order (allowed while PENDING or PAYMENT_FAILED)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @Operation(summary = "Delete an order (only PENDING or CANCELLED)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Full status history of an order")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderStatusHistoryResponse>> history(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getStatusHistory(id));
    }
}
