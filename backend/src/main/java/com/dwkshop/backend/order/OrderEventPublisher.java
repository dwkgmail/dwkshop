package com.dwkshop.backend.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!test")
class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String orderExchange;
    private final String orderCreatedRoutingKey;

    OrderEventPublisher(
        RabbitTemplate rabbitTemplate,
        @Value("${dwkshop.mq.order-exchange}") String orderExchange,
        @Value("${dwkshop.mq.order-created-routing-key}") String orderCreatedRoutingKey
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.orderExchange = orderExchange;
        this.orderCreatedRoutingKey = orderCreatedRoutingKey;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishOrderCreated(OrderCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(orderExchange, orderCreatedRoutingKey, event);
        } catch (AmqpException ex) {
            log.warn("Failed to publish order created event, orderId={}", event.orderId(), ex);
        }
    }
}
