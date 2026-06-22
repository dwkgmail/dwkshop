package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.OrderOutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderOutboxEventRepository extends JpaRepository<OrderOutboxEvent, Long> {
    boolean existsByAggregateIdAndEventType(Long aggregateId, String eventType);
    List<OrderOutboxEvent> findByPublishStatusAndNextRetryAtLessThanEqualOrderById(
        String status, LocalDateTime nextRetryAt, Pageable pageable);
}
