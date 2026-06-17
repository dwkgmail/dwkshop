package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.TradeOrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderItemRepository extends JpaRepository<TradeOrderItem, Long> {

    List<TradeOrderItem> findByOrderId(Long orderId);
}
