package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.InventoryConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryConsumedEventRepository extends JpaRepository<InventoryConsumedEvent, Long> {
    boolean existsByEventIdAndSkuId(String eventId, Long skuId);
    boolean existsByOrderIdAndSkuIdAndEventType(Long orderId, Long skuId, String eventType);
}
