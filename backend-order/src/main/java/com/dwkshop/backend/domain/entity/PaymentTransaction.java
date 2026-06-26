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
@Table(name = "payment_transaction")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String paymentNo;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, unique = true, length = 64)
    private String requestNo;

    @Column(length = 128)
    private String channelTradeNo;

    private LocalDateTime paidAt;

    private LocalDateTime closedAt;

    @Column(columnDefinition = "LONGTEXT")
    private String callbackPayload;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
