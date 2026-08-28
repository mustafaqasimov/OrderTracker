package com.mustafaqasimov.ordertracker.service;

import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailNotificationService")
class EmailNotificationServiceTest {

    @Mock JavaMailSender mailSender;
    @InjectMocks EmailNotificationService emailService;

    private Order order;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "no-reply@ordertracker.local");
        order = TestFixtures.order(1L, TestFixtures.user(7L, "user@test.local"), OrderStatus.PAID);
    }

    private SimpleMailMessage captureSentMessage() {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        return sent.getValue();
    }

    @Test
    @DisplayName("the envelope is addressed to the customer and carries the order number")
    void envelope() {
        emailService.sendOrderStatusChangedEmail(order, OrderStatus.PENDING, OrderStatus.PAID);

        SimpleMailMessage message = captureSentMessage();
        assertThat(message.getFrom()).isEqualTo("no-reply@ordertracker.local");
        assertThat(message.getTo()).containsExactly("user@test.local");
        assertThat(message.getSubject())
                .contains("ORD-20260101-ABCD1234")
                .contains("PAID");
    }

    @Test
    @DisplayName("the body names both statuses and the total")
    void bodyContent() {
        emailService.sendOrderStatusChangedEmail(order, OrderStatus.PENDING, OrderStatus.PAID);

        String body = captureSentMessage().getText();
        assertThat(body)
                .contains("ORD-20260101-ABCD1234")
                .contains("Previous status: PENDING")
                .contains("Current status:  PAID")
                .contains("59.98 USD")
                .contains("We have received your payment");
    }

    @Test
    @DisplayName("with no previous status the body simply omits that line")
    void bodyWithoutPreviousStatus() {
        emailService.sendOrderStatusChangedEmail(order, null, OrderStatus.PAID);

        assertThat(captureSentMessage().getText()).doesNotContain("Previous status");
    }

    @ParameterizedTest(name = "{0} adds its own wording")
    @CsvSource({
            "PAID,We have received your payment",
            "PAYMENT_FAILED,did not go through",
            "SHIPPED,has been shipped",
            "DELIVERED,has been delivered",
            "CANCELLED,has been cancelled",
            "REFUNDED,has been refunded"
    })
    void statusSpecificWording(OrderStatus status, String expectedPhrase) {
        emailService.sendOrderStatusChangedEmail(order, OrderStatus.PENDING, status);

        assertThat(captureSentMessage().getText()).contains(expectedPhrase);
    }

    @Test
    @DisplayName("a SHIPPED mail carries the tracking number when there is one")
    void shippedIncludesTrackingNumber() {
        order.setShipmentTrackingNumber("TRK-9");

        emailService.sendOrderStatusChangedEmail(order, OrderStatus.PAID, OrderStatus.SHIPPED);

        assertThat(captureSentMessage().getText()).contains("Tracking number: TRK-9");
    }

    @Test
    @DisplayName("a SHIPPED mail without a tracking number does not print an empty line for it")
    void shippedWithoutTrackingNumber() {
        emailService.sendOrderStatusChangedEmail(order, OrderStatus.PAID, OrderStatus.SHIPPED);

        assertThat(captureSentMessage().getText()).doesNotContain("Tracking number");
    }

    @Test
    @DisplayName("a mail failure propagates so @Retryable can retry it")
    void mailFailurePropagates() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        assertThatThrownBy(() ->
                emailService.sendOrderStatusChangedEmail(order, OrderStatus.PENDING, OrderStatus.PAID))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("the @Recover fallback logs and gives up quietly")
    void recoverSwallowsTheFailure() {
        assertThatCode(() -> emailService.recoverEmail(
                new MailSendException("SMTP down"), order, OrderStatus.PENDING, OrderStatus.PAID))
                .doesNotThrowAnyException();
    }
}
