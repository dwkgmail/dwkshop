package com.dwkshop.backend.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "order_outbox_event", uniqueConstraints = {
    @UniqueConstraint(name = "uk_order_outbox_event_id", columnNames = "event_id"),
    @UniqueConstraint(name = "uk_order_outbox_aggregate_type", columnNames = {"aggregate_id", "event_type"})
})
public class OrderOutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false, length = 64) private String eventId;
    @Column(name = "aggregate_id", nullable = false) private Long aggregateId;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(name = "routing_key", nullable = false, length = 80) private String routingKey;
    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT") private String payloadJson;
    @Column(name = "publish_status", nullable = false, length = 20) private String publishStatus;
    @Column(name = "retry_count", nullable = false) private Integer retryCount;
    @Column(name = "next_retry_at") private LocalDateTime nextRetryAt;
    @Column(name = "last_error") private String lastError;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
