package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.InventoryReconciliationRepairRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReconciliationRepairRecordRepository extends JpaRepository<InventoryReconciliationRepairRecord, Long> {
    List<InventoryReconciliationRepairRecord> findTop20BySkuIdOrderByCreatedAtDesc(Long skuId);
}
