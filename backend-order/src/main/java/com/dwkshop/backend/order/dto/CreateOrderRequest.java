package com.dwkshop.backend.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
    @NotBlank String settlementToken,
    @NotNull Integer expectedPayAmount,
    String remark,
    String clientRequestId
) {
    public CreateOrderRequest withClientRequestId(String fallbackClientRequestId) {
        if (clientRequestId != null && !clientRequestId.isBlank()) {
            return this;
        }
        return new CreateOrderRequest(settlementToken, expectedPayAmount, remark, fallbackClientRequestId);
    }
}
