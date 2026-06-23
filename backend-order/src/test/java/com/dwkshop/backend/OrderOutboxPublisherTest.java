package com.dwkshop.backend;

import com.dwkshop.backend.domain.entity.OrderOutboxEvent;
import com.dwkshop.backend.domain.repository.OrderOutboxEventRepository;
import com.dwkshop.backend.event.InventoryIntegrationEvent;
import com.dwkshop.backend.order.OrderOutboxPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderOutboxPublisherTest {

    @Test
    void rabbitMqTemporaryFailureKeepsOutboxPendingThenMarksSentAfterRecovery() throws Exception {
        OrderOutboxEvent outbox = pendingOutbox();
        OrderOutboxEventRepository repository = mock(OrderOutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OrderOutboxPublisher publisher = new OrderOutboxPublisher(repository, objectMapper, rabbitTemplate, "dwkshop.inventory.exchange");

        when(repository.findByPublishStatusAndNextRetryAtLessThanEqualOrderById(
            eq("PENDING"), any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(List.of(outbox));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker unavailable"));
            return null;
        }).doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
            eq("dwkshop.inventory.exchange"),
            eq("inventory.order-created"),
            ArgumentMatchers.any(InventoryIntegrationEvent.class),
            any(CorrelationData.class)
        );

        publisher.publishPending();

        assertThat(outbox.getPublishStatus()).isEqualTo("PENDING");
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextRetryAt()).isAfter(LocalDateTime.now());
        assertThat(outbox.getLastError()).contains("broker unavailable");
        assertThat(outbox.getPublishedAt()).isNull();

        outbox.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        publisher.publishPending();

        assertThat(outbox.getPublishStatus()).isEqualTo("SENT");
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getPublishedAt()).isNotNull();
        assertThat(outbox.getLastError()).isNull();
    }

    private OrderOutboxEvent pendingOutbox() throws Exception {
        LocalDateTime now = LocalDateTime.now().minusSeconds(1);
        InventoryIntegrationEvent event = new InventoryIntegrationEvent(
            "order-created-1",
            InventoryIntegrationEvent.ORDER_CREATED,
            1,
            100L,
            "SO202606230100",
            now,
            List.of(new InventoryIntegrationEvent.Item(501L, 1))
        );
        OrderOutboxEvent outbox = new OrderOutboxEvent();
        outbox.setEventId(event.eventId());
        outbox.setAggregateId(event.orderId());
        outbox.setEventType(event.eventType());
        outbox.setRoutingKey("inventory.order-created");
        outbox.setPayloadJson(new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(event));
        outbox.setPublishStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        return outbox;
    }
}
