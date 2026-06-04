package com.dwkshop.backend.product.dto;

public record ProductSkuResponse(
    Long id,
    String skuCode,
    String skuName,
    String specJson,
    String imageUrl,
    Integer salePrice,
    String salePriceText,
    Integer linePrice,
    String linePriceText,
    Integer stock,
    Integer lockedStock,
    String skuStatus,
    Boolean selectable
) {
}
