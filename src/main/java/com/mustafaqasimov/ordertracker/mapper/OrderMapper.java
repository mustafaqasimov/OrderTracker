package com.mustafaqasimov.ordertracker.mapper;

import com.mustafaqasimov.ordertracker.dto.request.OrderItemRequest;
import com.mustafaqasimov.ordertracker.dto.response.OrderItemResponse;
import com.mustafaqasimov.ordertracker.dto.response.OrderResponse;
import com.mustafaqasimov.ordertracker.dto.response.OrderStatusHistoryResponse;
import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.entity.OrderItem;
import com.mustafaqasimov.ordertracker.entity.OrderStatusHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    OrderItem toEntity(OrderItemRequest request);

    @Mapping(target = "userId", source = "user.id")
    OrderResponse toResponse(Order order);

    OrderItemResponse toItemResponse(OrderItem item);

    List<OrderItemResponse> toItemResponses(List<OrderItem> items);

    @Mapping(target = "changedAt", source = "createdAt")
    OrderStatusHistoryResponse toHistoryResponse(OrderStatusHistory history);

    List<OrderStatusHistoryResponse> toHistoryResponses(List<OrderStatusHistory> list);
}
