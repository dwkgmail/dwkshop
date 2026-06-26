package com.dwkshop.backend.order.dto;

public record PaymentCallbackResponse(
    boolean success,
    boolean duplicate,
    String message
) {
    public static PaymentCallbackResponse processed() {
        return new PaymentCallbackResponse(true, false, "SUCCESS");
    }

    public static PaymentCallbackResponse duplicated() {
        return new PaymentCallbackResponse(true, true, "SUCCESS");
    }
}
