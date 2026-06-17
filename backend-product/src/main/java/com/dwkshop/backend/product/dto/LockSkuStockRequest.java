package com.dwkshop.backend.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LockSkuStockRequest(
    @NotNull
    @Min(1)
    Integer quantity
) {
}
