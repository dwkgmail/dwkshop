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
@Table(name = "user_point_flow")
public class UserPointFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 64)
    private String flowNo;

    @Column(nullable = false, length = 30)
    private String changeType;

    @Column(nullable = false)
    private Integer changePoints;

    @Column(nullable = false)
    private Integer balanceAfter;

    @Column(nullable = false, length = 30)
    private String bizType;

    private Long bizId;

    private Long orderId;

    @Column(length = 64)
    private String bizNo;

    @Column(nullable = false)
    private Integer changeAmount;

    @Column(nullable = false)
    private Integer beforeBalance;

    @Column(nullable = false)
    private Integer afterBalance;

    @Column(nullable = false, length = 30)
    private String status;

    private String remark;

    private LocalDateTime createdAt;
}
