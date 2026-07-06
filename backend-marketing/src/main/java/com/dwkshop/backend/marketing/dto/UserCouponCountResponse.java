package com.dwkshop.backend.marketing.dto;

public record UserCouponCountResponse(
    Long userId,
    long couponCount
) {
}
