package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.AftersaleOutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AftersaleOutboxEventRepository extends JpaRepository<AftersaleOutboxEvent, Long> {
    List<AftersaleOutboxEvent> findByPublishStatusAndNextRetryAtLessThanEqualOrderById(String status, LocalDateTime now, Pageable pageable);
    boolean existsByAggregateIdAndEventType(Long aggregateId, String eventType);
    Optional<AftersaleOutboxEvent> findByAggregateIdAndEventType(Long aggregateId, String eventType);
}
