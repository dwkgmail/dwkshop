package com.dwkshop.backend.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductUpsertRequest(
    @NotNull Long categoryId,
    String productCode,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 120) String brandName,
    @Size(max = 255) String subtitle,
    @NotBlank String mainImageUrl,
    String productType,
    String saleStatus,
    String deliveryType,
    Boolean allowCart,
    Boolean allowSingleBuy,
    Boolean pointDeductEnabled,
    Boolean supportRefund,
    Boolean pointRewardEnabled,
    @Min(0) Integer pointReward,
    @Min(0) Integer virtualSales,
    String noticeTitle,
    String noticeContent,
    @NotEmpty List<@Valid ProductSkuRequest> skus
) {
}
