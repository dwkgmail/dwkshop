package com.dwkshop.backend.aftersale;

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
