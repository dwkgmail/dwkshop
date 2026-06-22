package com.dwkshop.backend.order;

import com.dwkshop.backend.domain.entity.OrderOutboxEvent;
import com.dwkshop.backend.domain.entity.TradeOrder;
import com.dwkshop.backend.domain.entity.TradeOrderItem;
import com.dwkshop.backend.domain.repository.OrderOutboxEventRepository;
import com.dwkshop.backend.event.InventoryIntegrationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderInventoryOutbox {
    private final OrderOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OrderInventoryOutbox(OrderOutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void append(TradeOrder order, List<TradeOrderItem> items, String eventType, int version, LocalDateTime occurredAt) {
        if (repository.existsByAggregateIdAndEventType(order.getId(), eventType)) return;
        String eventId = UUID.randomUUID().toString();
        InventoryIntegrationEvent event = new InventoryIntegrationEvent(eventId, eventType, version,
            order.getId(), order.getOrderNo(), occurredAt,
            items.stream().map(i -> new InventoryIntegrationEvent.Item(i.getSkuId(), i.getQuantity())).toList());
        OrderOutboxEvent outbox = new OrderOutboxEvent();
        outbox.setEventId(eventId);
        outbox.setAggregateId(order.getId());
        outbox.setEventType(eventType);
        outbox.setRoutingKey(switch (eventType) {
            case InventoryIntegrationEvent.ORDER_CREATED -> "inventory.order-created";
            case InventoryIntegrationEvent.ORDER_CANCELLED -> "inventory.order-cancelled";
            default -> throw new IllegalArgumentException("Unsupported order inventory event " + eventType);
        });
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize inventory event", ex);
        }
        outbox.setPublishStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(occurredAt);
        outbox.setCreatedAt(occurredAt);
        outbox.setUpdatedAt(occurredAt);
        repository.save(outbox);
    }
}
