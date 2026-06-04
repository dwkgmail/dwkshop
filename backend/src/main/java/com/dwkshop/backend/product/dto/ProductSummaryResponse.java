package com.dwkshop.backend.product.dto;

public record ProductSummaryResponse(
    Long id,
    Long categoryId,
    String productCode,
    String name,
    String subtitle,
    String mainImageUrl,
    String saleStatus,
    String deliveryType,
    Boolean allowCart,
    Boolean allowSingleBuy,
    Boolean pointDeductEnabled,
    Integer minSalePrice,
    String minSalePriceText,
    Integer displayedSales
) {
}
