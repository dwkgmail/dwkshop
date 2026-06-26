package com.dwkshop.backend.order;

public record SettlementPointSnapshot(
    boolean visible,
    int availablePoints,
    int deductionAmount,
    boolean selected
) {
}
