package com.dwkshop.backend.product.dto;

import java.time.LocalDateTime;

public record InventoryReconciliationEventResponse(
    String eventId,
    Long orderId,
    String eventType,
    LocalDateTime consumedAt
) {
}
