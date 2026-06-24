package com.dwkshop.backend.order;

public record RefundOrderItemSnapshot(
    Long skuId,
    Long productId,
    Integer quantity,
    Integer refundableQuantity,
    Integer refundedQuantity,
    Integer aftersaleQuantity,
    Integer payAmount,
    Integer refundAmount,
    String refundStatus,
    Boolean supportRefund
) {
}
