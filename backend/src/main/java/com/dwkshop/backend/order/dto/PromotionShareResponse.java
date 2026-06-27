package com.dwkshop.backend.order.dto;

public record PromotionShareResponse(
    String promotionType,
    String sourceId,
    String ruleId,
    String name,
    Integer discountAmount,
    String discountAmountText
) {
}
