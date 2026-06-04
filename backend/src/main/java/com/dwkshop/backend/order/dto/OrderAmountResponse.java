package com.dwkshop.backend.order.dto;

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
    String payAmountText
) {
}
