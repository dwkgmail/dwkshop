package com.dwkshop.backend.aftersale;

import com.dwkshop.backend.aftersale.dto.AftersaleResponse;
import com.dwkshop.backend.aftersale.dto.CreateAftersaleRequest;
import com.dwkshop.backend.aftersale.dto.RejectAftersaleRequest;
import com.dwkshop.backend.domain.entity.AftersaleOrder;
import com.dwkshop.backend.domain.entity.AftersaleRefundFlow;
import com.dwkshop.backend.domain.repository.AftersaleOrderRepository;
import com.dwkshop.backend.domain.repository.AftersaleRefundFlowRepository;
import com.dwkshop.backend.util.PriceFormatter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AftersaleService {

    private static final String APPLYING = "APPLYING";
    private static final String REFUNDED = "REFUNDED";
    private static final String REJECTED = "REJECTED";
    private static final String FLOW_PENDING = "PENDING";
    private static final String FLOW_COMPLETED = "COMPLETED";
    private static final String STEP_EVENT_PENDING = "EVENT_PENDING";

    private final AftersaleOrderRepository aftersaleOrderRepository;
    private final AftersaleRefundFlowRepository refundFlowRepository;
    private final OrderClient orderClient;
    private final RefundApprovedOutbox refundApprovedOutbox;

    public AftersaleService(
        AftersaleOrderRepository aftersaleOrderRepository,
        AftersaleRefundFlowRepository refundFlowRepository,
        OrderClient orderClient,
        RefundApprovedOutbox refundApprovedOutbox
    ) {
        this.aftersaleOrderRepository = aftersaleOrderRepository;
        this.refundFlowRepository = refundFlowRepository;
        this.orderClient = orderClient;
        this.refundApprovedOutbox = refundApprovedOutbox;
    }

    @Transactional
    public AftersaleResponse create(Long userId, CreateAftersaleRequest request) {
        aftersaleOrderRepository.findFirstByOrderIdAndAftersaleStatusIn(request.orderId(), List.of(APPLYING, REFUNDED))
            .ifPresent(item -> {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund request already exists");
            });
        AftersaleOrderSnapshot order = orderClient.applyAftersale(request.orderId(), userId);

        LocalDateTime now = LocalDateTime.now();
        AftersaleOrder aftersale = new AftersaleOrder();
        aftersale.setAftersaleNo("AS" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now) + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        aftersale.setOrderId(order.id());
        aftersale.setUserId(userId);
        aftersale.setAftersaleType("REFUND");
        aftersale.setAftersaleStatus(APPLYING);
        aftersale.setRefundAmount(order.payAmount());
        aftersale.setReason(request.reason().trim());
        aftersale.setApplyTime(now);
        aftersale.setCreatedAt(now);
        aftersale.setUpdatedAt(now);
        AftersaleOrder saved = aftersaleOrderRepository.save(aftersale);
        saveFlow(saved, FLOW_PENDING, null, 0, null);
        return toResponse(saved, order);
    }

    @Transactional(readOnly = true)
    public List<AftersaleResponse> listUser(Long userId) {
        return aftersaleOrderRepository.findByUserIdOrderByIdDesc(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public AftersaleResponse getUser(Long userId, Long id) {
        AftersaleOrder aftersale = aftersaleOrderRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found"));
        return toResponse(aftersale);
    }

    @Transactional(readOnly = true)
    public List<AftersaleResponse> listAdmin() {
        return aftersaleOrderRepository.findAllByOrderByIdDesc().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public AftersaleResponse approve(Long id) {
        AftersaleOrder aftersale = aftersaleOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found"));
        if (REFUNDED.equals(aftersale.getAftersaleStatus())) {
            return toResponse(aftersale);
        }
        if (!APPLYING.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aftersale is not applying");
        }

        RefundOrderContext orderContext = orderClient.getRefundContext(aftersale.getOrderId());
        if (!Boolean.TRUE.equals(orderContext.refundable())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not refundable");
        }
        LocalDateTime now = LocalDateTime.now();
        aftersale.setAftersaleStatus(REFUNDED);
        aftersale.setAuditTime(now);
        aftersale.setRefundTime(now);
        aftersale.setUpdatedAt(now);
        aftersaleOrderRepository.save(aftersale);
        refundApprovedOutbox.append(aftersale, orderContext, now);
        saveFlow(loadOrCreateFlow(aftersale), FLOW_COMPLETED, STEP_EVENT_PENDING, 0, null);
        return toResponse(aftersale, approvedOrderSnapshot(orderContext));
    }

    @Transactional
    public AftersaleResponse reject(Long id, RejectAftersaleRequest request) {
        AftersaleOrder aftersale = aftersaleOrderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found"));
        if (REJECTED.equals(aftersale.getAftersaleStatus())) {
            return toResponse(aftersale);
        }
        if (REFUNDED.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aftersale is already refunded");
        }
        if (!APPLYING.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aftersale is not applying");
        }
        AftersaleOrderSnapshot order = orderClient.rejectAftersale(aftersale.getOrderId());

        LocalDateTime now = LocalDateTime.now();
        aftersale.setAftersaleStatus(REJECTED);
        aftersale.setRejectReason(request == null || request.rejectReason() == null || request.rejectReason().isBlank() ? "Rejected by admin" : request.rejectReason().trim());
        aftersale.setAuditTime(now);
        aftersale.setUpdatedAt(now);
        aftersaleOrderRepository.save(aftersale);
        saveFlow(loadOrCreateFlow(aftersale), REJECTED, "DONE", 0, null);

        return toResponse(aftersale, order);
    }

    private AftersaleResponse toResponse(AftersaleOrder aftersale) {
        return toResponse(aftersale, orderClient.getAftersaleSnapshot(aftersale.getOrderId()));
    }

    private AftersaleResponse toResponse(AftersaleOrder aftersale, AftersaleOrderSnapshot order) {
        return new AftersaleResponse(
            aftersale.getId(),
            aftersale.getAftersaleNo(),
            aftersale.getOrderId(),
            order == null ? null : order.orderNo(),
            aftersale.getUserId(),
            order == null ? null : order.receiverMobile(),
            aftersale.getAftersaleType(),
            aftersale.getAftersaleStatus(),
            aftersale.getRefundAmount(),
            PriceFormatter.formatCents(aftersale.getRefundAmount()),
            aftersale.getReason(),
            aftersale.getRejectReason(),
            aftersale.getApplyTime(),
            aftersale.getAuditTime(),
            aftersale.getRefundTime()
        );
    }

    private AftersaleRefundFlow loadOrCreateFlow(AftersaleOrder aftersale) {
        return refundFlowRepository.findByAftersaleId(aftersale.getId())
            .orElseGet(() -> saveFlow(aftersale, FLOW_PENDING, null, 0, null));
    }

    private AftersaleRefundFlow saveFlow(AftersaleOrder aftersale, String flowStatus, String currentStep, int retryCount, String lastError) {
        AftersaleRefundFlow flow = refundFlowRepository.findByAftersaleId(aftersale.getId()).orElseGet(AftersaleRefundFlow::new);
        flow.setAftersaleId(aftersale.getId());
        flow.setAftersaleNo(aftersale.getAftersaleNo());
        flow.setOrderId(aftersale.getOrderId());
        flow.setFlowStatus(flowStatus);
        flow.setCurrentStep(currentStep);
        flow.setRetryCount(retryCount);
        flow.setLastError(lastError);
        flow.setCommandNo(flow.getCommandNo() == null ? refundCommandNo(aftersale.getAftersaleNo(), "flow") : flow.getCommandNo());
        LocalDateTime now = LocalDateTime.now();
        if (flow.getCreatedAt() == null) {
            flow.setCreatedAt(now);
        }
        flow.setUpdatedAt(now);
        return refundFlowRepository.save(flow);
    }

    private AftersaleRefundFlow saveFlow(AftersaleRefundFlow flow, String flowStatus, String currentStep, int retryCount, String lastError) {
        flow.setFlowStatus(flowStatus);
        flow.setCurrentStep(currentStep);
        flow.setRetryCount(retryCount);
        flow.setLastError(lastError);
        flow.setUpdatedAt(LocalDateTime.now());
        return refundFlowRepository.save(flow);
    }

    private String refundCommandNo(String base, String action) {
        return base + "-" + action.toUpperCase();
    }

    private AftersaleOrderSnapshot approvedOrderSnapshot(RefundOrderContext context) {
        return new AftersaleOrderSnapshot(
            context.orderId(), context.orderNo(), context.userId(), null, context.orderStatus(), "REFUNDED", "REFUNDED",
            context.payAmount(), context.refundable()
        );
    }
}
