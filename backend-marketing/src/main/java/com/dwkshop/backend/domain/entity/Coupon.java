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
@Table(name = "coupon")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String couponCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 30)
    private String couponType;

    @Column(nullable = false)
    private Integer thresholdAmount;

    @Column(nullable = false)
    private Integer discountAmount;

    private Integer discountRate;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer receivedQuantity;

    @Column(nullable = false)
    private Integer usedQuantity;

    @Column(nullable = false)
    private LocalDateTime receiveStartTime;

    @Column(nullable = false)
    private LocalDateTime receiveEndTime;

    @Column(nullable = false)
    private LocalDateTime useStartTime;

    @Column(nullable = false)
    private LocalDateTime useEndTime;

    @Column(nullable = false, length = 20)
    private String couponStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
