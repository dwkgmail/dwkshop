package com.dwkshop.backend.aftersale;

import com.dwkshop.backend.admin.AdminOperationLogService;
import com.dwkshop.backend.aftersale.dto.AftersaleResponse;
import com.dwkshop.backend.aftersale.dto.CreateAftersaleRequest;
import com.dwkshop.backend.aftersale.dto.RejectAftersaleRequest;
import com.dwkshop.backend.domain.entity.AftersaleOrder;
import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.entity.TradeOrder;
import com.dwkshop.backend.domain.entity.TradeOrderItem;
import com.dwkshop.backend.domain.repository.AftersaleOrderRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.domain.repository.TradeOrderItemRepository;
import com.dwkshop.backend.domain.repository.TradeOrderRepository;
import com.dwkshop.backend.product.PriceFormatter;
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

    private final AftersaleOrderRepository aftersaleOrderRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderItemRepository tradeOrderItemRepository;
    private final ProductSkuRepository productSkuRepository;
    private final AdminOperationLogService operationLogService;

    public AftersaleService(
        AftersaleOrderRepository aftersaleOrderRepository,
        TradeOrderRepository tradeOrderRepository,
        TradeOrderItemRepository tradeOrderItemRepository,
        ProductSkuRepository productSkuRepository,
        AdminOperationLogService operationLogService
    ) {
        this.aftersaleOrderRepository = aftersaleOrderRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderItemRepository = tradeOrderItemRepository;
        this.productSkuRepository = productSkuRepository;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public AftersaleResponse create(Long userId, CreateAftersaleRequest request) {
        TradeOrder order = tradeOrderRepository.findByIdAndUserId(request.orderId(), userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!"PAID".equals(order.getPayStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only paid orders can request refund");
        }
        if (REFUNDED.equals(order.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order already refunded");
        }
        aftersaleOrderRepository.findFirstByOrderIdAndAftersaleStatusIn(order.getId(), List.of(APPLYING, REFUNDED))
            .ifPresent(item -> {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund request already exists");
            });
        List<TradeOrderItem> items = tradeOrderItemRepository.findByOrderId(order.getId());
        if (items.isEmpty() || items.stream().anyMatch(item -> !Boolean.TRUE.equals(item.getSupportRefund()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order does not support refund");
        }
        int refundAmount = items.stream()
            .mapToInt(item -> positive(item.getRefundableAmount()))
            .sum();
        if (refundAmount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no refundable amount");
        }

        LocalDateTime now = LocalDateTime.now();
        AftersaleOrder aftersale = new AftersaleOrder();
        aftersale.setAftersaleNo("AS" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now) + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        aftersale.setOrderId(order.getId());
        aftersale.setUserId(userId);
        aftersale.setAftersaleType("REFUND");
        aftersale.setAftersaleStatus(APPLYING);
        aftersale.setRefundAmount(refundAmount);
        aftersale.setReason(request.reason().trim());
        aftersale.setApplyTime(now);
        aftersale.setCreatedAt(now);
        aftersale.setUpdatedAt(now);
        AftersaleOrder saved = aftersaleOrderRepository.save(aftersale);

        order.setAftersaleStatus(APPLYING);
        order.setUpdatedAt(now);
        tradeOrderRepository.save(order);
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
        if (!APPLYING.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aftersale is not applying");
        }
        TradeOrder order = tradeOrderRepository.findById(aftersale.getOrderId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        LocalDateTime now = LocalDateTime.now();
        refundOrderStock(order);
        List<TradeOrderItem> items = tradeOrderItemRepository.findByOrderId(order.getId());
        for (TradeOrderItem item : items) {
            item.setAftersaleQuantity(item.getQuantity());
            item.setRefundedQuantity(item.getQuantity());
            item.setRefundableQuantity(0);
            item.setRefundAmount(item.getRefundAmount() + positive(item.getRefundableAmount()));
            item.setRefundableAmount(0);
            item.setRefundStatus("REFUNDED");
            tradeOrderItemRepository.save(item);
        }

        order.setPayStatus(REFUNDED);
        order.setAftersaleStatus(REFUNDED);
        order.setUpdatedAt(now);
        tradeOrderRepository.save(order);

        aftersale.setAftersaleStatus(REFUNDED);
        aftersale.setAuditTime(now);
        aftersale.setRefundTime(now);
        aftersale.setUpdatedAt(now);
        aftersaleOrderRepository.save(aftersale);
        operationLogService.record("AFTERSALE", "APPROVE", "AFTERSALE", id, "售后通过：" + aftersale.getAftersaleNo());
        return toResponse(aftersale, order);
    }

    @Transactional
    public AftersaleResponse reject(Long id, RejectAftersaleRequest request) {
        AftersaleOrder aftersale = aftersaleOrderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aftersale not found"));
        if (!APPLYING.equals(aftersale.getAftersaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aftersale is not applying");
        }
        TradeOrder order = tradeOrderRepository.findById(aftersale.getOrderId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        LocalDateTime now = LocalDateTime.now();
        aftersale.setAftersaleStatus(REJECTED);
        aftersale.setRejectReason(request == null || request.rejectReason() == null || request.rejectReason().isBlank() ? "Rejected by admin" : request.rejectReason().trim());
        aftersale.setAuditTime(now);
        aftersale.setUpdatedAt(now);
        aftersaleOrderRepository.save(aftersale);

        order.setAftersaleStatus(REJECTED);
        order.setUpdatedAt(now);
        tradeOrderRepository.save(order);
        operationLogService.record("AFTERSALE", "REJECT", "AFTERSALE", id, "售后拒绝：" + aftersale.getRejectReason());
        return toResponse(aftersale, order);
    }

    private void refundOrderStock(TradeOrder order) {
        if (!"WAIT_SHIP".equals(order.getOrderStatus())) {
            return;
        }
        for (TradeOrderItem item : tradeOrderItemRepository.findByOrderId(order.getId())) {
            ProductSku sku = productSkuRepository.findByIdForUpdate(item.getSkuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sku not found"));
            sku.setLockedStock(Math.max(0, sku.getLockedStock() - item.getQuantity()));
            sku.setStock(sku.getStock() + item.getQuantity());
            sku.setUpdatedAt(LocalDateTime.now());
            productSkuRepository.save(sku);
        }
    }

    private AftersaleResponse toResponse(AftersaleOrder aftersale) {
        TradeOrder order = tradeOrderRepository.findById(aftersale.getOrderId()).orElse(null);
        return toResponse(aftersale, order);
    }

    private AftersaleResponse toResponse(AftersaleOrder aftersale, TradeOrder order) {
        return new AftersaleResponse(
            aftersale.getId(),
            aftersale.getAftersaleNo(),
            aftersale.getOrderId(),
            order == null ? null : order.getOrderNo(),
            aftersale.getUserId(),
            order == null ? null : order.getReceiverMobile(),
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

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }
}
