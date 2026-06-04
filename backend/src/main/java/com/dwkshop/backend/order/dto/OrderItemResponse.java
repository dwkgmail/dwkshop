package com.dwkshop.backend.order.dto;

public record OrderItemResponse(
    Long id,
    Long productId,
    Long skuId,
    String productName,
    String skuName,
    String productImageUrl,
    Integer salePrice,
    String salePriceText,
    Integer quantity,
    Integer payAmount,
    String payAmountText
) {
}
