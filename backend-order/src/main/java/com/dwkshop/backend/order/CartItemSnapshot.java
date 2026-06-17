package com.dwkshop.backend.order;

public record CartItemSnapshot(
    Long id,
    Long userId,
    Long productId,
    Long skuId,
    Integer quantity,
    Boolean checked,
    String status
) {
}
