package com.dwkshop.backend.order;

public record LockCouponRequest(
    String lockKey,
    Integer productAmount
) {
}
