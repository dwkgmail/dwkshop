package com.dwkshop.backend.order.dto;

import java.util.List;

public record ConfirmOrderResponse(
    String settlementToken,
    String sourceType,
    OrderAddressResponse address,
    List<ConfirmOrderItemResponse> items,
    Integer freightAmount,
    String freightAmountText,
    ConfirmCouponResponse selectedCoupon,
    List<ConfirmCouponResponse> availableCoupons,
    PointDeductionResponse pointDeduction,
    OrderAmountResponse amount,
    String remark
) {
}
