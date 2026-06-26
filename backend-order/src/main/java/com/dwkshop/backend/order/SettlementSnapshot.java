package com.dwkshop.backend.order;

import com.dwkshop.backend.order.dto.ConfirmCouponResponse;
import com.dwkshop.backend.order.dto.ConfirmOrderRequest;
import com.dwkshop.backend.order.dto.OrderAmountResponse;
import java.util.List;

public record SettlementSnapshot(
    Long userId,
    ConfirmOrderRequest request,
    String sourceType,
    MemberAddress address,
    List<SettlementSnapshotItem> items,
    Long selectedUserCouponId,
    List<ConfirmCouponResponse> availableCoupons,
    SettlementPointSnapshot pointSelection,
    OrderAmountResponse amount
) {
}
