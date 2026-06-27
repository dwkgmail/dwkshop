package com.dwkshop.backend.order.dto;

import java.util.List;

public record PromotionTraceResponse(
    String promotionType,
    String sourceId,
    String ruleId,
    String name,
    Integer discountAmount,
    String discountAmountText,
    List<PromotionTraceItemResponse> items
) {
}
