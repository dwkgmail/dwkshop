package com.dwkshop.backend;

import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.repository.InventoryConsumedEventRepository;
import com.dwkshop.backend.domain.repository.InventoryOrderItemStateRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.event.InventoryIntegrationEvent;
import com.dwkshop.backend.product.InventoryIntegrationEventConsumer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InventoryIntegrationEventConsumerTest {
    @Autowired InventoryIntegrationEventConsumer consumer;
    @Autowired ProductSkuRepository skuRepository;
    @Autowired InventoryConsumedEventRepository consumedRepository;
    @Autowired InventoryOrderItemStateRepository stateRepository;

    @BeforeEach
    void clean() {
        consumedRepository.deleteAllInBatch();
        stateRepository.deleteAllInBatch();
        skuRepository.deleteAllInBatch();
    }

    @Test
    void duplicateEventAndDuplicateBusinessKeyOnlyLockOnce() {
        ProductSku sku = sku(10, 0);
        var created = event("e-1", InventoryIntegrationEvent.ORDER_CREATED, 1, 101L, sku.getId(), 2);
        consumer.consume(created);
        consumer.consume(created);
        consumer.consume(event("e-2", InventoryIntegrationEvent.ORDER_CREATED, 1, 101L, sku.getId(), 2));

        ProductSku result = skuRepository.findById(sku.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(8);
        assertThat(result.getLockedStock()).isEqualTo(2);
        assertThat(consumedRepository.count()).isEqualTo(1);
    }

    @Test
    void cancellationBeforeCreationCreatesTombstoneAndLateCreationIsIgnored() {
        ProductSku sku = sku(10, 0);
        consumer.consume(event("cancel-first", InventoryIntegrationEvent.ORDER_CANCELLED, 2, 102L, sku.getId(), 3));
        consumer.consume(event("create-late", InventoryIntegrationEvent.ORDER_CREATED, 1, 102L, sku.getId(), 3));

        ProductSku result = skuRepository.findById(sku.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(10);
        assertThat(result.getLockedStock()).isZero();
        assertThat(stateRepository.findByOrderIdAndSkuId(102L, sku.getId()).orElseThrow().getState())
            .isEqualTo("RELEASED");
    }

    @Test
    void failedDeliveryRollsBackAndCanBeRetriedAfterCauseIsFixed() {
        ProductSku sku = sku(1, 0);
        var created = event("retry-event", InventoryIntegrationEvent.ORDER_CREATED, 1, 103L, sku.getId(), 2);
        assertThatThrownBy(() -> consumer.consume(created)).isInstanceOf(IllegalStateException.class);
        assertThat(consumedRepository.count()).isZero();
        assertThat(stateRepository.count()).isZero();

        ProductSku replenished = skuRepository.findById(sku.getId()).orElseThrow();
        replenished.setStock(5);
        skuRepository.save(replenished);
        consumer.consume(created);

        ProductSku result = skuRepository.findById(sku.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(3);
        assertThat(result.getLockedStock()).isEqualTo(2);
        assertThat(consumedRepository.count()).isEqualTo(1);
    }

    private InventoryIntegrationEvent event(String id, String type, int version, Long orderId, Long skuId, int quantity) {
        return new InventoryIntegrationEvent(id, type, version, orderId, "SO-" + orderId,
            LocalDateTime.now(), List.of(new InventoryIntegrationEvent.Item(skuId, quantity)));
    }

    private ProductSku sku(int stock, int locked) {
        ProductSku sku = new ProductSku();
        sku.setProductId(1L);
        sku.setSkuCode("SKU-" + UUID.randomUUID());
        sku.setSkuName("test");
        sku.setSpecJson("{}");
        sku.setSalePrice(100);
        sku.setStock(stock);
        sku.setLockedStock(locked);
        sku.setSkuStatus("ENABLED");
        sku.setCreatedAt(LocalDateTime.now());
        sku.setUpdatedAt(LocalDateTime.now());
        return skuRepository.save(sku);
    }
}
