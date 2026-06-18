package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.AftersaleRefundFlow;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AftersaleRefundFlowRepository extends JpaRepository<AftersaleRefundFlow, Long> {

    Optional<AftersaleRefundFlow> findByAftersaleId(Long aftersaleId);

    Optional<AftersaleRefundFlow> findByCommandNo(String commandNo);
}
