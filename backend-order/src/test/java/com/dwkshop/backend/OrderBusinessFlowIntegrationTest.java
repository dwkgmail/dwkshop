package com.dwkshop.backend;

import com.dwkshop.backend.auth.AuthTokenService;
import com.dwkshop.backend.auth.InternalServiceAuthConfig;
import com.dwkshop.backend.domain.entity.TradeOrder;
import com.dwkshop.backend.domain.entity.TradeOrderAmount;
import com.dwkshop.backend.domain.entity.TradeOrderItem;
import com.dwkshop.backend.domain.repository.OrderOutboxEventRepository;
import com.dwkshop.backend.domain.repository.PaymentOrderRepository;
import com.dwkshop.backend.domain.repository.PaymentTransactionRepository;
import com.dwkshop.backend.domain.repository.TradeOrderAmountRepository;
import com.dwkshop.backend.domain.repository.TradeOrderItemRepository;
import com.dwkshop.backend.domain.repository.TradeOrderRepository;
import com.dwkshop.backend.event.InventoryIntegrationEvent;
import com.dwkshop.backend.order.CartClient;
import com.dwkshop.backend.order.MarketingClient;
import com.dwkshop.backend.order.MarketingCoupon;
import com.dwkshop.backend.order.MarketingCouponSelection;
import com.dwkshop.backend.order.MemberAddress;
import com.dwkshop.backend.order.MemberClient;
import com.dwkshop.backend.order.MemberPointAccount;
import com.dwkshop.backend.order.ProductCatalogClient;
import com.dwkshop.backend.order.ProductSkuSnapshot;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OrderBusinessFlowIntegrationTest {

    private static final String INTERNAL_SECRET = "dwkshop-local-internal-secret-change-me";

    @Autowired MockMvc mockMvc;
    @Autowired TradeOrderRepository tradeOrderRepository;
    @Autowired TradeOrderItemRepository tradeOrderItemRepository;
    @Autowired TradeOrderAmountRepository tradeOrderAmountRepository;
    @Autowired OrderOutboxEventRepository orderOutboxEventRepository;
    @Autowired PaymentOrderRepository paymentOrderRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired AuthTokenService authTokenService;

    @MockBean CartClient cartClient;
    @MockBean MemberClient memberClient;
    @MockBean MarketingClient marketingClient;
    @MockBean ProductCatalogClient productCatalogClient;

    @BeforeEach
    void resetState() {
        paymentTransactionRepository.deleteAllInBatch();
        paymentOrderRepository.deleteAllInBatch();
        orderOutboxEventRepository.deleteAllInBatch();
        tradeOrderItemRepository.deleteAllInBatch();
        tradeOrderAmountRepository.deleteAllInBatch();
        tradeOrderRepository.deleteAllInBatch();
        when(memberClient.resolveAddress(eq(1L), eq(10L))).thenReturn(address());
        when(memberClient.getPointAccount(1L)).thenReturn(new MemberPointAccount(1L, 0));
        when(marketingClient.selectCoupon(eq(1L), eq(null), anyInt())).thenReturn(new MarketingCouponSelection(null, 0, List.of()));
    }

    @Test
    void confirmFailsWhenSkuStockIsInsufficient() throws Exception {
        when(productCatalogClient.getSkuSnapshot(501L)).thenReturn(sku(501L, 1, 1000, true));

        mockMvc.perform(post("/api/orders/confirm")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "sourceType": "BUY_NOW",
                      "skuId": 501,
                      "quantity": 2,
                      "addressId": 10
                    }
                    """))
            .andExpect(status().isBadRequest());

        assertThat(tradeOrderRepository.count()).isZero();
    }

    @Test
    void couponLockFailurePreventsOrderAndOutboxCreation() throws Exception {
        when(productCatalogClient.getSkuSnapshot(502L)).thenReturn(sku(502L, 5, 1000, true));
        when(marketingClient.selectCoupon(eq(1L), eq(9001L), anyInt())).thenReturn(new MarketingCouponSelection(
            9001L,
            300,
            List.of(new MarketingCoupon(9001L, 101L, "New User Coupon", "FULL_REDUCTION", 1000, 300, true))
        ));
        doThrow(new IllegalStateException("coupon already used")).when(marketingClient).lockCoupon(eq(1L), eq(9001L), org.mockito.ArgumentMatchers.anyString(), eq(1000));

        String token = confirmToken("""
            {
              "sourceType": "BUY_NOW",
              "skuId": 502,
              "quantity": 1,
              "addressId": 10,
              "couponUserId": 9001
            }
            """);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/orders/create")
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "settlementToken": "%s",
                  "expectedPayAmount": 700
                }
                """.formatted(token))))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasMessageContaining("coupon already used");

        assertThat(tradeOrderRepository.count()).isZero();
        assertThat(tradeOrderItemRepository.count()).isZero();
        assertThat(tradeOrderAmountRepository.count()).isZero();
        assertThat(orderOutboxEventRepository.count()).isZero();
    }

    @Test
    void concurrentCreateWithSameSettlementTokenPersistsOnlyOneOrderAndOutbox() throws Exception {
        when(productCatalogClient.getSkuSnapshot(504L)).thenReturn(sku(504L, 5, 1200, true));
        String token = confirmToken("""
            {
              "sourceType": "BUY_NOW",
              "skuId": 504,
              "quantity": 1,
              "addressId": 10
            }
            """);

        CountDownLatch start = new CountDownLatch(1);
        var statuses = java.util.Collections.synchronizedList(new ArrayList<Integer>());
        var executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    int status = mockMvc.perform(post("/api/orders/create")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                {
                                  "settlementToken": "%s",
                                  "expectedPayAmount": 1200
                                }
                                """.formatted(token)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                    statuses.add(status);
                } catch (Exception ex) {
                    statuses.add(500);
                }
            });
        }

        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        assertThat(tradeOrderRepository.count()).isEqualTo(1);
        assertThat(tradeOrderItemRepository.count()).isEqualTo(1);
        assertThat(tradeOrderAmountRepository.count()).isEqualTo(1);
        assertThat(orderOutboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void createOrderWithSameClientRequestIdReturnsExistingOrderAfterTokenWasConsumed() throws Exception {
        when(productCatalogClient.getSkuSnapshot(505L)).thenReturn(sku(505L, 5, 1300, true));
        String token = confirmToken("""
            {
              "sourceType": "BUY_NOW",
              "skuId": 505,
              "quantity": 1,
              "addressId": 10
            }
            """);

        String first = mockMvc.perform(post("/api/orders/create")
                .contentType(APPLICATION_JSON)
                .header("Idempotency-Key", "checkout-retry-001")
                .content("""
                    {
                      "settlementToken": "%s",
                      "expectedPayAmount": 1300
                    }
                    """.formatted(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Integer firstOrderId = com.jayway.jsonpath.JsonPath.read(first, "$.id");

        mockMvc.perform(post("/api/orders/create")
                .contentType(APPLICATION_JSON)
                .header("Idempotency-Key", "checkout-retry-001")
                .content("""
                    {
                      "settlementToken": "%s",
                      "expectedPayAmount": 1300
                    }
                    """.formatted(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(firstOrderId));

        assertThat(tradeOrderRepository.count()).isEqualTo(1);
        assertThat(tradeOrderItemRepository.count()).isEqualTo(1);
        assertThat(tradeOrderAmountRepository.count()).isEqualTo(1);
        assertThat(orderOutboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void pointDeductionIsPersistedAndRefundApprovalCompensatesOrderState() throws Exception {
        when(productCatalogClient.getSkuSnapshot(503L)).thenReturn(sku(503L, 5, 1500, true));
        when(memberClient.getPointAccount(1L)).thenReturn(new MemberPointAccount(1L, 50000));

        String token = confirmToken("""
            {
              "sourceType": "BUY_NOW",
              "skuId": 503,
              "quantity": 1,
              "addressId": 10,
              "usePoints": true
            }
            """);

        mockMvc.perform(post("/api/orders/create")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "settlementToken": "%s",
                      "expectedPayAmount": 1000
                    }
                    """.formatted(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount.pointDiscountAmount").value(500))
            .andExpect(jsonPath("$.payAmount").value(1000));

        TradeOrder order = tradeOrderRepository.findAll().get(0);
        verify(memberClient).freezePoints(1L, order.getId(), "ORDER_POINT:" + order.getId(), 50000);

        mockMvc.perform(post("/api/orders/{orderId}/pay", order.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payStatus").value("PAID"));
        verify(memberClient).deductFrozenPoints(1L, order.getId(), "ORDER_POINT:" + order.getId(), 50000);

        mockMvc.perform(post("/internal/orders/{orderId}/aftersale/apply", order.getId())
                .param("userId", "1")
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET))
            .andExpect(status().isOk());

        mockMvc.perform(post("/internal/orders/{orderId}/aftersale/approve", order.getId())
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDED"));

        TradeOrder refunded = tradeOrderRepository.findById(order.getId()).orElseThrow();
        TradeOrderAmount amount = tradeOrderAmountRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(refunded.getOrderStatus()).isEqualTo("WAIT_SHIP");
        assertThat(refunded.getPayStatus()).isEqualTo("REFUNDED");
        assertThat(amount.getPointDiscountAmount()).isEqualTo(500);
        verify(memberClient).refundPoints(1L, order.getId(), "ORDER_POINT:" + order.getId(), 50000);
    }

    @Test
    void paymentCallbackIsIdempotentByChannelTradeNoAndEmitsSuccessEventOnce() throws Exception {
        Long orderId = seedUnpaidOrder("SO202606260001", 1600);

        mockMvc.perform(post("/internal/orders/payments/callback")
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "orderId": %d,
                      "channel": "WECHAT",
                      "channelTradeNo": "wx-trade-001",
                      "amount": 1600,
                      "callbackPayload": "{\\"trade_state\\":\\"SUCCESS\\"}"
                    }
                    """.formatted(orderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/internal/orders/payments/callback")
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "orderId": %d,
                      "channel": "WECHAT",
                      "channelTradeNo": "wx-trade-001",
                      "amount": 1600
                    }
                    """.formatted(orderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.duplicate").value(true));

        TradeOrder paid = tradeOrderRepository.findById(orderId).orElseThrow();
        assertThat(paid.getOrderStatus()).isEqualTo("WAIT_SHIP");
        assertThat(paid.getPayStatus()).isEqualTo("PAID");
        assertThat(paymentTransactionRepository.count()).isEqualTo(1);
        assertThat(paymentOrderRepository.count()).isEqualTo(1);
        assertThat(orderOutboxEventRepository.count()).isEqualTo(1);
        assertThat(orderOutboxEventRepository.findAll().get(0).getEventType())
            .isEqualTo(InventoryIntegrationEvent.PAYMENT_SUCCEEDED);
    }

    @Test
    void paymentCallbackRejectsAmountMismatch() throws Exception {
        Long orderId = seedUnpaidOrder("SO202606260002", 1600);

        mockMvc.perform(post("/internal/orders/payments/callback")
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "orderId": %d,
                      "channel": "WECHAT",
                      "channelTradeNo": "wx-trade-amount-mismatch",
                      "amount": 1500
                    }
                    """.formatted(orderId)))
            .andExpect(status().isBadRequest());

        TradeOrder order = tradeOrderRepository.findById(orderId).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo("WAIT_PAY");
        assertThat(order.getPayStatus()).isEqualTo("UNPAID");
        assertThat(paymentTransactionRepository.count()).isZero();
        assertThat(orderOutboxEventRepository.count()).isZero();
    }

    @Test
    void paymentCallbackRejectsNonPayableOrderState() throws Exception {
        Long orderId = seedUnpaidOrder("SO202606260003", 1600);
        TradeOrder order = tradeOrderRepository.findById(orderId).orElseThrow();
        order.setOrderStatus("CANCELED");
        order.setPayStatus("CLOSED");
        order.setCancelTime(LocalDateTime.now());
        tradeOrderRepository.save(order);

        mockMvc.perform(post("/internal/orders/payments/callback")
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "orderId": %d,
                      "channel": "WECHAT",
                      "channelTradeNo": "wx-trade-canceled",
                      "amount": 1600
                    }
                    """.formatted(orderId)))
            .andExpect(status().isBadRequest());

        assertThat(paymentTransactionRepository.count()).isZero();
        assertThat(orderOutboxEventRepository.count()).isZero();
    }

    @Test
    void adminShippingStateMachineRejectsInvalidTransitionsAndCompletesDelivery() throws Exception {
        Long orderId = seedPaidOrder("SO202606230001");

        mockMvc.perform(post("/admin/orders/{orderId}/delivery-status", orderId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "deliveryStatus": "DELIVERED"
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/admin/orders/{orderId}/ship", orderId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "logisticsCompany": "SF",
                      "logisticsNo": "SF123456",
                      "deliveryRemark": "fragile"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("WAIT_RECEIVE"))
            .andExpect(jsonPath("$.deliveryStatus").value("SHIPPED"));

        mockMvc.perform(post("/admin/orders/{orderId}/delivery-status", orderId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "deliveryStatus": "DELIVERED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("FINISHED"))
            .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"));
    }

    private String confirmToken(String payload) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/orders/confirm")
                .contentType(APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(), "$.settlementToken");
    }

    private ProductSkuSnapshot sku(Long skuId, int stock, int salePrice, boolean supportPointDeduction) {
        return new ProductSkuSnapshot(101L, skuId, "Test Product", "/images/product.png", "ON_SALE",
            "NORMAL", false, true, true, supportPointDeduction, "Notice", "Notice content",
            "Default", "{}", salePrice, stock, "ENABLED");
    }

    private MemberAddress address() {
        return new MemberAddress(10L, 1L, "Alice", "13800000000", "Shanghai", "Shanghai", "Pudong", "Century Ave 1", true);
    }

    private Long seedPaidOrder(String orderNo) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 23, 10, 0);
        TradeOrder order = new TradeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(1L);
        order.setOrderStatus("WAIT_SHIP");
        order.setPayStatus("PAID");
        order.setDeliveryStatus("UNSHIPPED");
        order.setAftersaleStatus("NONE");
        order.setSourceType("BUY_NOW");
        order.setTotalAmount(1000);
        order.setDiscountAmount(0);
        order.setCouponAmount(0);
        order.setPointAmount(0);
        order.setFreightAmount(0);
        order.setPayAmount(1000);
        order.setReceiverName("Alice");
        order.setReceiverMobile("13800000000");
        order.setReceiverAddress("Shanghai Pudong Century Ave 1");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return tradeOrderRepository.save(order).getId();
    }

    private Long seedUnpaidOrder(String orderNo, int payAmount) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 10, 0);
        TradeOrder order = new TradeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(1L);
        order.setOrderStatus("WAIT_PAY");
        order.setPayStatus("UNPAID");
        order.setDeliveryStatus("UNSHIPPED");
        order.setAftersaleStatus("NONE");
        order.setSourceType("BUY_NOW");
        order.setTotalAmount(payAmount);
        order.setDiscountAmount(0);
        order.setCouponAmount(0);
        order.setPointAmount(0);
        order.setFreightAmount(0);
        order.setPayAmount(payAmount);
        order.setReceiverName("Alice");
        order.setReceiverMobile("13800000000");
        order.setReceiverAddress("Shanghai Pudong Century Ave 1");
        order.setPayExpireTime(LocalDateTime.now().plusMinutes(30));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        TradeOrder savedOrder = tradeOrderRepository.save(order);

        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(savedOrder.getId());
        item.setProductId(101L);
        item.setSkuId(501L);
        item.setProductName("Test Product");
        item.setSkuName("Default");
        item.setProductImageUrl("/images/product.png");
        item.setSalePrice(payAmount);
        item.setQuantity(1);
        item.setTotalAmount(payAmount);
        item.setDiscountAmount(0);
        item.setPayAmount(payAmount);
        item.setSupportRefund(true);
        item.setAftersaleQuantity(0);
        item.setRefundableQuantity(1);
        item.setRefundedQuantity(0);
        item.setRefundAmount(0);
        item.setRefundStatus("NONE");
        item.setCreatedAt(now);
        tradeOrderItemRepository.save(item);
        return savedOrder.getId();
    }

    private String adminToken() {
        return authTokenService.issue(99L, "admin", "ADMIN");
    }
}
