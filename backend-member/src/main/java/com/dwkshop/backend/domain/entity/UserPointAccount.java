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
@Table(name = "user_point_account")
public class UserPointAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private Integer availablePoints;

    @Column(nullable = false)
    private Integer lockedPoints;

    @Column(nullable = false)
    private Integer totalEarnedPoints;

    @Column(nullable = false)
    private Integer totalUsedPoints;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
