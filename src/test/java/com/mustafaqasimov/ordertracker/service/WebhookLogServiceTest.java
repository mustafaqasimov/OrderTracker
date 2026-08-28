package com.mustafaqasimov.ordertracker.service;

import com.mustafaqasimov.ordertracker.dto.response.WebhookLogResponse;
import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.entity.WebhookLog;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.WebhookLogStatus;
import com.mustafaqasimov.ordertracker.enums.WebhookSource;
import com.mustafaqasimov.ordertracker.exception.error.ResourceNotFoundException;
import com.mustafaqasimov.ordertracker.mapper.WebhookLogMapper;
import com.mustafaqasimov.ordertracker.repository.WebhookLogRepository;
import com.mustafaqasimov.ordertracker.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookLogService")
class WebhookLogServiceTest {

    @Mock WebhookLogRepository webhookLogRepository;
    @Mock WebhookLogMapper webhookLogMapper;

    @InjectMocks WebhookLogService webhookLogService;

    private WebhookLog storedLog(Long id) {
        WebhookLog log = WebhookLog.builder()
                .source(WebhookSource.PAYMENT)
                .eventType("payment.succeeded")
                .status(WebhookLogStatus.RECEIVED)
                .build();
        log.setId(id);
        return log;
    }

    @Test
    @DisplayName("saveIncoming stores the call with status RECEIVED")
    void saveIncoming() {
        when(webhookLogRepository.save(any(WebhookLog.class))).thenAnswer(inv -> inv.getArgument(0));

        webhookLogService.saveIncoming(WebhookSource.PAYMENT, "payment.succeeded", "pi_1", "{\"a\":1}");

        ArgumentCaptor<WebhookLog> saved = ArgumentCaptor.forClass(WebhookLog.class);
        verify(webhookLogRepository).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(WebhookSource.PAYMENT);
        assertThat(saved.getValue().getEventType()).isEqualTo("payment.succeeded");
        assertThat(saved.getValue().getExternalReference()).isEqualTo("pi_1");
        assertThat(saved.getValue().getPayload()).isEqualTo("{\"a\":1}");
        assertThat(saved.getValue().getStatus()).isEqualTo(WebhookLogStatus.RECEIVED);
        assertThat(saved.getValue().getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markProcessed links the order and stamps the time")
    void markProcessed() {
        WebhookLog log = storedLog(1L);
        Order order = TestFixtures.order(5L, TestFixtures.user(7L, "u@test.local"), OrderStatus.PAID);
        when(webhookLogRepository.findById(1L)).thenReturn(Optional.of(log));

        webhookLogService.markProcessed(1L, order);

        assertThat(log.getStatus()).isEqualTo(WebhookLogStatus.PROCESSED);
        assertThat(log.getOrder()).isSameAs(order);
        assertThat(log.getProcessedAt()).isNotNull();
        verify(webhookLogRepository).save(log);
    }

    @Test
    @DisplayName("markFailed stores the error message")
    void markFailed() {
        WebhookLog log = storedLog(1L);
        when(webhookLogRepository.findById(1L)).thenReturn(Optional.of(log));

        webhookLogService.markFailed(1L, "Order not found");

        assertThat(log.getStatus()).isEqualTo(WebhookLogStatus.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("Order not found");
        assertThat(log.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("a very long error message is truncated to the column width")
    void truncatesLongErrorMessage() {
        WebhookLog log = storedLog(1L);
        when(webhookLogRepository.findById(1L)).thenReturn(Optional.of(log));

        webhookLogService.markFailed(1L, "x".repeat(5000));

        assertThat(log.getErrorMessage()).hasSize(1000);
    }

    @Test
    @DisplayName("a null error message is stored as null, not as the string \"null\"")
    void nullErrorMessage() {
        WebhookLog log = storedLog(1L);
        when(webhookLogRepository.findById(1L)).thenReturn(Optional.of(log));

        webhookLogService.markFailed(1L, null);

        assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("marking an unknown log id fails")
    void markingUnknownLog() {
        when(webhookLogRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookLogService.markProcessed(404L, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> webhookLogService.markFailed(404L, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("search passes a specification through to the repository and maps the page")
    void search() {
        Pageable pageable = PageRequest.of(0, 10);
        when(webhookLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(storedLog(1L))));
        when(webhookLogMapper.toResponse(any())).thenReturn(WebhookLogResponse.builder().build());

        var page = webhookLogService.search(WebhookSource.PAYMENT, WebhookLogStatus.FAILED,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(webhookLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getById maps the entry it found")
    void getById() {
        WebhookLogResponse expected = WebhookLogResponse.builder().build();
        WebhookLog log = storedLog(1L);
        when(webhookLogRepository.findById(1L)).thenReturn(Optional.of(log));
        when(webhookLogMapper.toResponse(log)).thenReturn(expected);

        assertThat(webhookLogService.getById(1L)).isSameAs(expected);
    }

    @Test
    @DisplayName("getById fails for an unknown id")
    void getByIdUnknown() {
        when(webhookLogRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookLogService.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }
}
