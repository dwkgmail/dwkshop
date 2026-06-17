package com.dwkshop.backend.order.dto;

import java.time.LocalDateTime;

public record OrderSummaryResponse(
    Long id,
    String orderNo,
    Long userId,
    String orderStatus,
    String payStatus,
    String deliveryStatus,
    String aftersaleStatus,
    Integer payAmount,
    String payAmountText,
    LocalDateTime createdAt
) {
}
