package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.AftersaleOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface AftersaleOrderRepository extends JpaRepository<AftersaleOrder, Long> {

    List<AftersaleOrder> findByUserIdOrderByIdDesc(Long userId);

    List<AftersaleOrder> findAllByOrderByIdDesc();

    Optional<AftersaleOrder> findByIdAndUserId(Long id, Long userId);

    Optional<AftersaleOrder> findFirstByOrderIdAndAftersaleStatusIn(Long orderId, List<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from AftersaleOrder item where item.id = :id")
    Optional<AftersaleOrder> findByIdForUpdate(Long id);
}
