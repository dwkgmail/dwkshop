package com.dwkshop.backend.config;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RefundMqConfig {
    @Bean
    DirectExchange inventoryExchange(@Value("${dwkshop.mq.inventory-exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    Queue inventoryProductQueue(@Value("${dwkshop.mq.inventory-product-queue}") String name,
        @Value("${dwkshop.mq.inventory-exchange}") String exchange) {
        return new Queue(name, true, false, false, Map.of(
            "x-dead-letter-exchange", exchange, "x-dead-letter-routing-key", "inventory.product.dead"));
    }

    @Bean
    Queue inventoryProductDeadQueue(@Value("${dwkshop.mq.inventory-product-queue}") String name) {
        return new Queue(name + ".dead", true);
    }

    @Bean
    Binding inventoryCreatedBinding(DirectExchange inventoryExchange, Queue inventoryProductQueue) {
        return BindingBuilder.bind(inventoryProductQueue).to(inventoryExchange).with("inventory.order-created");
    }

    @Bean
    Binding inventoryCancelledBinding(DirectExchange inventoryExchange, Queue inventoryProductQueue) {
        return BindingBuilder.bind(inventoryProductQueue).to(inventoryExchange).with("inventory.order-cancelled");
    }

    @Bean
    Binding inventoryDeadBinding(DirectExchange inventoryExchange, Queue inventoryProductDeadQueue) {
        return BindingBuilder.bind(inventoryProductDeadQueue).to(inventoryExchange).with("inventory.product.dead");
    }

    @Bean
    DirectExchange refundExchange(@Value("${dwkshop.mq.refund-exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    Queue refundApprovedProductQueue(@Value("${dwkshop.mq.refund-approved-product-queue}") String name,
        @Value("${dwkshop.mq.refund-exchange}") String exchange) {
        return new Queue(name, true, false, false, Map.of(
            "x-dead-letter-exchange", exchange,
            "x-dead-letter-routing-key", "refund.approved.product.dead"));
    }

    @Bean
    Queue refundApprovedProductDeadQueue(@Value("${dwkshop.mq.refund-approved-product-queue}") String name) {
        return new Queue(name + ".dead", true);
    }

    @Bean
    Binding refundApprovedProductBinding(DirectExchange refundExchange, Queue refundApprovedProductQueue,
        @Value("${dwkshop.mq.refund-approved-routing-key}") String routingKey) {
        return BindingBuilder.bind(refundApprovedProductQueue).to(refundExchange).with(routingKey);
    }

    @Bean
    Binding refundApprovedProductDeadBinding(DirectExchange refundExchange, Queue refundApprovedProductDeadQueue) {
        return BindingBuilder.bind(refundApprovedProductDeadQueue).to(refundExchange).with("refund.approved.product.dead");
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() { return new Jackson2JsonMessageConverter(); }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
        Jackson2JsonMessageConverter converter,
        @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
            .maxAttempts(4).backOffOptions(500, 2.0, 5000)
            .recoverer(new RejectAndDontRequeueRecoverer()).build());
        return factory;
    }
}
