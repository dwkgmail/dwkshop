package com.dwkshop.backend.order;

import com.dwkshop.backend.order.dto.ConfirmOrderRequest;

public record SettlementSession(
    Long userId,
    ConfirmOrderRequest request,
    Integer expectedPayAmount,
    SettlementSnapshot snapshot
) {
    public SettlementSession(Long userId, ConfirmOrderRequest request, Integer expectedPayAmount) {
        this(userId, request, expectedPayAmount, null);
    }

    public boolean used() {
        return false;
    }
}
