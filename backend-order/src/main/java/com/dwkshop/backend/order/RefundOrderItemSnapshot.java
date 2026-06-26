package com.dwkshop.backend.order;

public record RefundOrderItemSnapshot(
    Long skuId,
    Long productId,
    Integer quantity,
    Integer refundableQuantity,
    Integer refundedQuantity,
    Integer aftersaleQuantity,
    Integer payAmount,
    Integer itemPayAmount,
    Integer couponShareAmount,
    Integer pointShareAmount,
    Integer freightShareAmount,
    Integer refundAmount,
    Integer refundedAmount,
    Integer refundableAmount,
    String refundStatus,
    Boolean supportRefund
) {
}
