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
@Table(name = "product_refund_command")
public class ProductRefundCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String commandNo;

    @Column(nullable = false, length = 30)
    private String commandType;

    @Column(nullable = false, length = 30)
    private String commandStatus;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
