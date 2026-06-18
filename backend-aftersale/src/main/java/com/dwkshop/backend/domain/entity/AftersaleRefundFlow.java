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
@Table(name = "aftersale_refund_flow")
public class AftersaleRefundFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long aftersaleId;

    @Column(nullable = false, unique = true, length = 64)
    private String aftersaleNo;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 30)
    private String flowStatus;

    @Column(length = 30)
    private String currentStep;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(length = 255)
    private String lastError;

    @Column(nullable = false, unique = true, length = 64)
    private String commandNo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
