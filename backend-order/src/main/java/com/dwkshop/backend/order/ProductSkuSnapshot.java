package com.dwkshop.backend.order;

public record ProductSkuSnapshot(
    Long productId,
    Long skuId,
    Long categoryId,
    String productName,
    String brandName,
    String productImageUrl,
    String saleStatus,
    String deliveryType,
    Boolean deletedFlag,
    Boolean allowCart,
    Boolean allowSingleBuy,
    Boolean supportPointDeduction,
    Boolean supportRefund,
    Integer snapshotVersion,
    String noticeTitle,
    String noticeContent,
    String skuName,
    String specJson,
    Integer salePrice,
    Integer stock,
    String skuStatus
) {
}
