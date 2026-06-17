package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.AftersaleOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AftersaleOrderRepository extends JpaRepository<AftersaleOrder, Long> {

    List<AftersaleOrder> findByUserIdOrderByIdDesc(Long userId);

    List<AftersaleOrder> findAllByOrderByIdDesc();

    Optional<AftersaleOrder> findByIdAndUserId(Long id, Long userId);

    Optional<AftersaleOrder> findFirstByOrderIdAndAftersaleStatusIn(Long orderId, List<String> statuses);
}
