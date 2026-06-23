package com.dwkshop.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import java.util.Map;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
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
    Queue orderCreatedQueue(@Value("${dwkshop.mq.order-created-queue}") String queueName,
        @Value("${dwkshop.mq.order-exchange}") String exchange) {
        return new Queue(queueName, true, false, false, Map.of(
            "x-dead-letter-exchange", exchange,
            "x-dead-letter-routing-key", "order.created.dead"));
    }

    @Bean
    Queue orderCreatedDeadQueue(@Value("${dwkshop.mq.order-created-queue}") String queueName) {
        return new Queue(queueName + ".dead", true);
    }

    @Bean
    Queue orderCreatedParkingLotQueue(@Value("${dwkshop.mq.order-created-queue}") String queueName) {
        return new Queue(queueName + ".parking-lot", true);
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
    Binding orderCreatedDeadBinding(DirectExchange orderExchange, Queue orderCreatedDeadQueue) {
        return BindingBuilder.bind(orderCreatedDeadQueue).to(orderExchange).with("order.created.dead");
    }

    @Bean
    Binding orderCreatedParkingLotBinding(DirectExchange orderExchange, Queue orderCreatedParkingLotQueue) {
        return BindingBuilder.bind(orderCreatedParkingLotQueue).to(orderExchange).with("order.created.parking-lot");
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
        @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup,
        @Value("${dwkshop.mq.listener.retry.max-attempts:4}") int maxAttempts,
        @Value("${dwkshop.mq.listener.retry.initial-interval-ms:500}") long initialInterval,
        @Value("${dwkshop.mq.listener.retry.multiplier:2.0}") double multiplier,
        @Value("${dwkshop.mq.listener.retry.max-interval-ms:5000}") long maxInterval
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
            .maxAttempts(maxAttempts)
            .backOffOptions(initialInterval, multiplier, maxInterval)
            .recoverer(new RejectAndDontRequeueRecoverer())
            .build());
        return factory;
    }
}
