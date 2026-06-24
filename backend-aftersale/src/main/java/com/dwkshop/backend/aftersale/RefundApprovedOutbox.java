package com.dwkshop.backend.aftersale;

import com.dwkshop.backend.domain.entity.AftersaleOrder;
import com.dwkshop.backend.domain.entity.AftersaleOrderItem;
import com.dwkshop.backend.domain.entity.AftersaleOutboxEvent;
import com.dwkshop.backend.domain.repository.AftersaleOutboxEventRepository;
import com.dwkshop.backend.event.RefundApprovedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RefundApprovedOutbox {
    static final String EVENT_TYPE = "REFUND_APPROVED";
    private final AftersaleOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public RefundApprovedOutbox(AftersaleOutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void append(AftersaleOrder aftersale, RefundOrderContext context, List<AftersaleOrderItem> refundItems, LocalDateTime approvedAt) {
        if (repository.existsByAggregateIdAndEventType(aftersale.getId(), EVENT_TYPE)) {
            return;
        }
        String eventId = UUID.randomUUID().toString();
        List<RefundApprovedEvent.RefundItem> items = refundItems.stream()
            .map(item -> new RefundApprovedEvent.RefundItem(item.getSkuId(), item.getQuantity(), item.getRefundAmount()))
            .toList();
        RefundApprovedEvent event = new RefundApprovedEvent(
            eventId, aftersale.getAftersaleNo() + "-RELEASE", aftersale.getId(), aftersale.getAftersaleNo(),
            aftersale.getOrderId(), context.orderStatus(), aftersale.getRefundScope(), aftersale.getRefundAmount(), approvedAt, items
        );
        AftersaleOutboxEvent outbox = new AftersaleOutboxEvent();
        outbox.setEventId(eventId);
        outbox.setAggregateId(aftersale.getId());
        outbox.setEventType(EVENT_TYPE);
        outbox.setPayloadJson(write(event));
        outbox.setPublishStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(approvedAt);
        outbox.setCreatedAt(approvedAt);
        outbox.setUpdatedAt(approvedAt);
        repository.save(outbox);
    }

    private String write(RefundApprovedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize refund approved event", ex);
        }
    }
}
