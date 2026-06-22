package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.InventoryOrderItemState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryOrderItemStateRepository extends JpaRepository<InventoryOrderItemState, Long> {
    Optional<InventoryOrderItemState> findByOrderIdAndSkuId(Long orderId, Long skuId);
}
