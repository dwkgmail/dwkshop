package com.dwkshop.backend.order;

import java.time.LocalDateTime;

public record OrderCreatedEvent(
    Long orderId,
    String orderNo,
    Long userId,
    Integer payAmount,
    LocalDateTime createdAt
) {
}
