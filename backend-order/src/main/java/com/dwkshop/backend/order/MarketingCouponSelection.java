package com.dwkshop.backend.order;

import java.util.List;

public record MarketingCouponSelection(
    Long selectedUserCouponId,
    Integer discountAmount,
    List<MarketingCoupon> availableCoupons
) {
}
