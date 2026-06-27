package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.PointFreeze;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointFreezeRepository extends JpaRepository<PointFreeze, Long> {

    Optional<PointFreeze> findByBizNo(String bizNo);
}
