package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.TradeOrderAmount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderAmountRepository extends JpaRepository<TradeOrderAmount, Long> {

    Optional<TradeOrderAmount> findByOrderId(Long orderId);
}
