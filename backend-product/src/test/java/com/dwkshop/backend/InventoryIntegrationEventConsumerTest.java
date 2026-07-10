package com.dwkshop.backend;

import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.repository.InventoryConsumedEventRepository;
import com.dwkshop.backend.domain.repository.InventoryOrderItemStateRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.event.InventoryIntegrationEvent;
import com.dwkshop.backend.product.InventoryIntegrationEventConsumer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    @Test
    void concurrentOrderCreatedEventsNeverOversellStock() throws Exception {
        ProductSku sku = sku(5, 0);
        CountDownLatch start = new CountDownLatch(1);
        var results = java.util.Collections.synchronizedList(new ArrayList<Boolean>());
        var executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    start.await();
                    consumer.consume(event("concurrent-" + index, InventoryIntegrationEvent.ORDER_CREATED, 1, 200L + index, sku.getId(), 3));
                    results.add(true);
                } catch (Exception ex) {
                    results.add(false);
                }
            });
        }

        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        ProductSku result = skuRepository.findById(sku.getId()).orElseThrow();
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(result.getStock()).isEqualTo(2);
        assertThat(result.getLockedStock()).isEqualTo(3);
        assertThat(consumedRepository.count()).isEqualTo(1);
    }

    @Test
    void duplicateRefundApprovedMessageOnlyRestoresStockOnce() {
        ProductSku sku = sku(2, 3);
        var refundApproved = event("refund-approved-1", InventoryIntegrationEvent.REFUND_APPROVED, 3, 301L, sku.getId(), 2);

        consumer.consume(refundApproved);
        consumer.consume(refundApproved);
        consumer.consume(event("refund-approved-2", InventoryIntegrationEvent.REFUND_APPROVED, 3, 301L, sku.getId(), 2));

        ProductSku result = skuRepository.findById(sku.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(4);
        assertThat(result.getLockedStock()).isEqualTo(1);
        assertThat(consumedRepository.count()).isEqualTo(1);
        assertThat(stateRepository.findByOrderIdAndSkuId(301L, sku.getId()).orElseThrow().getState())
            .isEqualTo("RELEASED");
    }

    @Test
    void paymentSucceededMarksLockedStockAsPaidWithoutChangingQuantity() {
        ProductSku sku = sku(10, 0);
        consumer.consume(event("create-before-pay", InventoryIntegrationEvent.ORDER_CREATED, 1, 401L, sku.getId(), 2));
        consumer.consume(event("pay-success", InventoryIntegrationEvent.PAYMENT_SUCCEEDED, 2, 401L, sku.getId(), 2));
        consumer.consume(event("refund-after-pay", InventoryIntegrationEvent.REFUND_APPROVED, 3, 401L, sku.getId(), 2));

        ProductSku result = skuRepository.findById(sku.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(10);
        assertThat(result.getLockedStock()).isZero();
        assertThat(stateRepository.findByOrderIdAndSkuId(401L, sku.getId()).orElseThrow().getState())
            .isEqualTo("RELEASED");
    }

    @Test
    void paymentBeforeCreationLocksStockWhenTheLateCreationEventArrives() {
        ProductSku sku = sku(10, 0);

        consumer.consume(event("pay-first", InventoryIntegrationEvent.PAYMENT_SUCCEEDED, 2, 402L, sku.getId(), 2));

        ProductSku beforeLock = skuRepository.findById(sku.getId()).orElseThrow();
        assertThat(beforeLock.getStock()).isEqualTo(10);
        assertThat(beforeLock.getLockedStock()).isZero();
        assertThat(stateRepository.findByOrderIdAndSkuId(402L, sku.getId()).orElseThrow().getState())
            .isEqualTo("PAYMENT_PENDING");

        consumer.consume(event("create-late", InventoryIntegrationEvent.ORDER_CREATED, 1, 402L, sku.getId(), 2));

        ProductSku result = skuRepository.findById(sku.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(8);
        assertThat(result.getLockedStock()).isEqualTo(2);
        assertThat(stateRepository.findByOrderIdAndSkuId(402L, sku.getId()).orElseThrow().getState())
            .isEqualTo("PAID");
    }

    @Test
    void lateCreationIsRetriedWhenPaymentPendingStockIsInsufficient() {
        ProductSku sku = sku(1, 0);
        consumer.consume(event("pay-first-retry", InventoryIntegrationEvent.PAYMENT_SUCCEEDED, 2, 403L, sku.getId(), 2));

        assertThatThrownBy(() -> consumer.consume(event("create-late-retry", InventoryIntegrationEvent.ORDER_CREATED, 1, 403L, sku.getId(), 2)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(consumedRepository.count()).isEqualTo(1);
        assertThat(stateRepository.findByOrderIdAndSkuId(403L, sku.getId()).orElseThrow().getState())
            .isEqualTo("PAYMENT_PENDING");

        ProductSku replenished = skuRepository.findById(sku.getId()).orElseThrow();
        replenished.setStock(5);
        skuRepository.save(replenished);
        consumer.consume(event("create-late-retry", InventoryIntegrationEvent.ORDER_CREATED, 1, 403L, sku.getId(), 2));

        ProductSku result = skuRepository.findById(sku.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(3);
        assertThat(result.getLockedStock()).isEqualTo(2);
        assertThat(stateRepository.findByOrderIdAndSkuId(403L, sku.getId()).orElseThrow().getState())
            .isEqualTo("PAID");
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
