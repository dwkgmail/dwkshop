package com.dwkshop.backend.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductSkuRequest(
    Long id,
    String skuCode,
    @NotBlank String skuName,
    @NotBlank String specJson,
    String imageUrl,
    @NotNull @Min(0) Integer salePrice,
    @Min(0) Integer linePrice,
    @NotNull @Min(0) Integer stock,
    String skuStatus
) {
}
