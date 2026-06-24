package com.dwkshop.backend.event;

import java.time.LocalDateTime;
import java.util.List;

public record RefundApprovedEvent(
    String eventId,
    String commandNo,
    Long aftersaleId,
    String aftersaleNo,
    Long orderId,
    String orderStatus,
    String refundScope,
    Integer refundAmount,
    LocalDateTime approvedAt,
    List<RefundItem> items
) {
    public record RefundItem(Long skuId, Integer quantity, Integer refundAmount) {
    }
}
