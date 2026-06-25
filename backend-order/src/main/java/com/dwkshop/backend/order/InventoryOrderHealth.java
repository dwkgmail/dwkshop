package com.dwkshop.backend.order;

import java.util.List;

public record InventoryOrderHealth(
    long pendingOutboxBacklog,
    List<Long> waitPayOrderIds
) {
}
