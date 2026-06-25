package com.dwkshop.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "inventory_reconciliation_repair_record")
public class InventoryReconciliationRepairRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long skuId;

    @Column(nullable = false)
    private Integer beforeLockedStock;

    @Column(nullable = false)
    private Integer projectedLockedStock;

    @Column(nullable = false)
    private Integer difference;

    @Column(nullable = false, length = 30)
    private String repairType;

    @Column(nullable = false, length = 20)
    private String repairStatus;

    @Column(nullable = false, length = 64)
    private String operator;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
