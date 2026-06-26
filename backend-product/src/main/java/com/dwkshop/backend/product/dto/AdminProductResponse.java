package com.dwkshop.backend.product.dto;

public record AdminProductResponse(
    Long id,
    Long categoryId,
    String productCode,
    String name,
    String brandName,
    String subtitle,
    String mainImageUrl,
    String productType,
    String saleStatus,
    String deliveryType,
    Boolean allowCart,
    Boolean allowSingleBuy,
    Boolean pointDeductEnabled,
    Boolean supportRefund,
    Boolean pointRewardEnabled,
    Integer pointReward,
    Integer virtualSales,
    Integer actualSales,
    Integer minSalePrice,
    String minSalePriceText,
    Integer stock
) {
}
