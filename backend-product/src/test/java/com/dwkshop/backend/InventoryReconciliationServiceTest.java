package com.dwkshop.backend;

import com.dwkshop.backend.domain.entity.InventoryOrderItemState;
import com.dwkshop.backend.domain.entity.Product;
import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.repository.InventoryOrderItemStateRepository;
import com.dwkshop.backend.domain.repository.InventoryReconciliationRepairRecordRepository;
import com.dwkshop.backend.domain.repository.ProductRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.product.InventoryReconciliationService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InventoryReconciliationServiceTest {

    @Autowired InventoryReconciliationService service;
    @Autowired ProductRepository productRepository;
    @Autowired ProductSkuRepository skuRepository;
    @Autowired InventoryOrderItemStateRepository stateRepository;
    @Autowired InventoryReconciliationRepairRecordRepository repairRecordRepository;

    @BeforeEach
    void clean() {
        repairRecordRepository.deleteAllInBatch();
        stateRepository.deleteAllInBatch();
        skuRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Test
    void reportShowsLockedStockDifferenceAndRepairRecordsAudit() {
        ProductSku sku = sku(8, 5);
        state(sku.getId(), 1001L, 2, "LOCKED");
        state(sku.getId(), 1002L, 1, "RELEASED");

        var report = service.getReport(true);

        assertThat(report.items()).hasSize(1);
        var item = report.items().get(0);
        assertThat(item.currentStock()).isEqualTo(8);
        assertThat(item.projectedLockedStock()).isEqualTo(2);
        assertThat(item.actualLockedStock()).isEqualTo(5);
        assertThat(item.difference()).isEqualTo(3);
        assertThat(item.relatedOrders()).hasSize(2);

        var record = service.repairLockedStock(sku.getId(), "tester", "test repair");

        assertThat(record.beforeLockedStock()).isEqualTo(5);
        assertThat(record.projectedLockedStock()).isEqualTo(2);
        assertThat(skuRepository.findById(sku.getId()).orElseThrow().getLockedStock()).isEqualTo(2);
        assertThat(repairRecordRepository.findAll()).hasSize(1);
    }

    private ProductSku sku(int stock, int lockedStock) {
        LocalDateTime now = LocalDateTime.now();
        Product product = new Product();
        product.setCategoryId(1L);
        product.setProductCode("P-RECON");
        product.setName("Reconciliation Product");
        product.setSubtitle("test");
        product.setMainImageUrl("/images/test.png");
        product.setProductType("NORMAL");
        product.setSaleStatus("ON_SALE");
        product.setDeliveryType("NORMAL");
        product.setAllowCart(true);
        product.setAllowSingleBuy(true);
        product.setSupportPointDeduction(false);
        product.setSupportPointReward(false);
        product.setPointReward(0);
        product.setVirtualSales(0);
        product.setActualSales(0);
        product.setDeletedFlag(false);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        Product savedProduct = productRepository.save(product);

        ProductSku sku = new ProductSku();
        sku.setProductId(savedProduct.getId());
        sku.setSkuCode("SKU-RECON");
        sku.setSkuName("Recon SKU");
        sku.setSpecJson("{}");
        sku.setSalePrice(100);
        sku.setStock(stock);
        sku.setLockedStock(lockedStock);
        sku.setSkuStatus("ENABLED");
        sku.setCreatedAt(now);
        sku.setUpdatedAt(now);
        return skuRepository.save(sku);
    }

    private void state(Long skuId, Long orderId, int quantity, String state) {
        InventoryOrderItemState itemState = new InventoryOrderItemState();
        itemState.setSkuId(skuId);
        itemState.setOrderId(orderId);
        itemState.setQuantity(quantity);
        itemState.setState(state);
        itemState.setLastEventVersion(1);
        itemState.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(itemState);
    }
}
