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
@Table(name = "coupon_lock_flow")
public class CouponLockFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long couponUserId;

    @Column(nullable = false)
    private Long userId;

    private Long orderId;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(nullable = false, length = 96)
    private String bizNo;

    @Column(nullable = false, length = 30)
    private String flowType;

    @Column(nullable = false, length = 30)
    private String beforeStatus;

    @Column(nullable = false, length = 30)
    private String afterStatus;

    @Column(length = 96)
    private String lockKey;

    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(nullable = false)
    private LocalDateTime operatedAt;

    private LocalDateTime createdAt;
}
