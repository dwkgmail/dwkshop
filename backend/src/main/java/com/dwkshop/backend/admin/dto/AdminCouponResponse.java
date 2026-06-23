package com.dwkshop.backend.admin.dto;

import java.time.LocalDateTime;

public record AdminCouponResponse(
    Long id,
    String couponCode,
    String name,
    String couponType,
    Integer thresholdAmount,
    String thresholdAmountText,
    Integer discountAmount,
    String discountAmountText,
    Integer discountRate,
    Integer totalQuantity,
    Integer receivedQuantity,
    Integer usedQuantity,
    LocalDateTime receiveStartTime,
    LocalDateTime receiveEndTime,
    LocalDateTime useStartTime,
    LocalDateTime useEndTime,
    String couponStatus
) {
}
