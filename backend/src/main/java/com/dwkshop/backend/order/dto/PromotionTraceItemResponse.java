package com.dwkshop.backend.order.dto;

public record PromotionTraceItemResponse(
    Long cartItemId,
    Long productId,
    Long skuId,
    Integer shareAmount,
    String shareAmountText
) {
}
