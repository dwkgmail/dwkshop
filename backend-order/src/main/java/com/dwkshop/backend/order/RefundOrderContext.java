package com.dwkshop.backend.order;

import java.util.List;

public record RefundOrderContext(
    Long orderId,
    String orderNo,
    Long userId,
    String orderStatus,
    String payStatus,
    String deliveryStatus,
    String aftersaleStatus,
    Integer payAmount,
    Boolean refundable,
    List<RefundOrderItemSnapshot> items
) {
}
