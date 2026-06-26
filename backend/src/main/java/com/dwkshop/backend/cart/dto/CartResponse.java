package com.dwkshop.backend.cart.dto;

import java.util.List;

public record CartResponse(
    Long userId,
    Integer badgeCount,
    Integer estimatedAmount,
    String estimatedAmountText,
    Boolean checkoutAvailable,
    String checkoutMessage,
    Integer invalidItemCount,
    Integer selectedItemCount,
    List<CartItemResponse> items
) {
}
