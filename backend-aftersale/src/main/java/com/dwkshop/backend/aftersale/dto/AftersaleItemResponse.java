package com.dwkshop.backend.aftersale.dto;

public record AftersaleItemResponse(
    Long skuId,
    Long productId,
    Integer quantity,
    Integer refundAmount,
    String refundAmountText
) {
}
