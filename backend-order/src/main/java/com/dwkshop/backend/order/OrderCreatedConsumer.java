package com.dwkshop.backend.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    @RabbitListener(queues = "${dwkshop.mq.order-created-queue}")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info(
            "Order created event consumed, orderId={}, orderNo={}, userId={}, payAmount={}",
            event.orderId(),
            event.orderNo(),
            event.userId(),
            event.payAmount()
        );
    }
}
