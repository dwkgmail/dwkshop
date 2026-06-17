package com.dwkshop.backend.product.dto;

public record ProductSkuSnapshotResponse(
    Long productId,
    Long skuId,
    String productName,
    String productImageUrl,
    String saleStatus,
    Boolean deletedFlag,
    Boolean allowCart,
    Boolean allowSingleBuy,
    Boolean supportPointDeduction,
    String skuName,
    String specJson,
    Integer salePrice,
    Integer stock,
    String skuStatus
) {
}
