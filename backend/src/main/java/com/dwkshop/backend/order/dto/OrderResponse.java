package com.dwkshop.backend.order.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
    Long id,
    String orderNo,
    Long userId,
    String orderStatus,
    String payStatus,
    String deliveryStatus,
    Integer payAmount,
    String payAmountText,
    String receiverName,
    String receiverMobile,
    String receiverAddress,
    String remark,
    LocalDateTime payExpireTime,
    LocalDateTime createdAt,
    OrderAmountResponse amount,
    List<OrderItemResponse> items
) {
}
