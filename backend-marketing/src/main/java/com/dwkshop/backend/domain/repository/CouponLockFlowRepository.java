package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.CouponLockFlow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponLockFlowRepository extends JpaRepository<CouponLockFlow, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}
