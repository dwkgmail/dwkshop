package com.dwkshop.backend.order.dto;

public record UserOrderCountResponse(
    Long userId,
    long orderCount
) {
}
