package com.dwkshop.backend.product.dto;

public record InventoryOrderItemStateResponse(
    Long skuId,
    Integer quantity,
    String state
) {
}
