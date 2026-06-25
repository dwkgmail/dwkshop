package com.dwkshop.backend.product.dto;

import java.time.LocalDateTime;

public record InventoryReconciliationOrderResponse(
    Long orderId,
    String orderNo,
    Integer quantity,
    String state,
    LocalDateTime updatedAt
) {
}
