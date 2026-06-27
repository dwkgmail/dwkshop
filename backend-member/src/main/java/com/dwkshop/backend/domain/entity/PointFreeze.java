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
@Table(name = "point_freeze")
public class PointFreeze {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, unique = true, length = 64)
    private String bizNo;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(nullable = false)
    private Integer freezePoints;

    @Column(nullable = false)
    private Integer beforeAvailablePoints;

    @Column(nullable = false)
    private Integer afterAvailablePoints;

    @Column(nullable = false)
    private Integer beforeLockedPoints;

    @Column(nullable = false)
    private Integer afterLockedPoints;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    private LocalDateTime frozenAt;

    private LocalDateTime releasedAt;

    private LocalDateTime deductedAt;

    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
