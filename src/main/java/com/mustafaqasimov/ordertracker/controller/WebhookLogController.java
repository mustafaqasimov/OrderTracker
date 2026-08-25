package com.mustafaqasimov.ordertracker.controller;

import com.mustafaqasimov.ordertracker.dto.response.WebhookLogResponse;
import com.mustafaqasimov.ordertracker.enums.WebhookLogStatus;
import com.mustafaqasimov.ordertracker.enums.WebhookSource;
import com.mustafaqasimov.ordertracker.service.WebhookLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/webhook-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin — Webhook Logs", description = "Audit of received webhook events")
public class WebhookLogController {

    private final WebhookLogService webhookLogService;

    @Operation(summary = "List webhook logs (filter by source, status, date range)")
    @GetMapping
    public ResponseEntity<Page<WebhookLogResponse>> list(
            @RequestParam(required = false) WebhookSource source,
            @RequestParam(required = false) WebhookLogStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(webhookLogService.search(source, status, from, to, pageable));
    }

    @Operation(summary = "Get one webhook log entry with its full payload")
    @GetMapping("/{id}")
    public ResponseEntity<WebhookLogResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(webhookLogService.getById(id));
    }
}
