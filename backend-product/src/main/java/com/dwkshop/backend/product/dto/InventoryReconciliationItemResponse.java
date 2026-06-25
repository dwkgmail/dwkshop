package com.dwkshop.backend.product.dto;

import java.util.List;

public record InventoryReconciliationItemResponse(
    Long skuId,
    String skuCode,
    String skuName,
    Long productId,
    String productName,
    Integer currentStock,
    Integer projectedLockedStock,
    Integer actualLockedStock,
    Integer difference,
    boolean autoRepairAllowed,
    List<InventoryReconciliationOrderResponse> relatedOrders,
    List<InventoryReconciliationEventResponse> recentEvents,
    List<InventoryRepairRecordResponse> repairRecords
) {
}
