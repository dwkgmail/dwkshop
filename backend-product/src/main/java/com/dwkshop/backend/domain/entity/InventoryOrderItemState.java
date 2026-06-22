package com.dwkshop.backend.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "inventory_order_item_state", uniqueConstraints =
    @UniqueConstraint(name = "uk_inventory_order_sku", columnNames = {"order_id", "sku_id"}))
public class InventoryOrderItemState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "sku_id", nullable = false) private Long skuId;
    @Column(nullable = false) private Integer quantity;
    @Column(nullable = false, length = 20) private String state;
    @Column(name = "last_event_version", nullable = false) private Integer lastEventVersion;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
