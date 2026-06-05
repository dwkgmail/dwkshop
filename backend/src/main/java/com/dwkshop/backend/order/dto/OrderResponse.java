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
    String aftersaleStatus,
    Integer payAmount,
    String payAmountText,
    String receiverName,
    String receiverMobile,
    String receiverAddress,
    String remark,
    String logisticsCompany,
    String logisticsNo,
    String deliveryRemark,
    LocalDateTime payExpireTime,
    LocalDateTime payTime,
    LocalDateTime deliveryTime,
    LocalDateTime finishTime,
    LocalDateTime createdAt,
    OrderAmountResponse amount,
    List<OrderItemResponse> items
) {
}
