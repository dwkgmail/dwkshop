package com.dwkshop.backend.cart.dto;

public record CartItemSnapshotResponse(
    Long id,
    Long userId,
    Long productId,
    Long skuId,
    Integer quantity,
    Boolean checked,
    String status
) {
}
