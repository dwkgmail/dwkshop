package com.dwkshop.backend.order;

public record LockSkuStockResponse(
    Long skuId,
    String skuName,
    Integer salePrice,
    Integer stock,
    Integer lockedStock,
    String skuStatus
) {
}
