package com.dwkshop.backend.aftersale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateAftersaleItemRequest(
    @NotNull Long skuId,
    @NotNull @Min(1) Integer quantity
) {
}
