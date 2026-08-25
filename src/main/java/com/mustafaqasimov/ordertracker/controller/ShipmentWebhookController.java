package com.mustafaqasimov.ordertracker.controller;

import com.mustafaqasimov.ordertracker.dto.request.ShipmentWebhookRequest;
import com.mustafaqasimov.ordertracker.exception.error.UnauthorizedException;
import com.mustafaqasimov.ordertracker.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/shipment")
@RequiredArgsConstructor
@Tag(name = "Webhooks — Shipment", description = "Endpoint called by the shipping carrier")
public class ShipmentWebhookController {

    private final WebhookService webhookService;

    @Value("${app.webhook.secret:changeit}")
    private String expectedSecret;

    @Operation(summary = "Receive a shipment status update from the carrier", description = "Handles incoming shipment status updates from the shipping carrier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shipment status update received successfully")
    })
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String providedSecret,
            @Valid @RequestBody ShipmentWebhookRequest request) {

        verifySecret(providedSecret);
        webhookService.handleShipmentWebhook(request);
        return ResponseEntity.ok().build();
    }

    private void verifySecret(String providedSecret) {
        if (providedSecret == null || !providedSecret.equals(expectedSecret)) {
            throw new UnauthorizedException("Invalid webhook secret");
        }
    }
}
