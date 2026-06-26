package com.dwkshop.backend.event;

import java.time.LocalDateTime;
import java.util.List;

/** Versioned integration contract for every stock mutation initiated by an order lifecycle event. */
public record InventoryIntegrationEvent(
    String eventId,
    String eventType,
    int eventVersion,
    Long orderId,
    String orderNo,
    LocalDateTime occurredAt,
    List<Item> items
) {
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String REFUND_APPROVED = "REFUND_APPROVED";

    public record Item(Long skuId, Integer quantity) {}
}
