package com.dwkshop.backend;

import com.dwkshop.backend.aftersale.AftersaleOrderSnapshot;
import com.dwkshop.backend.aftersale.OrderClient;
import com.dwkshop.backend.aftersale.ProductClient;
import com.dwkshop.backend.aftersale.RefundOrderContext;
import com.dwkshop.backend.auth.AuthTokenService;
import com.dwkshop.backend.domain.entity.AftersaleOrder;
import com.dwkshop.backend.domain.entity.AftersaleRefundFlow;
import com.dwkshop.backend.domain.entity.AftersaleOutboxEvent;
import com.dwkshop.backend.domain.repository.AftersaleOrderRepository;
import com.dwkshop.backend.domain.repository.AftersaleRefundFlowRepository;
import com.dwkshop.backend.domain.repository.AftersaleOutboxEventRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AftersaleServiceIntegrationTest {

    private final MockMvc mockMvc;
    private final AftersaleOrderRepository aftersaleOrderRepository;
    private final AftersaleRefundFlowRepository refundFlowRepository;
    private final AftersaleOutboxEventRepository outboxEventRepository;
    private final AuthTokenService authTokenService;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private ProductClient productClient;

    @Autowired
    AftersaleServiceIntegrationTest(
        MockMvc mockMvc,
        AftersaleOrderRepository aftersaleOrderRepository,
        AftersaleRefundFlowRepository refundFlowRepository,
        AftersaleOutboxEventRepository outboxEventRepository,
        AuthTokenService authTokenService
    ) {
        this.mockMvc = mockMvc;
        this.aftersaleOrderRepository = aftersaleOrderRepository;
        this.refundFlowRepository = refundFlowRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.authTokenService = authTokenService;
    }

    @BeforeEach
    void resetState() {
        outboxEventRepository.deleteAllInBatch();
        refundFlowRepository.deleteAllInBatch();
        aftersaleOrderRepository.deleteAllInBatch();
    }

    @Test
    void createApproveAndReadRefundFlowThroughInternalClients() throws Exception {
        when(orderClient.applyAftersale(100L, 1L)).thenReturn(
            new AftersaleOrderSnapshot(100L, "SO202606180100", 1L, "13800000000", "WAIT_SHIP", "PAID", "NONE", 19900, true)
        );
        when(orderClient.getRefundContext(100L)).thenReturn(
            new RefundOrderContext(
                100L,
                "SO202606180100",
                1L,
                "WAIT_SHIP",
                "PAID",
                "UNSHIPPED",
                "APPLYING",
                19900,
                true,
                List.of(refundItem(501L, 101L, 2, 2, 0, 0, 19900, 0, 19900))
            )
        );
        when(orderClient.completeAftersale(100L)).thenReturn(
            new AftersaleOrderSnapshot(100L, "SO202606180100", 1L, "13800000000", "WAIT_SHIP", "REFUNDED", "REFUNDED", 19900, true)
        );

        mockMvc.perform(post("/api/aftersales")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 100,
                      "reason": "Changed mind"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(100))
            .andExpect(jsonPath("$.orderNo").value("SO202606180100"))
            .andExpect(jsonPath("$.aftersaleStatus").value("APPLYING"))
            .andExpect(jsonPath("$.refundAmount").value(19900));

        AftersaleOrder created = aftersaleOrderRepository.findAll().get(0);
        assertThat(created.getAftersaleStatus()).isEqualTo("APPLYING");
        assertThat(created.getRefundAmount()).isEqualTo(19900);

        mockMvc.perform(post("/admin/aftersales/{id}/approve", created.getId())
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Admin-Confirm", "true")
                .header("X-Admin-Reason", "test approval"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDING"))
            .andExpect(jsonPath("$.refundTime").doesNotExist());

        AftersaleOutboxEvent outbox = outboxEventRepository.findAll().get(0);
        assertThat(outbox.getPublishStatus()).isEqualTo("PENDING");
        assertThat(outbox.getEventType()).isEqualTo("REFUND_APPROVED");
        assertThat(outbox.getPayloadJson()).contains(created.getAftersaleNo()).contains("\"skuId\":501").contains("\"quantity\":2");

        AftersaleRefundFlow flow = refundFlowRepository.findByAftersaleId(created.getId()).orElseThrow();
        assertThat(flow.getFlowStatus()).isEqualTo("REFUNDING");
        assertThat(flow.getCurrentStep()).isEqualTo("EVENT_PENDING");
        assertThat(flow.getRetryCount()).isEqualTo(0);

        mockMvc.perform(post("/admin/aftersales/{id}/refund/fail", created.getId())
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Admin-Confirm", "true")
                .header("X-Admin-Reason", "Payment channel timeout")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "failureReason": "Payment channel timeout"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUND_FAILED"))
            .andExpect(jsonPath("$.rejectReason").value("Payment channel timeout"));

        flow = refundFlowRepository.findByAftersaleId(created.getId()).orElseThrow();
        assertThat(flow.getFlowStatus()).isEqualTo("FAILED");
        assertThat(flow.getCurrentStep()).isEqualTo("CHANNEL_FAILED");
        assertThat(flow.getLastError()).isEqualTo("Payment channel timeout");

        mockMvc.perform(post("/admin/aftersales/{id}/refund/retry", created.getId())
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Admin-Confirm", "true")
                .header("X-Admin-Reason", "test retry"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDING"))
            .andExpect(jsonPath("$.rejectReason").doesNotExist());

        assertThat(outboxEventRepository.findAll()).hasSize(1);
        flow = refundFlowRepository.findByAftersaleId(created.getId()).orElseThrow();
        assertThat(flow.getFlowStatus()).isEqualTo("REFUNDING");
        assertThat(flow.getCurrentStep()).isEqualTo("EVENT_PENDING");
        assertThat(flow.getRetryCount()).isEqualTo(1);

        mockMvc.perform(post("/admin/aftersales/{id}/refund/complete", created.getId())
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Admin-Confirm", "true")
                .header("X-Admin-Reason", "test complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDED"))
            .andExpect(jsonPath("$.refundTime").isNotEmpty());

        mockMvc.perform(get("/api/aftersales/{id}", created.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDED"));
    }

    @Test
    void duplicateRefundApprovalReturnsRefundedStateAndDoesNotAppendAnotherOutboxEvent() throws Exception {
        when(orderClient.applyAftersale(102L, 1L)).thenReturn(
            new AftersaleOrderSnapshot(102L, "SO202606180102", 1L, "13800000002", "WAIT_SHIP", "PAID", "NONE", 9900, true)
        );
        when(orderClient.getRefundContext(102L)).thenReturn(
            new RefundOrderContext(
                102L,
                "SO202606180102",
                1L,
                "WAIT_SHIP",
                "PAID",
                "UNSHIPPED",
                "APPLYING",
                9900,
                true,
                List.of(refundItem(502L, 102L, 1, 1, 0, 0, 9900, 0, 9900))
            )
        );
        when(orderClient.getAftersaleSnapshot(102L)).thenReturn(
            new AftersaleOrderSnapshot(102L, "SO202606180102", 1L, "13800000002", "WAIT_SHIP", "REFUNDED", "REFUNDED", 9900, true)
        );

        mockMvc.perform(post("/api/aftersales")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 102,
                      "reason": "Duplicate approval check"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("APPLYING"));

        AftersaleOrder created = aftersaleOrderRepository.findAll().get(0);

        mockMvc.perform(post("/admin/aftersales/{id}/approve", created.getId())
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Admin-Confirm", "true")
                .header("X-Admin-Reason", "test approval"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDING"));

        mockMvc.perform(post("/admin/aftersales/{id}/approve", created.getId())
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Admin-Confirm", "true")
                .header("X-Admin-Reason", "test duplicate approval"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDING"));

        assertThat(outboxEventRepository.findAll()).hasSize(1);
        assertThat(refundFlowRepository.findByAftersaleId(created.getId()).orElseThrow().getFlowStatus())
            .isEqualTo("REFUNDING");
        verify(orderClient, times(2)).getRefundContext(102L);
    }

    @Test
    void returnRefundWaitsForReturnedGoodsBeforeRefunding() throws Exception {
        when(orderClient.applyAftersale(103L, 1L)).thenReturn(
            new AftersaleOrderSnapshot(103L, "SO202606180103", 1L, "13800000003", "WAIT_RECEIVE", "PAID", "NONE", 25900, true)
        );
        when(orderClient.getRefundContext(103L)).thenReturn(
            new RefundOrderContext(
                103L,
                "SO202606180103",
                1L,
                "WAIT_RECEIVE",
                "PAID",
                "SHIPPED",
                "APPLYING",
                25900,
                true,
                List.of(refundItem(504L, 103L, 1, 1, 0, 0, 25900, 0, 25900))
            )
        );
        when(orderClient.completeAftersale(103L)).thenReturn(
            new AftersaleOrderSnapshot(103L, "SO202606180103", 1L, "13800000003", "WAIT_RECEIVE", "REFUNDED", "REFUNDED", 25900, true)
        );

        mockMvc.perform(post("/api/aftersales")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 103,
                      "aftersaleType": "RETURN_AND_REFUND",
                      "reason": "Size is not right"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("APPLYING"))
            .andExpect(jsonPath("$.aftersaleType").value("RETURN_AND_REFUND"));

        AftersaleOrder created = aftersaleOrderRepository.findAll().get(0);

        mockMvc.perform(post("/admin/aftersales/{id}/approve", created.getId())
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Admin-Confirm", "true")
                .header("X-Admin-Reason", "test return approval"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("WAIT_RETURN"));

        assertThat(outboxEventRepository.findAll()).isEmpty();
        assertThat(refundFlowRepository.findByAftersaleId(created.getId()).orElseThrow().getCurrentStep())
            .isEqualTo("WAIT_RETURN");

        mockMvc.perform(post("/admin/aftersales/{id}/return", created.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDING"));

        assertThat(outboxEventRepository.findAll()).hasSize(1);

        mockMvc.perform(post("/admin/aftersales/{id}/refund/complete", created.getId())
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Admin-Confirm", "true")
                .header("X-Admin-Reason", "test complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDED"));
    }

    @Test
    void rejectUsesOrderServiceAndPersistsRejectReason() throws Exception {
        when(orderClient.applyAftersale(101L, 1L)).thenReturn(
            new AftersaleOrderSnapshot(101L, "SO202606180101", 1L, "13800000001", "WAIT_SHIP", "PAID", "NONE", 15900, true)
        );
        when(orderClient.getRefundContext(101L)).thenReturn(
            new RefundOrderContext(
                101L,
                "SO202606180101",
                1L,
                "WAIT_SHIP",
                "PAID",
                "UNSHIPPED",
                "APPLYING",
                15900,
                true,
                List.of(refundItem(503L, 101L, 1, 1, 0, 0, 15900, 0, 15900))
            )
        );
        when(orderClient.rejectAftersale(101L)).thenReturn(
            new AftersaleOrderSnapshot(101L, "SO202606180101", 1L, "13800000001", "WAIT_SHIP", "PAID", "REJECTED", 15900, true)
        );

        mockMvc.perform(post("/api/aftersales")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 101,
                      "reason": "Warranty expired"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("APPLYING"));

        AftersaleOrder created = aftersaleOrderRepository.findAll().get(0);

        mockMvc.perform(post("/admin/aftersales/{id}/reject", created.getId())
                .header("Authorization", "Bearer " + adminToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "rejectReason": "No eligible proof"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REJECTED"))
            .andExpect(jsonPath("$.rejectReason").value("No eligible proof"));

        verify(orderClient).rejectAftersale(101L);

        AftersaleOrder rejected = aftersaleOrderRepository.findById(created.getId()).orElseThrow();
        assertThat(rejected.getAftersaleStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getRejectReason()).isEqualTo("No eligible proof");

        AftersaleRefundFlow flow = refundFlowRepository.findByAftersaleId(created.getId()).orElseThrow();
        assertThat(flow.getFlowStatus()).isEqualTo("REJECTED");
        assertThat(flow.getCurrentStep()).isEqualTo("DONE");
    }

    @Test
    void createPartialRefundUsesItemSnapshotAndDoesNotAlwaysRefundOrderPayAmount() throws Exception {
        when(orderClient.applyAftersale(104L, 1L)).thenReturn(
            new AftersaleOrderSnapshot(104L, "SO202606180104", 1L, "13800000004", "WAIT_SHIP", "PAID", "NONE", 28200, true)
        );
        when(orderClient.getRefundContext(104L)).thenReturn(
            new RefundOrderContext(
                104L,
                "SO202606180104",
                1L,
                "WAIT_SHIP",
                "PAID",
                "UNSHIPPED",
                "NONE",
                28200,
                true,
                List.of(
                    new com.dwkshop.backend.aftersale.RefundOrderItemSnapshot(
                        505L, 104L, 2, 2, 0, 0,
                        12000, 12000, 1500, 500, 200,
                        0, 0, 12200, "NONE", true
                    ),
                    new com.dwkshop.backend.aftersale.RefundOrderItemSnapshot(
                        506L, 105L, 1, 1, 0, 0,
                        16000, 16000, 2000, 800, 300,
                        0, 0, 16300, "NONE", true
                    )
                )
            )
        );

        mockMvc.perform(post("/api/aftersales")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 104,
                      "reason": "Only one item",
                      "refundItems": [
                        { "skuId": 505, "quantity": 1 }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refundAmount").value(6000))
            .andExpect(jsonPath("$.refundItems[0].refundAmount").value(6000));

        AftersaleOrder created = aftersaleOrderRepository.findAll().get(0);
        assertThat(created.getRefundAmount()).isEqualTo(6000);
    }

    private String adminToken() {
        return authTokenService.issue(1L, "admin", "ADMIN");
    }

    private com.dwkshop.backend.aftersale.RefundOrderItemSnapshot refundItem(
        Long skuId,
        Long productId,
        int quantity,
        int refundableQuantity,
        int refundedQuantity,
        int aftersaleQuantity,
        int payAmount,
        int refundAmount,
        int refundableAmount
    ) {
        return new com.dwkshop.backend.aftersale.RefundOrderItemSnapshot(
            skuId,
            productId,
            quantity,
            refundableQuantity,
            refundedQuantity,
            aftersaleQuantity,
            payAmount,
            payAmount,
            0,
            0,
            0,
            refundAmount,
            refundAmount,
            refundableAmount,
            refundAmount > 0 ? "PARTIAL_REFUNDED" : "NONE",
            true
        );
    }
}
