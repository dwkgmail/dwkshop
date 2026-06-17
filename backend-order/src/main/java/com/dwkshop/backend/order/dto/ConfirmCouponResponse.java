package com.dwkshop.backend.order.dto;

public record ConfirmCouponResponse(
    Long couponUserId,
    Long couponId,
    String name,
    String couponType,
    Integer thresholdAmount,
    String thresholdAmountText,
    Integer discountAmount,
    String discountAmountText,
    Boolean selected
) {
}
