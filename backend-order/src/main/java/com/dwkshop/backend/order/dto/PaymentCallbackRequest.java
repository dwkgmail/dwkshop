package com.dwkshop.backend.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record PaymentCallbackRequest(
    String paymentNo,
    Long orderId,
    String channel,
    @NotBlank String channelTradeNo,
    @NotNull Integer amount,
    LocalDateTime paidAt,
    String callbackPayload
) {
}
