package com.mustafaqasimov.ordertracker.mapper;

import com.mustafaqasimov.ordertracker.dto.response.WebhookLogResponse;
import com.mustafaqasimov.ordertracker.entity.WebhookLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WebhookLogMapper {

    @Mapping(target = "receivedAt", source = "createdAt")
    @Mapping(target = "orderId", source = "order.id")
    WebhookLogResponse toResponse(WebhookLog log);
}

