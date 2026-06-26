package com.dwkshop.backend.order.dto;

import java.util.List;

public record OrderAmountResponse(
    Integer productAmount,
    String productAmountText,
    Integer productDiscountAmount,
    String productDiscountAmountText,
    Integer couponDiscountAmount,
    String couponDiscountAmountText,
    Integer pointDiscountAmount,
    String pointDiscountAmountText,
    Integer freightAmount,
    String freightAmountText,
    Integer freightDiscountAmount,
    String freightDiscountAmountText,
    Integer payAmount,
    String payAmountText,
    List<PromotionTraceResponse> promotionTraces,
    String promotionTraceJson
) {
}
