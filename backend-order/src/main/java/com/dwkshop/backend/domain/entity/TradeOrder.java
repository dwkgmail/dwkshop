package com.dwkshop.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "trade_order",
    uniqueConstraints = @UniqueConstraint(name = "uk_trade_order_user_client_request", columnNames = {"user_id", "client_request_id"})
)
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderNo;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 64)
    private String clientRequestId;

    @Column(nullable = false, length = 30)
    private String orderStatus;

    @Column(nullable = false, length = 30)
    private String payStatus;

    @Column(nullable = false, length = 30)
    private String deliveryStatus;

    @Column(nullable = false, length = 30)
    private String aftersaleStatus;

    @Column(nullable = false, length = 20)
    private String sourceType;

    @Column(nullable = false)
    private Integer totalAmount;

    @Column(nullable = false)
    private Integer discountAmount;

    @Column(nullable = false)
    private Integer couponAmount;

    private Long couponUserId;

    @Column(nullable = false)
    private Integer pointAmount;

    @Column(nullable = false)
    private Integer freightAmount;

    @Column(nullable = false)
    private Integer payAmount;

    @Column(nullable = false, length = 64)
    private String receiverName;

    @Column(nullable = false, length = 20)
    private String receiverMobile;

    @Column(nullable = false, length = 500)
    private String receiverAddress;

    private String remark;

    @Column(length = 64)
    private String logisticsCompany;

    @Column(length = 64)
    private String logisticsNo;

    @Column(length = 255)
    private String deliveryRemark;

    private LocalDateTime payExpireTime;

    private LocalDateTime payTime;

    private LocalDateTime deliveryTime;

    private LocalDateTime cancelTime;

    private LocalDateTime finishTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
