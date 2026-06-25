package com.dwkshop.backend.product;

import java.util.List;

public record InventoryOrderHealth(
    long pendingOutboxBacklog,
    List<Long> waitPayOrderIds
) {
    static InventoryOrderHealth empty() {
        return new InventoryOrderHealth(0, List.of());
    }
}
