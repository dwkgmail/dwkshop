package com.dwkshop.backend.aftersale;

import com.dwkshop.backend.domain.entity.AftersaleOutboxEvent;
import com.dwkshop.backend.domain.repository.AftersaleOutboxEventRepository;
import com.dwkshop.backend.event.RefundApprovedEvent;
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
public class AftersaleOutboxPublisher {
    private final AftersaleOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public AftersaleOutboxPublisher(AftersaleOutboxEventRepository repository, ObjectMapper objectMapper,
        RabbitTemplate rabbitTemplate, @Value("${dwkshop.mq.refund-exchange}") String exchange,
        @Value("${dwkshop.mq.refund-approved-routing-key}") String routingKey) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Scheduled(fixedDelayString = "${dwkshop.outbox.publish-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        var events = repository.findByPublishStatusAndNextRetryAtLessThanEqualOrderById(
            "PENDING", LocalDateTime.now(), PageRequest.of(0, 50));
        for (AftersaleOutboxEvent outbox : events) {
            try {
                RefundApprovedEvent event = objectMapper.readValue(outbox.getPayloadJson(), RefundApprovedEvent.class);
                CorrelationData correlation = new CorrelationData(outbox.getEventId());
                rabbitTemplate.convertAndSend(exchange, routingKey, event, correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException("Broker rejected message: " + confirm.getReason());
                }
                if (correlation.getReturned() != null) {
                    throw new IllegalStateException("Message was unroutable: " + correlation.getReturned().getReplyText());
                }
                outbox.setPublishStatus("SENT");
                outbox.setPublishedAt(LocalDateTime.now());
                outbox.setLastError(null);
            } catch (Exception ex) {
                int retries = outbox.getRetryCount() + 1;
                outbox.setRetryCount(retries);
                outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(retries, 8))));
                outbox.setLastError(trim(ex.getMessage()));
            }
            outbox.setUpdatedAt(LocalDateTime.now());
            repository.save(outbox);
        }
    }

    private String trim(String message) {
        if (message == null) return null;
        return message.length() <= 255 ? message : message.substring(0, 255);
    }
}
