package com.dwkshop.backend.order;

import com.dwkshop.backend.event.RefundApprovedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RefundApprovedConsumer {
    private final OrderService orderService;

    public RefundApprovedConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = "${dwkshop.mq.refund-approved-order-queue}")
    public void consume(RefundApprovedEvent event) {
        orderService.completeAftersale(event);
    }
}
