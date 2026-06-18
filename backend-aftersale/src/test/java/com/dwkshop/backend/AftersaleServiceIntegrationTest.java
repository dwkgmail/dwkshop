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
                List.of(new com.dwkshop.backend.aftersale.RefundOrderItemSnapshot(501L, 101L, 2, true))
            )
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
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDED"))
            .andExpect(jsonPath("$.refundTime").isNotEmpty());

        AftersaleOutboxEvent outbox = outboxEventRepository.findAll().get(0);
        assertThat(outbox.getPublishStatus()).isEqualTo("PENDING");
        assertThat(outbox.getEventType()).isEqualTo("REFUND_APPROVED");
        assertThat(outbox.getPayloadJson()).contains(created.getAftersaleNo()).contains("\"skuId\":501").contains("\"quantity\":2");

        AftersaleRefundFlow flow = refundFlowRepository.findByAftersaleId(created.getId()).orElseThrow();
        assertThat(flow.getFlowStatus()).isEqualTo("COMPLETED");
        assertThat(flow.getCurrentStep()).isEqualTo("EVENT_PENDING");
        assertThat(flow.getRetryCount()).isEqualTo(0);

        mockMvc.perform(get("/api/aftersales/{id}", created.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aftersaleStatus").value("REFUNDED"));
    }

    @Test
    void rejectUsesOrderServiceAndPersistsRejectReason() throws Exception {
        when(orderClient.applyAftersale(101L, 1L)).thenReturn(
            new AftersaleOrderSnapshot(101L, "SO202606180101", 1L, "13800000001", "WAIT_SHIP", "PAID", "NONE", 15900, true)
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

    private String adminToken() {
        return authTokenService.issue(1L, "admin", "ADMIN");
    }
}
