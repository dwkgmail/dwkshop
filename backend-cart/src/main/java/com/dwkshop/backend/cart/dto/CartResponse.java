package com.dwkshop.backend.cart.dto;

import java.util.List;

public record CartResponse(
    Long userId,
    Integer badgeCount,
    Integer estimatedAmount,
    String estimatedAmountText,
    List<CartItemResponse> items
) {
}
