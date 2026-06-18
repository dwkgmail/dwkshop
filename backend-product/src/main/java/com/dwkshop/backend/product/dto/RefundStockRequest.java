package com.dwkshop.backend.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RefundStockRequest(
    @NotBlank String commandNo,
    @NotBlank String commandType,
    @NotEmpty @Valid List<RefundStockItemRequest> items
) {
}
