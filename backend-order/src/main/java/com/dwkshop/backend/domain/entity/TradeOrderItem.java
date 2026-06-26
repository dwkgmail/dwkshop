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
@Table(name = "trade_order_item")
public class TradeOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long skuId;

    @Column(nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, length = 120)
    private String skuName;

    @Column(columnDefinition = "TEXT")
    private String specJson = "{}";

    @Column(nullable = false)
    private Long categoryId = 0L;

    @Column(nullable = false, length = 120)
    private String brandName = "";

    @Column(nullable = false)
    private String productImageUrl;

    @Column(nullable = false)
    private Integer salePrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer totalAmount;

    @Column(nullable = false)
    private Integer discountAmount;

    @Column(nullable = false)
    private Integer payAmount;

    @Column(nullable = false)
    private Integer couponShareAmount;

    @Column(nullable = false)
    private Integer pointShareAmount;

    @Column(nullable = false)
    private Integer freightShareAmount;

    @Column(nullable = false, length = 20)
    private String deliveryType = "NORMAL";

    @Column(nullable = false)
    private Boolean supportRefund;

    @Column(nullable = false)
    private Boolean supportPointDeduction = false;

    @Column(nullable = false)
    private Integer snapshotVersion = 1;

    @Column(nullable = false)
    private Integer aftersaleQuantity;

    @Column(nullable = false)
    private Integer refundableQuantity;

    @Column(nullable = false)
    private Integer refundedQuantity;

    @Column(nullable = false)
    private Integer refundAmount;

    @Column(nullable = false)
    private Integer refundableAmount;

    @Column(nullable = false, length = 30)
    private String refundStatus;

    private LocalDateTime createdAt;
}
