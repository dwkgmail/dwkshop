package com.dwkshop.backend;

import com.dwkshop.backend.domain.entity.TradeOrder;
import com.dwkshop.backend.domain.entity.TradeOrderAmount;
import com.dwkshop.backend.domain.entity.TradeOrderItem;
import com.dwkshop.backend.domain.repository.TradeOrderAmountRepository;
import com.dwkshop.backend.domain.repository.TradeOrderItemRepository;
import com.dwkshop.backend.domain.repository.TradeOrderRepository;
import com.dwkshop.backend.auth.InternalServiceAuthConfig;
import com.dwkshop.backend.order.CartClient;
import com.dwkshop.backend.order.MarketingClient;
import com.dwkshop.backend.order.MemberClient;
import com.dwkshop.backend.order.ProductCatalogClient;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OrderInternalApiIntegrationTest {

    private static final String INTERNAL_SECRET = "dwkshop-local-internal-secret-change-me";

    private final MockMvc mockMvc;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderItemRepository tradeOrderItemRepository;
    private final TradeOrderAmountRepository tradeOrderAmountRepository;

    @MockBean
    private CartClient cartClient;

    @MockBean
    private MemberClient memberClient;

    @MockBean
    private MarketingClient marketingClient;

    @MockBean
    private ProductCatalogClient productCatalogClient;

    @Autowired
    OrderInternalApiIntegrationTest(
        MockMvc mockMvc,
        TradeOrderRepository tradeOrderRepository,
        TradeOrderItemRepository tradeOrderItemRepository,
        TradeOrderAmountRepository tradeOrderAmountRepository
    ) {
        this.mockMvc = mockMvc;
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderItemRepository = tradeOrderItemRepository;
        this.tradeOrderAmountRepository = tradeOrderAmountRepository;
    }

    @BeforeEach
    void resetState() {
        tradeOrderItemRepository.deleteAllInBatch();
        tradeOrderAmountRepository.deleteAllInBatch();
        tradeOrderRepository.deleteAllInBatch();
    }

    @Test
    void internalSnapshotAndRefundContextExposePersistedOrderData() throws Exception {
        Long orderId = seedOrder("SO202606180001", 19900, 2, true);

        mockMvc.perform(get("/internal/orders/{orderId}/aftersale", orderId)
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNo").value("SO202606180001"))
            .andExpect(jsonPath("$.orderStatus").value("WAIT_SHIP"))
            .andExpect(jsonPath("$.payStatus").value("PAID"))
            .andExpect(jsonPath("$.aftersaleStatus").value("NONE"))
            .andExpect(jsonPath("$.payAmount").value(19900))
            .andExpect(jsonPath("$.refundable").value(true));

        mockMvc.perform(get("/internal/orders/{orderId}/refund-context", orderId)
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNo").value("SO202606180001"))
            .andExpect(jsonPath("$.deliveryStatus").value("UNSHIPPED"))
            .andExpect(jsonPath("$.refundable").value(true))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].skuId").value(501))
            .andExpect(jsonPath("$.items[0].supportRefund").value(true));
    }

    @Test
    void internalAftersaleApplyApproveAndRejectUpdateOrderState() throws Exception {
        Long orderId = seedOrder("SO202606180002", 29900, 2, true);

        mockMvc.perform(post("/internal/orders/{orderId}/aftersale/apply", orderId)
                .param("userId", "1")
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(orderId))
            .andExpect(jsonPath("$.aftersaleStatus").value("APPLYING"))
            .andExpect(jsonPath("$.payStatus").value("PAID"));

        mockMvc.perform(post("/internal/orders/{orderId}/aftersale/approve", orderId)
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("WAIT_SHIP"))
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDED"))
            .andExpect(jsonPath("$.payStatus").value("REFUNDED"));

        TradeOrder refundedOrder = tradeOrderRepository.findById(orderId).orElseThrow();
        assertThat(refundedOrder.getOrderStatus()).isEqualTo("WAIT_SHIP");
        assertThat(refundedOrder.getAftersaleStatus()).isEqualTo("REFUNDED");
        assertThat(refundedOrder.getPayStatus()).isEqualTo("REFUNDED");

        List<TradeOrderItem> items = tradeOrderItemRepository.findByOrderId(orderId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getAftersaleQuantity()).isEqualTo(2);
    }

    @Test
    void internalAftersaleRejectMovesOrderToRejected() throws Exception {
        Long orderId = seedOrder("SO202606180003", 15900, 1, true);

        mockMvc.perform(post("/internal/orders/{orderId}/aftersale/apply", orderId)
                .param("userId", "1")
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("APPLYING"));

        mockMvc.perform(post("/internal/orders/{orderId}/aftersale/reject", orderId)
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REJECTED"))
            .andExpect(jsonPath("$.payStatus").value("PAID"));

        TradeOrder rejectedOrder = tradeOrderRepository.findById(orderId).orElseThrow();
        assertThat(rejectedOrder.getAftersaleStatus()).isEqualTo("REJECTED");
        assertThat(rejectedOrder.getPayStatus()).isEqualTo("PAID");
    }

    private Long seedOrder(String orderNo, int payAmount, int quantity, boolean supportRefund) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 18, 10, 0);

        TradeOrder order = new TradeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(1L);
        order.setOrderStatus("WAIT_SHIP");
        order.setPayStatus("PAID");
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
        order.setReceiverAddress("Shanghai Pudong");
        order.setRemark("test order");
        order.setPayExpireTime(now.plusMinutes(30));
        order.setPayTime(now);
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
        item.setSalePrice(payAmount / quantity);
        item.setQuantity(quantity);
        item.setTotalAmount(payAmount);
        item.setDiscountAmount(0);
        item.setPayAmount(payAmount);
        item.setSupportRefund(supportRefund);
        item.setAftersaleQuantity(0);
        item.setCreatedAt(now);
        tradeOrderItemRepository.save(item);

        TradeOrderAmount amount = new TradeOrderAmount();
        amount.setOrderId(savedOrder.getId());
        amount.setProductAmount(payAmount);
        amount.setActivityDiscountAmount(0);
        amount.setCouponDiscountAmount(0);
        amount.setPointDiscountAmount(0);
        amount.setFreightAmount(0);
        amount.setFreightDiscountAmount(0);
        amount.setPayAmount(payAmount);
        amount.setCreatedAt(now);
        tradeOrderAmountRepository.save(amount);

        return savedOrder.getId();
    }
}
