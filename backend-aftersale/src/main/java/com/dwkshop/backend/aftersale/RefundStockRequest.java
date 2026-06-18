package com.dwkshop.backend.aftersale;

import java.util.List;

public record RefundStockRequest(
    String commandNo,
    String commandType,
    List<RefundStockItemRequest> items
) {
}
