package com.mustafaqasimov.ordertracker.service;

import com.mustafaqasimov.ordertracker.dto.response.WebhookLogResponse;
import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.entity.WebhookLog;
import com.mustafaqasimov.ordertracker.enums.WebhookLogStatus;
import com.mustafaqasimov.ordertracker.enums.WebhookSource;
import com.mustafaqasimov.ordertracker.exception.error.ResourceNotFoundException;
import com.mustafaqasimov.ordertracker.mapper.WebhookLogMapper;
import com.mustafaqasimov.ordertracker.repository.WebhookLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookLogService {

    private final WebhookLogRepository webhookLogRepository;
    private final WebhookLogMapper webhookLogMapper;

    // ---- writes (each in its own transaction so the log survives even
    //      if the surrounding processing is rolled back) ----

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookLog saveIncoming(WebhookSource source, String eventType,
                                   String externalReference, String payload) {
        return webhookLogRepository.save(WebhookLog.builder()
                .source(source)
                .eventType(eventType)
                .externalReference(externalReference)
                .payload(payload)
                .status(WebhookLogStatus.RECEIVED)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(Long logId, Order order) {
        WebhookLog entry = webhookLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookLog not found: " + logId));
        entry.setStatus(WebhookLogStatus.PROCESSED);
        entry.setProcessedAt(LocalDateTime.now());
        entry.setOrder(order);
        webhookLogRepository.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long logId, String errorMessage) {
        WebhookLog entry = webhookLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookLog not found: " + logId));
        entry.setStatus(WebhookLogStatus.FAILED);
        entry.setProcessedAt(LocalDateTime.now());
        if (errorMessage != null && errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 1000);
        }
        entry.setErrorMessage(errorMessage);
        webhookLogRepository.save(entry);
    }

    // ---- admin queries ----

    @Transactional(readOnly = true)
    public Page<WebhookLogResponse> search(WebhookSource source,
                                           WebhookLogStatus status,
                                           LocalDateTime from,
                                           LocalDateTime to,
                                           Pageable pageable) {
        Specification<WebhookLog> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (source != null) preds.add(cb.equal(root.get("source"), source));
            if (status != null) preds.add(cb.equal(root.get("status"), status));
            if (from != null) preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(preds.toArray(new Predicate[0]));
        };
        return webhookLogRepository.findAll(spec, pageable).map(webhookLogMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public WebhookLogResponse getById(Long id) {
        WebhookLog entry = webhookLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookLog not found: " + id));
        return webhookLogMapper.toResponse(entry);
    }
}
