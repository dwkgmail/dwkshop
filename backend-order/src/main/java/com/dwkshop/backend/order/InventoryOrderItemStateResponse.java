package com.dwkshop.backend.order;

public record InventoryOrderItemStateResponse(
    Long skuId,
    Integer quantity,
    String state
) {
}
