package com.dwkshop.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import java.util.Map;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    DirectExchange orderExchange(@Value("${dwkshop.mq.order-exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    Queue orderCreatedQueue(@Value("${dwkshop.mq.order-created-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    Binding orderCreatedBinding(
        DirectExchange orderExchange,
        Queue orderCreatedQueue,
        @Value("${dwkshop.mq.order-created-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(orderCreatedQueue).to(orderExchange).with(routingKey);
    }

    @Bean
    DirectExchange refundExchange(@Value("${dwkshop.mq.refund-exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    Queue refundApprovedOrderQueue(@Value("${dwkshop.mq.refund-approved-order-queue}") String name,
        @Value("${dwkshop.mq.refund-exchange}") String exchange) {
        return new Queue(name, true, false, false, Map.of(
            "x-dead-letter-exchange", exchange,
            "x-dead-letter-routing-key", "refund.approved.order.dead"));
    }

    @Bean
    Queue refundApprovedOrderDeadQueue(@Value("${dwkshop.mq.refund-approved-order-queue}") String name) {
        return new Queue(name + ".dead", true);
    }

    @Bean
    Binding refundApprovedOrderBinding(DirectExchange refundExchange, Queue refundApprovedOrderQueue,
        @Value("${dwkshop.mq.refund-approved-routing-key}") String routingKey) {
        return BindingBuilder.bind(refundApprovedOrderQueue).to(refundExchange).with(routingKey);
    }

    @Bean
    Binding refundApprovedOrderDeadBinding(DirectExchange refundExchange, Queue refundApprovedOrderDeadQueue) {
        return BindingBuilder.bind(refundApprovedOrderDeadQueue).to(refundExchange).with("refund.approved.order.dead");
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        Jackson2JsonMessageConverter messageConverter,
        @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
