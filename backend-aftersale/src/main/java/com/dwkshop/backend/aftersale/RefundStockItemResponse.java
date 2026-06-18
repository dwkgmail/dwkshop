package com.dwkshop.backend.aftersale;

public record RefundStockItemResponse(
    Long skuId,
    String skuName,
    Integer stock,
    Integer lockedStock,
    String skuStatus,
    Integer quantity,
    Integer stockDelta,
    Integer lockedStockDelta
) {
}
