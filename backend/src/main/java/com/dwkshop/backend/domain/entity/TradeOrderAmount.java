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
@Table(name = "trade_order_amount")
public class TradeOrderAmount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Integer productAmount;

    @Column(nullable = false)
    private Integer activityDiscountAmount;

    @Column(nullable = false)
    private Integer couponDiscountAmount;

    @Column(nullable = false)
    private Integer pointDiscountAmount;

    @Column(nullable = false)
    private Integer freightAmount;

    @Column(nullable = false)
    private Integer freightDiscountAmount;

    @Column(nullable = false)
    private Integer payAmount;

    private LocalDateTime createdAt;
}
