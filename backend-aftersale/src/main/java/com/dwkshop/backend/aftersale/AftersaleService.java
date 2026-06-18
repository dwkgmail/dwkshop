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
import java.util.function.Supplier;
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
    private static final String FLOW_PROCESSING = "PROCESSING";
    private static final String FLOW_COMPLETED = "COMPLETED";
    private static final String FLOW_COMPENSATED = "COMPENSATED";
    private static final String FLOW_FAILED = "FAILED";
    private static final String STEP_PRODUCT_RELEASE = "PRODUCT_RELEASE";
    private static final String STEP_ORDER_COMPLETE = "ORDER_COMPLETE";
    private static final String STEP_COMPENSATE_PRODUCT = "COMPENSATE_PRODUCT";
    private static final int MAX_RETRY = 3;

    private final AftersaleOrderRepository aftersaleOrderRepository;
    private final AftersaleRefundFlowRepository refundFlowRepository;
    private final OrderClient orderClient;
    private final ProductClient productClient;

    public AftersaleService(
        AftersaleOrderRepository aftersaleOrderRepository,
        AftersaleRefundFlowRepository refundFlowRepository,
        OrderClient orderClient,
        ProductClient productClient
    ) {
        this.aftersaleOrderRepository = aftersaleOrderRepository;
        this.refundFlowRepository = refundFlowRepository;
        this.orderClient = orderClient;
        this.productClient = productClient;
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
        AftersaleOrder aftersale = aftersaleOrderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found"));
        if (REFUNDED.equals(aftersale.getAftersaleStatus())) {
            return toResponse(aftersale);
        }
        if (!APPLYING.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aftersale is not applying");
        }

        AftersaleRefundFlow flow = loadOrCreateFlow(aftersale);
        if (FLOW_COMPLETED.equals(flow.getFlowStatus()) || FLOW_COMPENSATED.equals(flow.getFlowStatus())) {
            return toResponse(markRefunded(aftersale), orderClient.getAftersaleSnapshot(aftersale.getOrderId()));
        }

        flow.setFlowStatus(FLOW_PROCESSING);
        flow.setCurrentStep(STEP_PRODUCT_RELEASE);
        flow.setLastError(null);
        flow.setUpdatedAt(LocalDateTime.now());
        refundFlowRepository.save(flow);

        RefundOrderContext orderContext = executeWithRetry("load refund context", () -> orderClient.getRefundContext(aftersale.getOrderId()));
        List<RefundStockItemRequest> stockItems = orderContext.items().stream()
            .filter(item -> Boolean.TRUE.equals(item.supportRefund()))
            .map(item -> new RefundStockItemRequest(item.skuId(), item.quantity()))
            .toList();
        boolean productReleased = false;

        try {
            if ("WAIT_SHIP".equals(orderContext.orderStatus()) && !stockItems.isEmpty()) {
                executeWithRetry("release refund stock", () -> productClient.releaseRefundStock(refundCommandNo(flow, "release"), stockItems));
                productReleased = true;
                flow.setCurrentStep(STEP_ORDER_COMPLETE);
                flow.setUpdatedAt(LocalDateTime.now());
                refundFlowRepository.save(flow);
            }

            AftersaleOrderSnapshot order = executeWithRetry("complete refund order", () -> orderClient.completeAftersale(aftersale.getOrderId()));
            LocalDateTime now = LocalDateTime.now();
            aftersale.setAftersaleStatus(REFUNDED);
            aftersale.setAuditTime(now);
            aftersale.setRefundTime(now);
            aftersale.setUpdatedAt(now);
            aftersaleOrderRepository.save(aftersale);
            saveFlow(flow, FLOW_COMPLETED, STEP_ORDER_COMPLETE, flow.getRetryCount(), null);
            return toResponse(aftersale, order);
        } catch (RuntimeException ex) {
            if (productReleased) {
                try {
                    executeWithRetry("compensate refund stock", () -> productClient.restoreRefundStock(refundCommandNo(flow, "compensate"), stockItems));
                    saveFlow(flow, FLOW_COMPENSATED, STEP_COMPENSATE_PRODUCT, flow.getRetryCount(), null);
                } catch (RuntimeException compensationEx) {
                    saveFlow(flow, FLOW_FAILED, STEP_COMPENSATE_PRODUCT, incrementRetry(flow), trimError(compensationEx.getMessage()));
                    throw compensationEx;
                }
            }
            saveFlow(flow, FLOW_FAILED, STEP_ORDER_COMPLETE, incrementRetry(flow), trimError(ex.getMessage()));
            throw ex;
        }
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

    private AftersaleOrder markRefunded(AftersaleOrder aftersale) {
        aftersale.setAftersaleStatus(REFUNDED);
        aftersale.setRefundTime(LocalDateTime.now());
        aftersale.setUpdatedAt(LocalDateTime.now());
        return aftersaleOrderRepository.save(aftersale);
    }

    private String refundCommandNo(AftersaleRefundFlow flow, String action) {
        return flow.getAftersaleNo() + "-" + action.toUpperCase();
    }

    private String refundCommandNo(String base, String action) {
        return base + "-" + action.toUpperCase();
    }

    private int incrementRetry(AftersaleRefundFlow flow) {
        int next = flow.getRetryCount() == null ? 1 : flow.getRetryCount() + 1;
        if (next > MAX_RETRY) {
            return MAX_RETRY;
        }
        return next;
    }

    private String trimError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }

    private <T> T executeWithRetry(String action, Supplier<T> supplier) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return supplier.get();
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt == MAX_RETRY) {
                    break;
                }
            }
        }
        throw last == null ? new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, action + " failed") : last;
    }
}
