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
@Table(name = "product_sku")
public class ProductSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, unique = true, length = 64)
    private String skuCode;

    @Column(nullable = false, length = 120)
    private String skuName;

    @Column(nullable = false, length = 500)
    private String specJson;

    private String imageUrl;

    @Column(nullable = false)
    private Integer salePrice;

    private Integer linePrice;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Integer lockedStock;

    @Column(nullable = false, length = 20)
    private String skuStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
