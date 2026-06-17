package com.dwkshop.backend.product.dto;

public record ProductSkuSnapshotResponse(
    Long productId,
    Long skuId,
    String productName,
    String productImageUrl,
    String saleStatus,
    String deliveryType,
    Boolean deletedFlag,
    Boolean allowCart,
    Boolean allowSingleBuy,
    Boolean supportPointDeduction,
    String noticeTitle,
    String noticeContent,
    String skuName,
    String specJson,
    Integer salePrice,
    Integer stock,
    String skuStatus
) {
}
