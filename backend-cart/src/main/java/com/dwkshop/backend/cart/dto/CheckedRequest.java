package com.dwkshop.backend.cart.dto;

import jakarta.validation.constraints.NotNull;

public record CheckedRequest(
    @NotNull Boolean checked
) {
}
