package com.dwkshop.backend.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "inventory_consumed_event", uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_event_sku", columnNames = {"event_id", "sku_id"}),
    @UniqueConstraint(name = "uk_inventory_business_event", columnNames = {"order_id", "sku_id", "event_type"})
})
public class InventoryConsumedEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false, length = 64) private String eventId;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "sku_id", nullable = false) private Long skuId;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(name = "consumed_at", nullable = false) private LocalDateTime consumedAt;
}
