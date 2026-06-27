package com.dwkshop.backend.product;

import com.dwkshop.backend.auth.RequiresConfirmation;
import com.dwkshop.backend.auth.RequiresPermission;
import com.dwkshop.backend.product.dto.InventoryReconciliationResponse;
import com.dwkshop.backend.product.dto.InventoryRepairRecordResponse;
import com.dwkshop.backend.product.dto.InventoryRepairRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/inventory-reconciliation")
public class AdminInventoryReconciliationController {

    private final InventoryReconciliationService inventoryReconciliationService;

    public AdminInventoryReconciliationController(InventoryReconciliationService inventoryReconciliationService) {
        this.inventoryReconciliationService = inventoryReconciliationService;
    }

    @GetMapping
    @RequiresPermission("inventory:read")
    public InventoryReconciliationResponse getReport(@RequestParam(defaultValue = "false") boolean onlyDiff) {
        return inventoryReconciliationService.getReport(onlyDiff);
    }

    @PostMapping("/skus/{skuId}/repair")
    @RequiresPermission("inventory:repair")
    @RequiresConfirmation
    public InventoryRepairRecordResponse repair(@PathVariable Long skuId, @RequestBody(required = false) InventoryRepairRequest request) {
        return inventoryReconciliationService.repairLockedStock(
            skuId,
            request == null ? null : request.operator(),
            request == null ? null : request.reason()
        );
    }
}
