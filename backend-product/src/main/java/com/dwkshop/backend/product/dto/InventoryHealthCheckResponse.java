package com.dwkshop.backend.product.dto;

public record InventoryHealthCheckResponse(
    String checkType,
    String status,
    long count,
    String message
) {
}
