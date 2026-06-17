package com.dwkshop.backend.product.dto;

public record LockSkuStockResponse(
    Long skuId,
    String skuName,
    Integer salePrice,
    Integer stock,
    Integer lockedStock,
    String skuStatus
) {
}
