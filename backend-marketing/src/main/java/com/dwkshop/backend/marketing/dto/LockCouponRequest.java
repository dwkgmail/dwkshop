package com.dwkshop.backend.marketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record LockCouponRequest(
    @NotBlank
    String lockKey,
    @NotNull
    @PositiveOrZero
    Integer productAmount
) {
}
