package com.dwkshop.backend.order;

public record AftersaleOrderSnapshot(
    Long id,
    String orderNo,
    Long userId,
    String receiverMobile,
    String orderStatus,
    String payStatus,
    String aftersaleStatus,
    Integer payAmount,
    Boolean refundable
) {
}
