package com.dwkshop.backend.order.dto;

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
    Boolean allowSingleBuy,
    Boolean pointDeductEnabled,
    String noticeTitle,
    String noticeContent
) {
}
