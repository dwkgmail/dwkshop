package com.dwkshop.backend.product;

public record InventoryOrderSummary(
    Long id,
    String orderNo,
    String orderStatus
) {
}
