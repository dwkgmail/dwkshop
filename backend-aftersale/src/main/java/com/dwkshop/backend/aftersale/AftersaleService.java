package com.dwkshop.backend.aftersale;

import com.dwkshop.backend.aftersale.dto.AftersaleResponse;
import com.dwkshop.backend.aftersale.dto.AftersaleItemResponse;
import com.dwkshop.backend.aftersale.dto.CreateAftersaleRequest;
import com.dwkshop.backend.aftersale.dto.RejectAftersaleRequest;
import com.dwkshop.backend.domain.entity.AftersaleOrderItem;
import com.dwkshop.backend.domain.entity.AftersaleOrder;
import com.dwkshop.backend.domain.entity.AftersaleRefundFlow;
import com.dwkshop.backend.domain.repository.AftersaleOrderItemRepository;
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
    private static final String APPROVED = "APPROVED";
    private static final String REFUNDED = "REFUNDED";
    private static final String REJECTED = "REJECTED";
    private static final String WAIT_RETURN = "WAIT_RETURN";
    private static final String RETURNED = "RETURNED";
    private static final String REFUNDING = "REFUNDING";
    private static final String CLOSED = "CLOSED";
    private static final String CANCELED = "CANCELED";
    private static final String FLOW_PENDING = "PENDING";
    private static final String FLOW_APPROVED = "APPROVED";
    private static final String FLOW_REFUNDING = "REFUNDING";
    private static final String FLOW_COMPLETED = "COMPLETED";
    private static final String STEP_WAIT_RETURN = "WAIT_RETURN";
    private static final String STEP_EVENT_PENDING = "EVENT_PENDING";
    private static final String REFUND_ONLY = "REFUND_ONLY";
    private static final String RETURN_AND_REFUND = "RETURN_AND_REFUND";
    private static final String EXCHANGE = "EXCHANGE";
    private static final String COMPENSATION_REFUND = "COMPENSATION_REFUND";
    private static final String FULL = "FULL";
    private static final String PARTIAL = "PARTIAL";
    private static final List<String> ACTIVE_STATUSES = List.of(APPLYING, APPROVED, WAIT_RETURN, RETURNED, REFUNDING);

    private final AftersaleOrderRepository aftersaleOrderRepository;
    private final AftersaleOrderItemRepository aftersaleOrderItemRepository;
    private final AftersaleRefundFlowRepository refundFlowRepository;
    private final OrderClient orderClient;
    private final RefundApprovedOutbox refundApprovedOutbox;

    public AftersaleService(
        AftersaleOrderRepository aftersaleOrderRepository,
        AftersaleOrderItemRepository aftersaleOrderItemRepository,
        AftersaleRefundFlowRepository refundFlowRepository,
        OrderClient orderClient,
        RefundApprovedOutbox refundApprovedOutbox
    ) {
        this.aftersaleOrderRepository = aftersaleOrderRepository;
        this.aftersaleOrderItemRepository = aftersaleOrderItemRepository;
        this.refundFlowRepository = refundFlowRepository;
        this.orderClient = orderClient;
        this.refundApprovedOutbox = refundApprovedOutbox;
    }

    @Transactional
    public AftersaleResponse create(Long userId, CreateAftersaleRequest request) {
        aftersaleOrderRepository.findFirstByOrderIdAndAftersaleStatusIn(request.orderId(), ACTIVE_STATUSES)
            .ifPresent(item -> {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund request already exists");
            });
        RefundOrderContext context = orderClient.getRefundContext(request.orderId());
        List<ResolvedRefundItem> refundItems = resolveRefundItems(request, context);
        if (refundItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund items are required");
        }
        String refundScope = refundScope(request, refundItems, context);
        AftersaleOrderSnapshot order = orderClient.applyAftersale(request.orderId(), userId);

        LocalDateTime now = LocalDateTime.now();
        AftersaleOrder aftersale = new AftersaleOrder();
        aftersale.setAftersaleNo("AS" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now) + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        aftersale.setOrderId(order.id());
        aftersale.setUserId(userId);
        aftersale.setAftersaleType(normalizeAftersaleType(request.aftersaleType()));
        aftersale.setRefundScope(refundScope);
        aftersale.setAftersaleStatus(APPLYING);
        aftersale.setRefundAmount(refundItems.stream().mapToInt(ResolvedRefundItem::refundAmount).sum());
        aftersale.setIncludeFreight(Boolean.TRUE.equals(request.includeFreight()));
        aftersale.setReason(request.reason().trim());
        aftersale.setRefundReasonType(normalizeOptionalText(request.refundReasonType()));
        aftersale.setEvidenceImages(joinImages(request.evidenceImages()));
        aftersale.setReturnLogisticsCompany(normalizePlainText(request.returnLogisticsCompany()));
        aftersale.setReturnLogisticsNo(normalizePlainText(request.returnLogisticsNo()));
        aftersale.setApplyTime(now);
        aftersale.setCreatedAt(now);
        aftersale.setUpdatedAt(now);
        AftersaleOrder saved = aftersaleOrderRepository.save(aftersale);
        aftersaleOrderItemRepository.saveAll(refundItems.stream().map(item -> toEntity(saved, item, now)).toList());
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
        if (REFUNDED.equals(aftersale.getAftersaleStatus()) || REFUNDING.equals(aftersale.getAftersaleStatus()) || WAIT_RETURN.equals(aftersale.getAftersaleStatus())) {
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
        aftersale.setAuditTime(now);
        if (requiresReturn(aftersale)) {
            aftersale.setAftersaleStatus(WAIT_RETURN);
            aftersale.setUpdatedAt(now);
            aftersaleOrderRepository.save(aftersale);
            saveFlow(loadOrCreateFlow(aftersale), FLOW_APPROVED, STEP_WAIT_RETURN, 0, null);
            return toResponse(aftersale, approvedOrderSnapshot(orderContext, WAIT_RETURN, "PAID"));
        }
        startRefunding(aftersale, orderContext, now);
        return toResponse(aftersale, approvedOrderSnapshot(orderContext, REFUNDING, "PAID"));
    }

    @Transactional
    public AftersaleResponse confirmReturned(Long id) {
        AftersaleOrder aftersale = aftersaleOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found"));
        if (REFUNDED.equals(aftersale.getAftersaleStatus()) || REFUNDING.equals(aftersale.getAftersaleStatus())) {
            return toResponse(aftersale);
        }
        if (!WAIT_RETURN.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aftersale is not waiting for return");
        }
        RefundOrderContext orderContext = orderClient.getRefundContext(aftersale.getOrderId());
        if (!Boolean.TRUE.equals(orderContext.refundable())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not refundable");
        }
        LocalDateTime now = LocalDateTime.now();
        aftersale.setAftersaleStatus(RETURNED);
        aftersale.setUpdatedAt(now);
        aftersaleOrderRepository.save(aftersale);
        startRefunding(aftersale, orderContext, now);
        return toResponse(aftersale, approvedOrderSnapshot(orderContext, REFUNDING, "PAID"));
    }

    @Transactional
    public AftersaleResponse completeRefund(Long id) {
        AftersaleOrder aftersale = aftersaleOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found"));
        if (REFUNDED.equals(aftersale.getAftersaleStatus())) {
            return toResponse(aftersale);
        }
        if (!REFUNDING.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aftersale is not refunding");
        }
        AftersaleOrderSnapshot order = orderClient.completeAftersale(aftersale.getOrderId());
        LocalDateTime now = LocalDateTime.now();
        aftersale.setAftersaleStatus(REFUNDED);
        aftersale.setRefundTime(now);
        aftersale.setUpdatedAt(now);
        aftersaleOrderRepository.save(aftersale);
        saveFlow(loadOrCreateFlow(aftersale), FLOW_COMPLETED, "DONE", 0, null);
        return toResponse(aftersale, order);
    }

    @Transactional
    public AftersaleResponse reject(Long id, RejectAftersaleRequest request) {
        AftersaleOrder aftersale = aftersaleOrderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found"));
        if (REJECTED.equals(aftersale.getAftersaleStatus())) {
            return toResponse(aftersale);
        }
        if (REFUNDED.equals(aftersale.getAftersaleStatus()) || REFUNDING.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aftersale is already refunded");
        }
        if (!APPLYING.equals(aftersale.getAftersaleStatus()) && !WAIT_RETURN.equals(aftersale.getAftersaleStatus())) {
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

    @Transactional
    public AftersaleResponse cancel(Long userId, Long id) {
        AftersaleOrder aftersale = aftersaleOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found"));
        if (!aftersale.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found");
        }
        if (CANCELED.equals(aftersale.getAftersaleStatus())) {
            return toResponse(aftersale);
        }
        if (!APPLYING.equals(aftersale.getAftersaleStatus()) && !WAIT_RETURN.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aftersale cannot be canceled");
        }
        AftersaleOrderSnapshot order = orderClient.rejectAftersale(aftersale.getOrderId());
        LocalDateTime now = LocalDateTime.now();
        aftersale.setAftersaleStatus(CANCELED);
        aftersale.setUpdatedAt(now);
        aftersaleOrderRepository.save(aftersale);
        saveFlow(loadOrCreateFlow(aftersale), CANCELED, "DONE", 0, null);
        return toResponse(aftersale, order);
    }

    @Transactional
    public AftersaleResponse close(Long id) {
        AftersaleOrder aftersale = aftersaleOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found"));
        if (CLOSED.equals(aftersale.getAftersaleStatus())) {
            return toResponse(aftersale);
        }
        if (!REJECTED.equals(aftersale.getAftersaleStatus()) && !CANCELED.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only rejected or canceled aftersales can be closed");
        }
        LocalDateTime now = LocalDateTime.now();
        aftersale.setAftersaleStatus(CLOSED);
        aftersale.setUpdatedAt(now);
        aftersaleOrderRepository.save(aftersale);
        saveFlow(loadOrCreateFlow(aftersale), CLOSED, "DONE", 0, null);
        return toResponse(aftersale);
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
            aftersale.getRefundScope(),
            aftersale.getAftersaleStatus(),
            aftersaleOrderItemRepository.findByAftersaleIdOrderById(aftersale.getId()).stream()
                .map(this::toItemResponse)
                .toList(),
            aftersale.getRefundAmount(),
            PriceFormatter.formatCents(aftersale.getRefundAmount()),
            aftersale.getIncludeFreight(),
            aftersale.getReason(),
            aftersale.getRefundReasonType(),
            splitImages(aftersale.getEvidenceImages()),
            aftersale.getReturnLogisticsCompany(),
            aftersale.getReturnLogisticsNo(),
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

    private void startRefunding(AftersaleOrder aftersale, RefundOrderContext orderContext, LocalDateTime now) {
        aftersale.setAftersaleStatus(REFUNDING);
        aftersale.setUpdatedAt(now);
        aftersaleOrderRepository.save(aftersale);
        List<AftersaleOrderItem> items = aftersaleOrderItemRepository.findByAftersaleIdOrderById(aftersale.getId());
        refundApprovedOutbox.append(aftersale, orderContext, items, now);
        saveFlow(loadOrCreateFlow(aftersale), FLOW_REFUNDING, STEP_EVENT_PENDING, 0, null);
    }

    private boolean requiresReturn(AftersaleOrder aftersale) {
        return RETURN_AND_REFUND.equals(aftersale.getAftersaleType()) || EXCHANGE.equals(aftersale.getAftersaleType());
    }

    private AftersaleOrderSnapshot approvedOrderSnapshot(RefundOrderContext context, String aftersaleStatus, String payStatus) {
        return new AftersaleOrderSnapshot(
            context.orderId(), context.orderNo(), context.userId(), null, context.orderStatus(), payStatus, aftersaleStatus,
            context.payAmount(), context.refundable()
        );
    }

    private List<ResolvedRefundItem> resolveRefundItems(CreateAftersaleRequest request, RefundOrderContext context) {
        if (!Boolean.TRUE.equals(context.refundable())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not refundable");
        }
        List<RefundOrderItemSnapshot> sourceItems = context.items() == null ? List.of() : context.items();
        List<RequestedRefundItem> requestedItems = request.refundItems() == null || request.refundItems().isEmpty()
            ? sourceItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.supportRefund()) && positive(item.refundableQuantity()) > 0)
                .map(item -> new RequestedRefundItem(item.skuId(), positive(item.refundableQuantity())))
                .toList()
            : request.refundItems().stream()
                .map(item -> new RequestedRefundItem(item.skuId(), item.quantity()))
                .toList();
        int grossPayAmount = sourceItems.stream().mapToInt(item -> positive(item.payAmount())).sum();
        int orderPayAmount = positive(context.payAmount());
        return requestedItems.stream().map(requested -> {
            RefundOrderItemSnapshot source = sourceItems.stream()
                .filter(item -> item.skuId().equals(requested.skuId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund SKU is not in order"));
            if (!Boolean.TRUE.equals(source.supportRefund())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund SKU is not refundable");
            }
            int quantity = positive(requested.quantity());
            int refundableQuantity = positive(source.refundableQuantity());
            if (quantity <= 0 || quantity > refundableQuantity) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund quantity exceeds refundable quantity");
            }
            int itemGrossAmount = positive(source.payAmount()) * quantity / Math.max(positive(source.quantity()), 1);
            int refundAmount = grossPayAmount <= 0 ? 0 : itemGrossAmount * orderPayAmount / grossPayAmount;
            return new ResolvedRefundItem(source.productId(), source.skuId(), quantity, refundAmount);
        }).toList();
    }

    private String refundScope(CreateAftersaleRequest request, List<ResolvedRefundItem> refundItems, RefundOrderContext context) {
        String requested = normalizeOptionalText(request.refundScope());
        if (FULL.equals(requested) || PARTIAL.equals(requested)) {
            return requested;
        }
        int requestedQuantity = refundItems.stream().mapToInt(ResolvedRefundItem::quantity).sum();
        int refundableQuantity = context.items() == null ? 0 : context.items().stream().mapToInt(item -> positive(item.refundableQuantity())).sum();
        return requestedQuantity >= refundableQuantity ? FULL : PARTIAL;
    }

    private AftersaleOrderItem toEntity(AftersaleOrder aftersale, ResolvedRefundItem item, LocalDateTime now) {
        AftersaleOrderItem entity = new AftersaleOrderItem();
        entity.setAftersaleId(aftersale.getId());
        entity.setOrderId(aftersale.getOrderId());
        entity.setProductId(item.productId());
        entity.setSkuId(item.skuId());
        entity.setQuantity(item.quantity());
        entity.setRefundAmount(item.refundAmount());
        entity.setCreatedAt(now);
        return entity;
    }

    private AftersaleItemResponse toItemResponse(AftersaleOrderItem item) {
        return new AftersaleItemResponse(
            item.getSkuId(),
            item.getProductId(),
            item.getQuantity(),
            item.getRefundAmount(),
            PriceFormatter.formatCents(item.getRefundAmount())
        );
    }

    private String normalizeAftersaleType(String type) {
        String normalized = normalizeOptionalText(type);
        if (RETURN_AND_REFUND.equals(normalized) || EXCHANGE.equals(normalized) || COMPENSATION_REFUND.equals(normalized)) {
            return normalized;
        }
        return REFUND_ONLY;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

    private String normalizePlainText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String joinImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .limit(9)
            .reduce((left, right) -> left + "," + right)
            .orElse(null);
    }

    private List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(images.split(","))
            .filter(item -> !item.isBlank())
            .toList();
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private record RequestedRefundItem(Long skuId, Integer quantity) {
    }

    private record ResolvedRefundItem(Long productId, Long skuId, Integer quantity, Integer refundAmount) {
    }
}
