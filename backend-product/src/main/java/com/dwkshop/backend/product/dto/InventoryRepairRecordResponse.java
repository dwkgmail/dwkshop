package com.dwkshop.backend.product.dto;

import java.time.LocalDateTime;

public record InventoryRepairRecordResponse(
    Long id,
    Long skuId,
    Integer beforeLockedStock,
    Integer projectedLockedStock,
    Integer difference,
    String repairType,
    String repairStatus,
    String operator,
    String reason,
    LocalDateTime createdAt
) {
}
