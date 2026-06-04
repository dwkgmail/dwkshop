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
@Table(name = "product_notice")
public class ProductNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false, length = 120)
    private String noticeTitle;

    @Column(nullable = false, length = 2000)
    private String noticeContent;

    @Column(nullable = false)
    private Boolean enabledFlag;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
