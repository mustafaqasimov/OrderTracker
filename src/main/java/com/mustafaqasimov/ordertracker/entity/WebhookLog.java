package com.mustafaqasimov.ordertracker.entity;

import com.mustafaqasimov.ordertracker.enums.WebhookLogStatus;
import com.mustafaqasimov.ordertracker.enums.WebhookSource;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "webhook_logs")
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WebhookLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    WebhookSource source;

    @Column(name = "event_type", length = 80)
    String eventType;

    @Column(name = "external_reference", length = 150)
    String externalReference;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    WebhookLogStatus status;

    @Column(name = "error_message", length = 1000)
    String errorMessage;

    @Column(name = "processed_at")
    LocalDateTime processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    Order order;
}
