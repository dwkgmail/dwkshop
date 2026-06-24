package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.TradeOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    List<TradeOrder> findAllByOrderByIdDesc();

    List<TradeOrder> findByUserIdOrderByIdDesc(Long userId);

    Optional<TradeOrder> findByIdAndUserId(Long id, Long userId);

    Optional<TradeOrder> findByUserIdAndClientRequestId(Long userId, String clientRequestId);
}
