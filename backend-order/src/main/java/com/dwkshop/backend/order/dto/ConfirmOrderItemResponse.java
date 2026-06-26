package com.dwkshop.backend.order.dto;

import java.util.List;

public record ConfirmOrderItemResponse(
    Long cartItemId,
    Long productId,
    Long skuId,
    String productName,
    String skuName,
    String productImageUrl,
    Integer salePrice,
    String salePriceText,
    Integer quantity,
    Integer totalAmount,
    String totalAmountText,
    Integer couponShareAmount,
    String couponShareAmountText,
    Integer pointShareAmount,
    String pointShareAmountText,
    Integer freightShareAmount,
    String freightShareAmountText,
    Integer payAmount,
    String payAmountText,
    List<PromotionShareResponse> promotionShares,
    Boolean allowSingleBuy,
    Boolean pointDeductEnabled,
    String noticeTitle,
    String noticeContent
) {
}
