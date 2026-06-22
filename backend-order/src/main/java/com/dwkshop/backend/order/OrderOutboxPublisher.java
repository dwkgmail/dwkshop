package com.dwkshop.backend.order;

import com.dwkshop.backend.domain.entity.OrderOutboxEvent;
import com.dwkshop.backend.domain.repository.OrderOutboxEventRepository;
import com.dwkshop.backend.event.InventoryIntegrationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
public class OrderOutboxPublisher {
    private final OrderOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public OrderOutboxPublisher(OrderOutboxEventRepository repository, ObjectMapper objectMapper,
        RabbitTemplate rabbitTemplate, @Value("${dwkshop.mq.inventory-exchange}") String exchange) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @Scheduled(fixedDelayString = "${dwkshop.outbox.publish-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        var events = repository.findByPublishStatusAndNextRetryAtLessThanEqualOrderById(
            "PENDING", LocalDateTime.now(), PageRequest.of(0, 50));
        for (OrderOutboxEvent outbox : events) {
            try {
                InventoryIntegrationEvent event = objectMapper.readValue(outbox.getPayloadJson(), InventoryIntegrationEvent.class);
                CorrelationData correlation = new CorrelationData(outbox.getEventId());
                rabbitTemplate.convertAndSend(exchange, outbox.getRoutingKey(), event, correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck()) throw new IllegalStateException("Broker rejected message: " + confirm.getReason());
                if (correlation.getReturned() != null) throw new IllegalStateException("Message was unroutable");
                outbox.setPublishStatus("SENT");
                outbox.setPublishedAt(LocalDateTime.now());
                outbox.setLastError(null);
            } catch (Exception ex) {
                int retries = outbox.getRetryCount() + 1;
                outbox.setRetryCount(retries);
                outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(retries, 8))));
                String message = ex.getMessage();
                outbox.setLastError(message == null ? null : message.substring(0, Math.min(255, message.length())));
            }
            outbox.setUpdatedAt(LocalDateTime.now());
            repository.save(outbox);
        }
    }
}
