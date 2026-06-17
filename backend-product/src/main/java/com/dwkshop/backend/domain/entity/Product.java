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
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false, unique = true, length = 64)
    private String productCode;

    @Column(nullable = false, length = 120)
    private String name;

    private String subtitle;

    @Column(nullable = false)
    private String mainImageUrl;

    @Column(nullable = false, length = 30)
    private String productType;

    @Column(nullable = false, length = 20)
    private String saleStatus;

    @Column(nullable = false, length = 20)
    private String deliveryType;

    @Column(nullable = false)
    private Boolean allowCart;

    @Column(nullable = false)
    private Boolean allowSingleBuy;

    @Column(nullable = false)
    private Boolean supportPointDeduction;

    @Column(nullable = false)
    private Boolean supportPointReward;

    @Column(nullable = false)
    private Integer pointReward;

    @Column(nullable = false)
    private Integer virtualSales;

    @Column(nullable = false)
    private Integer actualSales;

    @Column(nullable = false)
    private Boolean deletedFlag;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
