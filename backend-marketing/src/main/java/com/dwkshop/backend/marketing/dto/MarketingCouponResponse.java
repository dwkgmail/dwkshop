package com.dwkshop.backend.marketing.dto;

public record MarketingCouponResponse(
    Long userCouponId,
    Long couponId,
    String name,
    String couponType,
    Integer thresholdAmount,
    Integer discountAmount,
    Boolean selected
) {
}
