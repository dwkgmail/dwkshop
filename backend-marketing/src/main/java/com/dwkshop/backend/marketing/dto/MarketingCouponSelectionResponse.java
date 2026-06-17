package com.dwkshop.backend.marketing.dto;

import java.util.List;

public record MarketingCouponSelectionResponse(
    Long selectedUserCouponId,
    Integer discountAmount,
    List<MarketingCouponResponse> availableCoupons
) {
}
