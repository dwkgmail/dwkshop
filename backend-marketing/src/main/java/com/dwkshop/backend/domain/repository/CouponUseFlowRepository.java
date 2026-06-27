package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.CouponUseFlow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUseFlowRepository extends JpaRepository<CouponUseFlow, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}
