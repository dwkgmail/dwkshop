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
@Table(name = "aftersale_order")
public class AftersaleOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String aftersaleNo;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String aftersaleType;

    @Column(nullable = false, length = 30)
    private String aftersaleStatus;

    @Column(nullable = false)
    private Integer refundAmount;

    @Column(nullable = false)
    private String reason;

    private String rejectReason;

    private LocalDateTime applyTime;

    private LocalDateTime auditTime;

    private LocalDateTime refundTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
