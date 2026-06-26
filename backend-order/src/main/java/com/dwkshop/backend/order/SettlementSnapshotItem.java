package com.dwkshop.backend.order;

public record SettlementSnapshotItem(
    Long cartItemId,
    ProductSkuSnapshot sku,
    int quantity
) {
}
