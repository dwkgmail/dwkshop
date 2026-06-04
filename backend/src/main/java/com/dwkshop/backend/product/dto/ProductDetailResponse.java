package com.dwkshop.backend.product.dto;

import java.util.List;

public record ProductDetailResponse(
    Long id,
    Long categoryId,
    String productCode,
    String name,
    String subtitle,
    String mainImageUrl,
    String productType,
    String saleStatus,
    Boolean offSale,
    String offSaleMessage,
    String deliveryType,
    Boolean allowCart,
    Boolean allowSingleBuy,
    Boolean pointDeductEnabled,
    Boolean pointRewardEnabled,
    Integer pointReward,
    Integer displayedSales,
    String noticeTitle,
    String noticeContent,
    List<ProductSkuResponse> skus
) {
}
