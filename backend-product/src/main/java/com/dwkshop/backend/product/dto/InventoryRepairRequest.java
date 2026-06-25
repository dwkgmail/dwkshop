package com.dwkshop.backend.product.dto;

public record InventoryRepairRequest(
    String operator,
    String reason
) {
}
