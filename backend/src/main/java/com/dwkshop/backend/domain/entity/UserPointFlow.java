package com.dwkshop.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_point_flow")
public class UserPointFlow {

    @Id
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

    private String remark;

    private LocalDateTime createdAt;
}
