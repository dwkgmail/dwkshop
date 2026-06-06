package com.dwkshop.backend.order;

import com.dwkshop.backend.order.dto.ConfirmOrderRequest;

public record SettlementSession(
    Long userId,
    ConfirmOrderRequest request,
    Integer expectedPayAmount
) {
    public boolean used() {
        return false;
    }
}
