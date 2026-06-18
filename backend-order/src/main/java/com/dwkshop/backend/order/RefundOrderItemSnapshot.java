package com.dwkshop.backend.order;

public record RefundOrderItemSnapshot(
    Long skuId,
    Long productId,
    Integer quantity,
    Boolean supportRefund
) {
}
