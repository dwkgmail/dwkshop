package com.dwkshop.backend.aftersale;

public record RefundStockItemRequest(
    Long skuId,
    Integer quantity
) {
}
