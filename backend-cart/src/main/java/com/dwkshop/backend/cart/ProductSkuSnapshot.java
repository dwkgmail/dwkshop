package com.dwkshop.backend.cart;

public record ProductSkuSnapshot(
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
