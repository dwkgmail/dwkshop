package com.dwkshop.backend.product;

import com.dwkshop.backend.domain.entity.InventoryConsumedEvent;
import com.dwkshop.backend.domain.entity.InventoryOrderItemState;
import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.repository.InventoryConsumedEventRepository;
import com.dwkshop.backend.domain.repository.InventoryOrderItemStateRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.event.InventoryIntegrationEvent;
import java.time.LocalDateTime;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryIntegrationEventConsumer {
    private final ProductSkuRepository skuRepository;
    private final InventoryConsumedEventRepository consumedRepository;
    private final InventoryOrderItemStateRepository stateRepository;

    public InventoryIntegrationEventConsumer(ProductSkuRepository skuRepository,
        InventoryConsumedEventRepository consumedRepository, InventoryOrderItemStateRepository stateRepository) {
        this.skuRepository = skuRepository;
        this.consumedRepository = consumedRepository;
        this.stateRepository = stateRepository;
    }

    @RabbitListener(queues = "${dwkshop.mq.inventory-product-queue}")
    @Transactional
    public void consume(InventoryIntegrationEvent event) {
        if (event.eventId() == null || event.orderId() == null || event.items() == null) {
            throw new IllegalArgumentException("Invalid inventory integration event");
        }
        for (InventoryIntegrationEvent.Item item : event.items()) consumeItem(event, item);
    }

    private void consumeItem(InventoryIntegrationEvent event, InventoryIntegrationEvent.Item item) {
        if (item.skuId() == null || item.quantity() == null || item.quantity() <= 0) {
            throw new IllegalArgumentException("Invalid inventory event item");
        }
        if (consumedRepository.existsByEventIdAndSkuId(event.eventId(), item.skuId())
            || consumedRepository.existsByOrderIdAndSkuIdAndEventType(event.orderId(), item.skuId(), event.eventType())) return;

        InventoryOrderItemState state = stateRepository.findByOrderIdAndSkuId(event.orderId(), item.skuId()).orElse(null);
        boolean stale = state != null && event.eventVersion() < state.getLastEventVersion();
        if (!stale) {
            if (InventoryIntegrationEvent.ORDER_CREATED.equals(event.eventType())) lock(state, event, item);
            else if (InventoryIntegrationEvent.PAYMENT_SUCCEEDED.equals(event.eventType())) markPaid(state, event, item);
            else if (InventoryIntegrationEvent.ORDER_CANCELLED.equals(event.eventType())
                || InventoryIntegrationEvent.REFUND_APPROVED.equals(event.eventType())) release(state, event, item);
            else throw new IllegalArgumentException("Unsupported inventory event type " + event.eventType());
        }
        markConsumed(event, item);
    }

    private void lock(InventoryOrderItemState state, InventoryIntegrationEvent event, InventoryIntegrationEvent.Item item) {
        if (state != null && ("LOCKED".equals(state.getState()) || "RELEASED".equals(state.getState()))) return;
        ProductSku sku = loadSku(item.skuId());
        if (sku.getStock() < item.quantity()) throw new IllegalStateException("Insufficient stock for sku " + item.skuId());
        sku.setStock(sku.getStock() - item.quantity());
        sku.setLockedStock(sku.getLockedStock() + item.quantity());
        sku.setUpdatedAt(LocalDateTime.now());
        skuRepository.save(sku);
        saveState(state, event, item, "LOCKED");
    }

    private void markPaid(InventoryOrderItemState state, InventoryIntegrationEvent event, InventoryIntegrationEvent.Item item) {
        if (state == null || "RELEASED".equals(state.getState())) {
            saveState(state, event, item, "RELEASED");
            return;
        }
        if ("LOCKED".equals(state.getState()) || "PAID".equals(state.getState())) {
            saveState(state, event, item, "PAID");
            return;
        }
        throw new IllegalStateException("Order item is not payable for sku " + item.skuId());
    }

    private void release(InventoryOrderItemState state, InventoryIntegrationEvent event, InventoryIntegrationEvent.Item item) {
        if (state != null && "RELEASED".equals(state.getState())) return;
        if ((state != null && ("LOCKED".equals(state.getState()) || "PAID".equals(state.getState())))
            || (state == null && InventoryIntegrationEvent.REFUND_APPROVED.equals(event.eventType()))) {
            ProductSku sku = loadSku(item.skuId());
            if (sku.getLockedStock() < item.quantity()) throw new IllegalStateException("Insufficient locked stock for sku " + item.skuId());
            sku.setLockedStock(sku.getLockedStock() - item.quantity());
            sku.setStock(sku.getStock() + item.quantity());
            sku.setUpdatedAt(LocalDateTime.now());
            skuRepository.save(sku);
        }
        // A release arriving first is a terminal tombstone; a late create event must not lock stock.
        saveState(state, event, item, "RELEASED");
    }

    private ProductSku loadSku(Long skuId) {
        return skuRepository.findByIdForUpdate(skuId).orElseThrow(() -> new IllegalStateException("Unknown sku " + skuId));
    }

    private void saveState(InventoryOrderItemState state, InventoryIntegrationEvent event,
        InventoryIntegrationEvent.Item item, String value) {
        InventoryOrderItemState target = state == null ? new InventoryOrderItemState() : state;
        target.setOrderId(event.orderId());
        target.setSkuId(item.skuId());
        target.setQuantity(item.quantity());
        target.setState(value);
        target.setLastEventVersion(event.eventVersion());
        target.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(target);
    }

    private void markConsumed(InventoryIntegrationEvent event, InventoryIntegrationEvent.Item item) {
        InventoryConsumedEvent consumed = new InventoryConsumedEvent();
        consumed.setEventId(event.eventId());
        consumed.setOrderId(event.orderId());
        consumed.setSkuId(item.skuId());
        consumed.setEventType(event.eventType());
        consumed.setConsumedAt(LocalDateTime.now());
        consumedRepository.save(consumed);
    }
}
