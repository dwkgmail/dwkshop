package com.dwkshop.backend.aftersale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAftersaleRequest(
    @NotNull Long orderId,
    @NotBlank String reason
) {
}
