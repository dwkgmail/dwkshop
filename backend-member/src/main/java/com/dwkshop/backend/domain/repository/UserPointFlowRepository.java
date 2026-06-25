package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.UserPointFlow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPointFlowRepository extends JpaRepository<UserPointFlow, Long> {

    boolean existsByFlowNo(String flowNo);
}
