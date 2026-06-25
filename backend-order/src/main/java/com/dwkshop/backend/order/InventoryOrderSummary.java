package com.dwkshop.backend.order;

public record InventoryOrderSummary(
    Long id,
    String orderNo,
    String orderStatus
) {
}
