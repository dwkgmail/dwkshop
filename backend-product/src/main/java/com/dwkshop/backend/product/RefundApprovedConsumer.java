package com.dwkshop.backend.product;

import com.dwkshop.backend.event.RefundApprovedEvent;
import com.dwkshop.backend.event.InventoryIntegrationEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RefundApprovedConsumer {
    private final InventoryIntegrationEventConsumer inventoryConsumer;

    public RefundApprovedConsumer(InventoryIntegrationEventConsumer inventoryConsumer) {
        this.inventoryConsumer = inventoryConsumer;
    }

    @RabbitListener(queues = "${dwkshop.mq.refund-approved-product-queue}")
    public void consume(RefundApprovedEvent event) {
        if (event.items() == null || event.items().isEmpty()) {
            return;
        }
        inventoryConsumer.consume(new InventoryIntegrationEvent(
            event.eventId(), InventoryIntegrationEvent.REFUND_APPROVED, 3, event.orderId(),
            event.aftersaleNo(), event.approvedAt(), event.items().stream()
                .map(item -> new InventoryIntegrationEvent.Item(item.skuId(), item.quantity())).toList()));
    }
}
