package com.dwkshop.backend.order;

public record MarketingCoupon(
    Long userCouponId,
    Long couponId,
    String name,
    String couponType,
    Integer thresholdAmount,
    Integer discountAmount,
    Boolean selected
) {
}
