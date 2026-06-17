package com.dwkshop.backend.order.dto;

public record PointDeductionResponse(
    Boolean visible,
    Integer availablePoints,
    Integer deductionAmount,
    String deductionAmountText,
    Boolean selected
) {
}
