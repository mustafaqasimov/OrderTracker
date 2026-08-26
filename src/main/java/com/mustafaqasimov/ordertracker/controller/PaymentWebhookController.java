package com.mustafaqasimov.ordertracker.controller;

import com.mustafaqasimov.ordertracker.dto.request.PaymentWebhookRequest;
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
@RequestMapping("/api/webhooks/payment")
@RequiredArgsConstructor
@Tag(name = "Webhooks — Payment", description = "Endpoint called by the payment gateway")
public class PaymentWebhookController {

    private final WebhookService webhookService;

    @Value("${app.webhook.secret}")
    private String expectedSecret;

    @Operation(summary = "Receive a payment status update from the gateway", description = "Handles incoming payment status updates from the payment gateway")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment status update received successfully")
    })
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String providedSecret,
            @Valid @RequestBody PaymentWebhookRequest request) {

        verifySecret(providedSecret);
        webhookService.handlePaymentWebhook(request);
        return ResponseEntity.ok().build();
    }

    private void verifySecret(String providedSecret) {
        if (providedSecret == null || !providedSecret.equals(expectedSecret)) {
            throw new UnauthorizedException("Invalid webhook secret");
        }
    }
}
