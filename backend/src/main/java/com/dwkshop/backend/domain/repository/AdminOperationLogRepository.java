package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.AdminOperationLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminOperationLogRepository extends JpaRepository<AdminOperationLog, Long> {

    List<AdminOperationLog> findTop100ByOrderByIdDesc();
}
