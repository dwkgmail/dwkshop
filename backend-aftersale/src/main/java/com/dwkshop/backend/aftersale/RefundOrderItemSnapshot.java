package com.dwkshop.backend.aftersale;

public record RefundOrderItemSnapshot(
    Long skuId,
    Long productId,
    Integer quantity,
    Boolean supportRefund
) {
}
