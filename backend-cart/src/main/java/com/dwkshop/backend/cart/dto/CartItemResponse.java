package com.dwkshop.backend.cart.dto;

public record CartItemResponse(
    Long id,
    Long productId,
    Long skuId,
    String productName,
    String productImageUrl,
    String skuName,
    String specJson,
    Integer salePrice,
    String salePriceText,
    Integer quantity,
    Integer stock,
    Boolean checked,
    Boolean allowCart,
    Boolean allowSingleBuy,
    Boolean pointDeductEnabled,
    String status,
    String statusMessage,
    Boolean canCheck,
    Integer estimatedAmount,
    String estimatedAmountText
) {
}
