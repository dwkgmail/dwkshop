package com.dwkshop.backend.marketing.dto;

import jakarta.validation.constraints.NotNull;

public record UseCouponRequest(
    @NotNull
    Long orderId
) {
}
