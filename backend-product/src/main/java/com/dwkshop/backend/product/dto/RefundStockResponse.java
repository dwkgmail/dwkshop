package com.dwkshop.backend.product.dto;

import java.util.List;

public record RefundStockResponse(
    String commandNo,
    String commandType,
    String commandStatus,
    List<RefundStockItemResponse> items
) {
}
