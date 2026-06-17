package com.dwkshop.backend.aftersale.dto;

import java.time.LocalDateTime;

public record AftersaleResponse(
    Long id,
    String aftersaleNo,
    Long orderId,
    String orderNo,
    Long userId,
    String receiverMobile,
    String aftersaleType,
    String aftersaleStatus,
    Integer refundAmount,
    String refundAmountText,
    String reason,
    String rejectReason,
    LocalDateTime applyTime,
    LocalDateTime auditTime,
    LocalDateTime refundTime
) {
}
