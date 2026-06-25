package com.dwkshop.backend.product.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InventoryReconciliationResponse(
    LocalDateTime checkedAt,
    List<InventoryReconciliationItemResponse> items,
    List<InventoryHealthCheckResponse> checks
) {
}
