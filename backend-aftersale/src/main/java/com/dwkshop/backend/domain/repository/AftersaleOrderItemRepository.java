package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.AftersaleOrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AftersaleOrderItemRepository extends JpaRepository<AftersaleOrderItem, Long> {

    List<AftersaleOrderItem> findByAftersaleIdOrderById(Long aftersaleId);
}
