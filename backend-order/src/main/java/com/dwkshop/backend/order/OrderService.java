package com.dwkshop.backend.order;

import com.dwkshop.backend.domain.entity.PaymentOrder;
import com.dwkshop.backend.domain.entity.PaymentTransaction;
import com.dwkshop.backend.domain.entity.TradeOrder;
import com.dwkshop.backend.domain.entity.TradeOrderAmount;
import com.dwkshop.backend.domain.entity.TradeOrderItem;
import com.dwkshop.backend.domain.repository.OrderOutboxEventRepository;
import com.dwkshop.backend.audit.AdminOperationLogService;
import com.dwkshop.backend.domain.repository.PaymentOrderRepository;
import com.dwkshop.backend.domain.repository.PaymentTransactionRepository;
import com.dwkshop.backend.domain.repository.TradeOrderAmountRepository;
import com.dwkshop.backend.domain.repository.TradeOrderItemRepository;
import com.dwkshop.backend.domain.repository.TradeOrderRepository;
import com.dwkshop.backend.event.RefundApprovedEvent;
import com.dwkshop.backend.event.InventoryIntegrationEvent;
import com.dwkshop.backend.order.dto.AdminShipOrderRequest;
import com.dwkshop.backend.order.dto.AdminUpdateDeliveryStatusRequest;
import com.dwkshop.backend.order.dto.ConfirmCouponResponse;
import com.dwkshop.backend.order.dto.ConfirmOrderItemResponse;
import com.dwkshop.backend.order.dto.ConfirmOrderRequest;
import com.dwkshop.backend.order.dto.ConfirmOrderResponse;
import com.dwkshop.backend.order.dto.CreateOrderRequest;
import com.dwkshop.backend.order.dto.OrderAddressResponse;
import com.dwkshop.backend.order.dto.OrderAmountResponse;
import com.dwkshop.backend.order.dto.OrderItemResponse;
import com.dwkshop.backend.order.dto.OrderResponse;
import com.dwkshop.backend.order.dto.OrderSummaryResponse;
import com.dwkshop.backend.order.dto.PaymentCallbackRequest;
import com.dwkshop.backend.order.dto.PaymentCallbackResponse;
import com.dwkshop.backend.order.dto.PointDeductionResponse;
import com.dwkshop.backend.order.dto.PromotionShareResponse;
import com.dwkshop.backend.order.dto.PromotionTraceItemResponse;
import com.dwkshop.backend.order.dto.PromotionTraceResponse;
import com.dwkshop.backend.util.PriceFormatter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    private static final String CART = "CART";
    private static final String BUY_NOW = "BUY_NOW";
    private static final String ON_SALE = "ON_SALE";
    private static final String ENABLED = "ENABLED";
    private static final int NORMAL_FREIGHT = 0;
    private static final int COLD_CHAIN_FREIGHT = 1000;
    private static final int POINT_EXCHANGE_RATE = 100;

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderItemRepository tradeOrderItemRepository;
    private final TradeOrderAmountRepository tradeOrderAmountRepository;
    private final OrderOutboxEventRepository orderOutboxEventRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SettlementSessionStore settlementSessionStore;
    private final CartClient cartClient;
    private final MemberClient memberClient;
    private final MarketingClient marketingClient;
    private final ProductCatalogClient productCatalogClient;
    private final OrderInventoryOutbox inventoryOutbox;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Duration settlementTtl;
    private final int paymentTimeoutCloseBatchSize;

    public OrderService(
        TradeOrderRepository tradeOrderRepository,
        TradeOrderItemRepository tradeOrderItemRepository,
        TradeOrderAmountRepository tradeOrderAmountRepository,
        OrderOutboxEventRepository orderOutboxEventRepository,
        PaymentOrderRepository paymentOrderRepository,
        PaymentTransactionRepository paymentTransactionRepository,
        SettlementSessionStore settlementSessionStore,
        CartClient cartClient,
        MemberClient memberClient,
        MarketingClient marketingClient,
        ProductCatalogClient productCatalogClient,
        OrderInventoryOutbox inventoryOutbox,
        TransactionTemplate transactionTemplate,
        ObjectMapper objectMapper,
        @Value("${dwkshop.order.settlement-ttl-minutes:30}") long settlementTtlMinutes,
        @Value("${dwkshop.order.payment-timeout-close-batch-size:100}") int paymentTimeoutCloseBatchSize
    ) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderItemRepository = tradeOrderItemRepository;
        this.tradeOrderAmountRepository = tradeOrderAmountRepository;
        this.orderOutboxEventRepository = orderOutboxEventRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.settlementSessionStore = settlementSessionStore;
        this.cartClient = cartClient;
        this.memberClient = memberClient;
        this.marketingClient = marketingClient;
        this.productCatalogClient = productCatalogClient;
        this.inventoryOutbox = inventoryOutbox;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.settlementTtl = Duration.ofMinutes(settlementTtlMinutes);
        this.paymentTimeoutCloseBatchSize = Math.max(paymentTimeoutCloseBatchSize, 1);
    }

    @Transactional(readOnly = true)
    public ConfirmOrderResponse confirm(Long userId, ConfirmOrderRequest request) {
        // 提交订单前先完成一次结算，后续创建订单必须携带本次结算 token。
        SettlementCalculation calculation = calculate(userId, request);
        String token = "SETTLE-" + UUID.randomUUID();
        ConfirmOrderResponse response = toConfirmResponse(token, calculation, request.remark());
        // 在内存中暂存本次结算快照，用于校验金额并拦截重复提交。
        settlementSessionStore.save(token, new SettlementSession(userId, request, response.amount().payAmount(), toSnapshot(userId, request, calculation)), settlementTtl);
        return response;
    }

    public OrderResponse create(Long userId, CreateOrderRequest request) {
        String clientRequestId = normalizeClientRequestId(request.clientRequestId());
        if (clientRequestId != null) {
            OrderResponse existingOrder = findIdempotentOrder(userId, clientRequestId);
            if (existingOrder != null) {
                return existingOrder;
            }
        }
        SettlementSession session = settlementSessionStore.consume(request.settlementToken())
            .orElse(null);
        if (session == null || !session.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单信息已过期，请重新确认");
        }
        synchronized (session) {
            // 同一个结算会话只允许消费一次，避免重复点击造成重复下单。
            if (session.used()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单已创建，请勿重复提交");
            }
            // 创建订单使用确认页生成的短期价格快照，订单一旦创建即锁价。
            SettlementCalculation calculation = calculationFromSnapshot(session);
            if (!session.expectedPayAmount().equals(request.expectedPayAmount())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单金额已变化，请重新确认");
            }
            String couponLockKey = couponLockKey(request.settlementToken(), clientRequestId);
            boolean couponLocked = false;
            OrderResponse response;
            try {
                if (calculation.selectedUserCouponId() != null) {
                    marketingClient.lockCoupon(userId, calculation.selectedUserCouponId(), couponLockKey, calculation.amount().productAmount());
                    couponLocked = true;
                }
                response = transactionTemplate.execute(status ->
                    persistOrder(userId, session.request(), calculation, request.remark(), clientRequestId)
                );
            } catch (RuntimeException ex) {
                if (couponLocked) {
                    releaseCouponQuietly(userId, calculation.selectedUserCouponId(), null);
                }
                throw ex;
            }
            deleteCartItemsAfterCreate(userId, calculation);
            return response;
        }
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> listOrders(Long userId) {
        return tradeOrderRepository.findByUserIdOrderByIdDesc(userId).stream()
            .map(this::toOrderSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> listAdminOrders() {
        return tradeOrderRepository.findAllByOrderByIdDesc().stream()
            .map(this::toOrderSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {
        TradeOrder order = tradeOrderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getAdminOrder(Long orderId) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        return toOrderResponse(order);
    }

    public OrderResponse cancel(Long userId, Long orderId) {
        OrderTransition transition = transactionTemplate.execute(status -> {
            TradeOrder order = tradeOrderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
            LocalDateTime now = LocalDateTime.now();
            OrderStateMachine.cancelUnpaid(order, now);
            tradeOrderRepository.save(order);
            inventoryOutbox.append(order, tradeOrderItemRepository.findByOrderId(orderId),
                InventoryIntegrationEvent.ORDER_CANCELLED, 2, now);
            return new OrderTransition(toOrderResponse(order), order.getCouponUserId(), pointDiscountPoints(order), false);
        });
        releaseCouponIfPresent(userId, transition.couponUserId(), orderId);
        releasePointsIfPresent(userId, orderId, transition.pointAmount());
        return transition.response();
    }

    public OrderResponse pay(Long userId, Long orderId) {
        OrderTransition transition = transactionTemplate.execute(status -> {
            TradeOrder order = tradeOrderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
            if (OrderStateMachine.PAY_PAID.equals(order.getPayStatus())) {
                return new OrderTransition(toOrderResponse(order), order.getCouponUserId(), 0, false);
            }
            LocalDateTime now = LocalDateTime.now();
            if (isPaymentExpired(order, now)) {
                expirePayment(order, now);
                return new OrderTransition(null, order.getCouponUserId(), pointDiscountPoints(order), true);
            }
            OrderStateMachine.pay(order, now);
            tradeOrderRepository.save(order);
            inventoryOutbox.append(order, tradeOrderItemRepository.findByOrderId(orderId),
                InventoryIntegrationEvent.PAYMENT_SUCCEEDED, 2, now);
            return new OrderTransition(toOrderResponse(order), order.getCouponUserId(), pointDiscountPoints(order), false);
        });
        if (transition.expired()) {
            releaseCouponIfPresent(userId, transition.couponUserId(), orderId);
            releasePointsIfPresent(userId, orderId, transition.pointAmount());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单支付已超时");
        }
        deductPointsIfPresent(userId, orderId, transition.pointAmount());
        useCouponIfPresent(userId, transition.couponUserId(), orderId);
        return transition.response();
    }

    public int closeExpiredUnpaidOrders() {
        return closeExpiredUnpaidOrders(LocalDateTime.now(), paymentTimeoutCloseBatchSize);
    }

    public int closeExpiredUnpaidOrders(LocalDateTime now, int batchSize) {
        List<Long> orderIds = tradeOrderRepository.findExpiredUnpaidOrderIds(
            OrderStateMachine.ORDER_WAIT_PAY,
            OrderStateMachine.PAY_UNPAID,
            now,
            PageRequest.of(0, Math.max(batchSize, 1))
        );
        int closed = 0;
        for (Long orderId : orderIds) {
            ExpiredOrderTransition transition = transactionTemplate.execute(status -> {
                TradeOrder order = tradeOrderRepository.findById(orderId).orElse(null);
                if (order == null || !isPaymentExpired(order, now)) {
                    return ExpiredOrderTransition.unchanged();
                }
                expirePayment(order, now);
                return ExpiredOrderTransition.closed(
                    order.getUserId(), order.getId(), order.getCouponUserId(), pointDiscountPoints(order));
            });
            if (transition.closed()) {
                releaseCouponIfPresent(transition.userId(), transition.couponUserId(), transition.orderId());
                releasePointsIfPresent(transition.userId(), transition.orderId(), transition.pointAmount());
                closed++;
            }
        }
        return closed;
    }

    public PaymentCallbackResponse handlePaymentCallback(PaymentCallbackRequest request) {
        PaymentCallbackTransition transition;
        try {
            transition = transactionTemplate.execute(status -> applyPaymentCallback(request));
        } catch (DataIntegrityViolationException ex) {
            if (isSuccessfulCallbackRecorded(request.channelTradeNo())) {
                return PaymentCallbackResponse.duplicated();
            }
            throw ex;
        }
        if (transition.duplicate()) {
            return PaymentCallbackResponse.duplicated();
        }
        deductPointsIfPresent(transition.userId(), transition.orderId(), transition.pointAmount());
        useCouponIfPresent(transition.userId(), transition.couponUserId(), transition.orderId());
        return PaymentCallbackResponse.processed();
    }

    private PaymentCallbackTransition applyPaymentCallback(PaymentCallbackRequest request) {
        String channelTradeNo = normalizeRequiredText(request.channelTradeNo(), "channelTradeNo不能为空");
        if (isSuccessfulCallbackRecorded(channelTradeNo)) {
            return PaymentCallbackTransition.duplicated();
        }

        TradeOrder order = resolvePaymentCallbackOrder(request);
        if (!order.getPayAmount().equals(request.amount())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付金额与订单应付金额不一致");
        }
        LocalDateTime now = LocalDateTime.now();
        if (isPaymentExpired(order, now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单已超过可支付时间");
        }
        if (!OrderStateMachine.ORDER_WAIT_PAY.equals(order.getOrderStatus())
            || !OrderStateMachine.PAY_UNPAID.equals(order.getPayStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单当前状态不可支付");
        }

        PaymentOrder paymentOrder = resolvePaymentOrder(request, order, now);
        LocalDateTime paidAt = request.paidAt() == null ? now : request.paidAt();
        OrderStateMachine.pay(order, paidAt);
        tradeOrderRepository.save(order);
        inventoryOutbox.append(order, tradeOrderItemRepository.findByOrderId(order.getId()),
            InventoryIntegrationEvent.PAYMENT_SUCCEEDED, 2, paidAt);

        paymentOrder.setStatus("PAID");
        paymentOrder.setAmount(request.amount());
        paymentOrder.setChannelTradeNo(channelTradeNo);
        paymentOrder.setPaidAt(paidAt);
        paymentOrder.setCallbackPayload(request.callbackPayload());
        paymentOrder.setUpdatedAt(now);
        paymentOrderRepository.save(paymentOrder);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPaymentNo(paymentOrder.getPaymentNo());
        transaction.setOrderId(order.getId());
        transaction.setUserId(order.getUserId());
        transaction.setChannel(normalizeOptionalText(request.channel()) == null ? "UNKNOWN" : normalizeOptionalText(request.channel()));
        transaction.setAmount(request.amount());
        transaction.setStatus("SUCCESS");
        transaction.setRequestNo(callbackRequestNo(channelTradeNo));
        transaction.setChannelTradeNo(channelTradeNo);
        transaction.setPaidAt(paidAt);
        transaction.setCallbackPayload(request.callbackPayload());
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);
        paymentTransactionRepository.saveAndFlush(transaction);

        return PaymentCallbackTransition.processed(
            order.getUserId(), order.getId(), order.getCouponUserId(), pointDiscountPoints(order));
    }

    private boolean isSuccessfulCallbackRecorded(String channelTradeNo) {
        String normalized = normalizeOptionalText(channelTradeNo);
        if (normalized == null) {
            return false;
        }
        return paymentTransactionRepository.findByChannelTradeNo(normalized)
            .filter(transaction -> "SUCCESS".equals(transaction.getStatus()))
            .isPresent();
    }

    private TradeOrder resolvePaymentCallbackOrder(PaymentCallbackRequest request) {
        String paymentNo = normalizeOptionalText(request.paymentNo());
        if (paymentNo != null) {
            PaymentOrder paymentOrder = paymentOrderRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "支付单不存在"));
            return tradeOrderRepository.findById(paymentOrder.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        }
        if (request.orderId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId或paymentNo不能为空");
        }
        return tradeOrderRepository.findById(request.orderId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
    }

    private PaymentOrder resolvePaymentOrder(PaymentCallbackRequest request, TradeOrder order, LocalDateTime now) {
        String paymentNo = normalizeOptionalText(request.paymentNo());
        PaymentOrder existing = paymentNo == null
            ? paymentOrderRepository.findByOrderId(order.getId()).orElse(null)
            : paymentOrderRepository.findByPaymentNo(paymentNo).orElse(null);
        if (existing != null) {
            if (!existing.getOrderId().equals(order.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付单与订单不匹配");
            }
            return existing;
        }
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentNo(paymentNo == null ? "PAY-" + order.getOrderNo() : paymentNo);
        paymentOrder.setOrderId(order.getId());
        paymentOrder.setUserId(order.getUserId());
        paymentOrder.setChannel(normalizeOptionalText(request.channel()) == null ? "UNKNOWN" : normalizeOptionalText(request.channel()));
        paymentOrder.setAmount(request.amount());
        paymentOrder.setStatus("INIT");
        paymentOrder.setRequestNo(callbackRequestNo(normalizeRequiredText(request.channelTradeNo(), "channelTradeNo不能为空")));
        paymentOrder.setCreatedAt(now);
        paymentOrder.setUpdatedAt(now);
        return paymentOrderRepository.save(paymentOrder);
    }

    @Transactional
    public OrderResponse shipOrder(Long orderId, AdminShipOrderRequest request) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        LocalDateTime now = LocalDateTime.now();
        OrderStateMachine.ship(order, now);
        order.setLogisticsCompany(normalizeOptionalText(request.logisticsCompany()));
        order.setLogisticsNo(normalizeOptionalText(request.logisticsNo()));
        order.setDeliveryRemark(normalizeOptionalText(request.deliveryRemark()));
        tradeOrderRepository.save(order);
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse updateDeliveryStatus(Long orderId, AdminUpdateDeliveryStatusRequest request) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        String targetStatus = normalizeDeliveryStatus(request.deliveryStatus());
        LocalDateTime now = LocalDateTime.now();
        OrderStateMachine.updateDelivery(order, targetStatus, now);
        order.setDeliveryRemark(normalizeOptionalText(request.deliveryRemark()));
        tradeOrderRepository.save(order);
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public AftersaleOrderSnapshot getAftersaleSnapshot(Long orderId) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        return toAftersaleSnapshot(order);
    }

    @Transactional
    public AftersaleOrderSnapshot applyAftersale(Long orderId, Long userId) {
        TradeOrder order = tradeOrderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        List<TradeOrderItem> items = tradeOrderItemRepository.findByOrderId(orderId);
        if (items.isEmpty() || items.stream().anyMatch(item -> !Boolean.TRUE.equals(item.getSupportRefund()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单不支持退款");
        }
        OrderStateMachine.applyAftersale(order, LocalDateTime.now());
        return toAftersaleSnapshot(tradeOrderRepository.save(order), items);
    }

    @Transactional
    public AftersaleOrderSnapshot approveAftersale(Long orderId) {
        return completeAftersale(orderId);
    }

    @Transactional
    public AftersaleOrderSnapshot rejectAftersale(Long orderId) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        OrderStateMachine.rejectAftersale(order, LocalDateTime.now());
        return toAftersaleSnapshot(tradeOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public RefundOrderContext getRefundContext(Long orderId) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        List<TradeOrderItem> items = tradeOrderItemRepository.findByOrderId(orderId);
        boolean refundable = !items.isEmpty()
            && items.stream().anyMatch(item -> Boolean.TRUE.equals(item.getSupportRefund()) && item.getRefundableQuantity() > 0);
        return new RefundOrderContext(
            order.getId(),
            order.getOrderNo(),
            order.getUserId(),
            order.getOrderStatus(),
            order.getPayStatus(),
            order.getDeliveryStatus(),
            order.getAftersaleStatus(),
            order.getPayAmount(),
            refundable,
            items.stream()
                .map(item -> new RefundOrderItemSnapshot(
                    item.getSkuId(),
                    item.getProductId(),
                    item.getQuantity(),
                    item.getRefundableQuantity(),
                    item.getRefundedQuantity(),
                    item.getAftersaleQuantity(),
                    item.getPayAmount(),
                    item.getPayAmount(),
                    item.getCouponShareAmount(),
                    item.getPointShareAmount(),
                    item.getFreightShareAmount(),
                    item.getRefundAmount(),
                    item.getRefundAmount(),
                    item.getRefundableAmount(),
                    item.getRefundStatus(),
                    item.getSupportRefund()
                ))
                .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<InventoryOrderSummary> getInventoryOrderSummaries(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = new LinkedHashSet<>(orderIds).stream().toList();
        return tradeOrderRepository.findByIdIn(distinctIds).stream()
            .map(order -> new InventoryOrderSummary(order.getId(), order.getOrderNo(), order.getOrderStatus()))
            .toList();
    }

    @Transactional(readOnly = true)
    public InventoryOrderHealth getInventoryOrderHealth(int pendingMinutes) {
        int normalizedMinutes = Math.max(pendingMinutes, 1);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(normalizedMinutes);
        long pendingOutboxBacklog = orderOutboxEventRepository.countByPublishStatusAndCreatedAtBefore("PENDING", cutoff);
        List<Long> staleWaitPayOrderIds = tradeOrderRepository.findStaleOrderIdsByStatus(
            "WAIT_PAY",
            cutoff,
            PageRequest.of(0, 100)
        );
        return new InventoryOrderHealth(pendingOutboxBacklog, staleWaitPayOrderIds);
    }

    @Transactional
    public AftersaleOrderSnapshot completeAftersale(Long orderId) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (OrderStateMachine.AFTERSALE_REFUNDED.equals(order.getAftersaleStatus())) {
            return toAftersaleSnapshot(order);
        }
        List<TradeOrderItem> items = tradeOrderItemRepository.findByOrderId(orderId);
        for (TradeOrderItem item : items) {
            applyRefund(item, item.getRefundableQuantity(), item.getRefundableAmount());
        }
        tradeOrderItemRepository.saveAll(items);
        OrderStateMachine.completeAftersale(order, LocalDateTime.now());
        TradeOrder savedOrder = tradeOrderRepository.save(order);
        refundCouponAfterCommit(savedOrder.getUserId(), savedOrder.getCouponUserId(), savedOrder.getId(), true);
        refundPointsAfterCommit(savedOrder.getUserId(), savedOrder.getId(), pointDiscountPoints(savedOrder), true);
        return toAftersaleSnapshot(savedOrder, items);
    }

    @Transactional
    public AftersaleOrderSnapshot completeAftersale(RefundApprovedEvent event) {
        TradeOrder order = tradeOrderRepository.findById(event.orderId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (OrderStateMachine.AFTERSALE_REFUNDED.equals(order.getAftersaleStatus())) {
            return toAftersaleSnapshot(order);
        }
        List<TradeOrderItem> items = tradeOrderItemRepository.findByOrderId(event.orderId());
        List<RefundApprovedEvent.RefundItem> refundItems = event.items() == null ? List.of() : event.items();
        if (refundItems.isEmpty()) {
            for (TradeOrderItem item : items) {
                applyRefund(item, item.getRefundableQuantity(), item.getRefundableAmount());
            }
        } else {
            for (RefundApprovedEvent.RefundItem refundItem : refundItems) {
                TradeOrderItem item = items.stream()
                    .filter(candidate -> candidate.getSkuId().equals(refundItem.skuId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "退款商品不属于订单"));
                applyRefund(item, refundItem.quantity(), refundItem.refundAmount());
            }
        }
        tradeOrderItemRepository.saveAll(items);
        boolean fullRefund = items.stream().allMatch(item -> item.getRefundedQuantity() >= item.getQuantity());
        OrderStateMachine.completeAftersale(order, LocalDateTime.now(), fullRefund);
        TradeOrder savedOrder = tradeOrderRepository.save(order);
        refundCouponAfterCommit(savedOrder.getUserId(), savedOrder.getCouponUserId(), savedOrder.getId(), fullRefund);
        refundPointsAfterCommit(savedOrder.getUserId(), savedOrder.getId(), pointDiscountPoints(savedOrder), fullRefund);
        return toAftersaleSnapshot(savedOrder, items);
    }

    private AftersaleOrderSnapshot toAftersaleSnapshot(TradeOrder order) {
        return toAftersaleSnapshot(order, tradeOrderItemRepository.findByOrderId(order.getId()));
    }

    private AftersaleOrderSnapshot toAftersaleSnapshot(TradeOrder order, List<TradeOrderItem> items) {
        boolean refundable = !items.isEmpty()
            && items.stream().anyMatch(item -> Boolean.TRUE.equals(item.getSupportRefund()) && item.getRefundableQuantity() > 0);
        return new AftersaleOrderSnapshot(
            order.getId(),
            order.getOrderNo(),
            order.getUserId(),
            order.getReceiverMobile(),
            order.getOrderStatus(),
            order.getPayStatus(),
            order.getAftersaleStatus(),
            order.getPayAmount(),
            refundable
        );
    }

    private SettlementCalculation calculate(Long userId, ConfirmOrderRequest request) {
        String sourceType = normalizeSourceType(request.sourceType());
        // 先把购物车/立即购买两种入口统一整理成结算项，后续金额逻辑都基于它。
        List<SettlementItem> items = resolveItems(userId, sourceType, request);
        validateItems(items);
        MemberAddress address = resolveAddress(userId, request.addressId());
        int productAmount = items.stream().mapToInt(SettlementItem::totalAmount).sum();
        int productDiscountAmount = 0;
        int freightAmount = calculateFreight(items);
        CouponSelection couponSelection = selectCoupon(userId, request.couponUserId(), productAmount);
        PointSelection pointSelection = selectPoints(userId, request.usePoints(), items, productAmount - productDiscountAmount - couponSelection.discountAmount());
        int payAmount = productAmount - productDiscountAmount - couponSelection.discountAmount() - pointSelection.deductionAmount() + freightAmount;
        payAmount = Math.max(payAmount, 0);
        List<PromotionTraceResponse> promotionTraces = buildPromotionTraces(userId, items, couponSelection, pointSelection);
        OrderAmountResponse amount = toAmount(productAmount, productDiscountAmount, couponSelection.discountAmount(), pointSelection.deductionAmount(), freightAmount, 0, payAmount, promotionTraces, writePromotionJson(promotionTraces));
        return new SettlementCalculation(sourceType, address, items, couponSelection.selectedUserCouponId(), couponSelection.availableCoupons(), pointSelection, amount);
    }

    private List<SettlementItem> resolveItems(Long userId, String sourceType, ConfirmOrderRequest request) {
        if (CART.equals(sourceType)) {
            List<CartItemSnapshot> cartItems = cartClient.listItems(userId, request.cartItemIds());
            if (cartItems.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要结算的商品");
            }
            if (request.cartItemIds() != null && !request.cartItemIds().isEmpty() && cartItems.size() != request.cartItemIds().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "购物车商品不存在");
            }
            return cartItems.stream().map(item -> toSettlementItem(item, item.skuId(), item.quantity())).toList();
        }
        int quantity = request.quantity() == null ? 1 : request.quantity();
        if (request.skuId() == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择商品规格和数量");
        }
        return List.of(toSettlementItem(null, request.skuId(), quantity));
    }


    private SettlementItem toSettlementItem(CartItemSnapshot cartItem, Long skuId, int quantity) {
        ProductSkuSnapshot sku = productCatalogClient.getSkuSnapshot(skuId);
        if (sku == null || Boolean.TRUE.equals(sku.deletedFlag())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不存在");
        }
        return new SettlementItem(cartItem == null ? null : cartItem.id(), sku, quantity);
    }

    private void validateItems(List<SettlementItem> items) {
        for (SettlementItem item : items) {
            // 结算前统一检查商品状态、SKU 状态和库存，避免脏数据进入下单流程。
            if (!ON_SALE.equals(item.sku().saleStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部分商品已下架，请重新确认");
            }
            if (!ENABLED.equals(item.sku().skuStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部分商品规格已失效，请重新选择");
            }
            if (item.sku().stock() < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足，请修改购买数量");
            }
        }
        boolean hasNormalSingleBuy = items.stream().anyMatch(item -> Boolean.TRUE.equals(item.sku().allowSingleBuy()));
        boolean hasOnlyCannotSingleBuy = items.stream().allMatch(item -> !Boolean.TRUE.equals(item.sku().allowSingleBuy()));
        // 不可单独购买的商品不能脱离搭售场景单独下单。
        if (hasOnlyCannotSingleBuy || (!hasNormalSingleBuy && items.size() > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不可单独购买");
        }
        long deliveryTypeCount = items.stream()
            .map(item -> item.sku().deliveryType())
            .filter(deliveryType -> deliveryType != null && !deliveryType.isBlank())
            .distinct()
            .count();
        if (deliveryTypeCount > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Different delivery types cannot be checked out together");
        }
    }

    private MemberAddress resolveAddress(Long userId, Long addressId) {
        return memberClient.resolveAddress(userId, addressId);
    }

    private int calculateFreight(List<SettlementItem> items) {
        boolean hasColdChain = items.stream().anyMatch(item -> "COLD_CHAIN".equals(item.sku().deliveryType()));
        return hasColdChain ? COLD_CHAIN_FREIGHT : NORMAL_FREIGHT;
    }

    private CouponSelection selectCoupon(Long userId, Long requestedCouponUserId, int productAmount) {
        MarketingCouponSelection selection = marketingClient.selectCoupon(userId, requestedCouponUserId, productAmount);
        if (selection == null) {
            return new CouponSelection(null, null, null, null, List.of(), 0);
        }
        List<ConfirmCouponResponse> responses = selection.availableCoupons() == null
            ? List.of()
            : selection.availableCoupons().stream().map(this::toCouponResponse).toList();
        MarketingCoupon selectedCoupon = selection.availableCoupons() == null
            ? null
            : selection.availableCoupons().stream()
                .filter(coupon -> Boolean.TRUE.equals(coupon.selected()))
                .findFirst()
                .orElse(null);
        return new CouponSelection(
            selection.selectedUserCouponId(),
            selectedCoupon == null ? null : selectedCoupon.couponId(),
            selectedCoupon == null ? null : selectedCoupon.name(),
            selectedCoupon == null ? null : selectedCoupon.couponType(),
            responses,
            selection.discountAmount() == null ? 0 : selection.discountAmount()
        );
    }

    private PointSelection selectPoints(Long userId, Boolean usePoints, List<SettlementItem> items, int remainingAmount) {
        boolean visible = items.stream().anyMatch(item -> Boolean.TRUE.equals(item.sku().supportPointDeduction()));
        MemberPointAccount account = memberClient.getPointAccount(userId);
        int availablePoints = account == null || account.availablePoints() == null ? 0 : account.availablePoints();
        boolean selected = visible && Boolean.TRUE.equals(usePoints) && availablePoints > 0;
        // 积分按固定比例抵扣，但最低只抵到 0，不会把应付金额抵成负数。
        int deduction = selected ? Math.min(availablePoints / POINT_EXCHANGE_RATE, Math.max(remainingAmount, 0)) : 0;
        return new PointSelection(visible, availablePoints, deduction, selected);
    }

    private SettlementSnapshot toSnapshot(Long userId, ConfirmOrderRequest request, SettlementCalculation calculation) {
        return new SettlementSnapshot(
            userId,
            request,
            calculation.sourceType(),
            calculation.address(),
            calculation.items().stream()
                .map(item -> new SettlementSnapshotItem(item.cartItemId(), item.sku(), item.quantity()))
                .toList(),
            calculation.selectedUserCouponId(),
            calculation.availableCoupons(),
            new SettlementPointSnapshot(
                calculation.pointSelection().visible(),
                calculation.pointSelection().availablePoints(),
                calculation.pointSelection().deductionAmount(),
                calculation.pointSelection().selected()
            ),
            calculation.amount()
        );
    }

    private SettlementCalculation calculationFromSnapshot(SettlementSession session) {
        SettlementSnapshot snapshot = session.snapshot();
        if (snapshot == null) {
            return calculate(session.userId(), session.request());
        }
        return new SettlementCalculation(
            snapshot.sourceType(),
            snapshot.address(),
            snapshot.items().stream()
                .map(item -> new SettlementItem(item.cartItemId(), item.sku(), item.quantity()))
                .toList(),
            snapshot.selectedUserCouponId(),
            snapshot.availableCoupons(),
            new PointSelection(
                snapshot.pointSelection().visible(),
                snapshot.pointSelection().availablePoints(),
                snapshot.pointSelection().deductionAmount(),
                snapshot.pointSelection().selected()
            ),
            snapshot.amount()
        );
    }

    private OrderResponse persistOrder(Long userId, ConfirmOrderRequest request, SettlementCalculation calculation, String createRemark, String clientRequestId) {
        LocalDateTime now = LocalDateTime.now();
        // 先落主订单，再保存明细、金额快照和优惠券使用状态。
        TradeOrder order = new TradeOrder();
        order.setOrderNo("SO" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now) + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        order.setUserId(userId);
        order.setClientRequestId(clientRequestId);
        OrderStateMachine.initializeCreated(order);
        order.setSourceType(calculation.sourceType());
        order.setTotalAmount(calculation.amount().productAmount());
        order.setDiscountAmount(calculation.amount().productDiscountAmount());
        order.setCouponAmount(calculation.amount().couponDiscountAmount());
        order.setCouponUserId(calculation.selectedUserCouponId());
        order.setPointAmount(calculation.amount().pointDiscountAmount());
        order.setFreightAmount(calculation.amount().freightAmount());
        order.setPayAmount(calculation.amount().payAmount());
        order.setReceiverName(calculation.address().receiverName());
        order.setReceiverMobile(calculation.address().receiverMobile());
        order.setReceiverAddress(addressText(calculation.address()));
        order.setRemark(createRemark == null ? request.remark() : createRemark);
        order.setPayExpireTime(now.plusMinutes(30));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        TradeOrder savedOrder = tradeOrderRepository.save(order);

        List<TradeOrderItem> savedItems = new java.util.ArrayList<>();
        List<ItemRefundSnapshot> refundSnapshots = buildRefundSnapshots(calculation);
        for (int index = 0; index < calculation.items().size(); index++) {
            SettlementItem item = calculation.items().get(index);
            ItemRefundSnapshot refundSnapshot = refundSnapshots.get(index);
            TradeOrderItem orderItem = new TradeOrderItem();
            orderItem.setOrderId(savedOrder.getId());
            orderItem.setProductId(item.sku().productId());
            orderItem.setSkuId(item.sku().skuId());
            orderItem.setProductName(item.sku().productName());
            orderItem.setSkuName(item.sku().skuName());
            orderItem.setSpecJson(item.sku().specJson());
            orderItem.setCategoryId(item.sku().categoryId());
            orderItem.setBrandName(item.sku().brandName());
            orderItem.setProductImageUrl(item.sku().productImageUrl());
            orderItem.setSalePrice(item.sku().salePrice());
            orderItem.setQuantity(item.quantity());
            orderItem.setTotalAmount(item.totalAmount());
            orderItem.setDiscountAmount(0);
            orderItem.setPayAmount(refundSnapshot.itemPayAmount());
            orderItem.setCouponShareAmount(refundSnapshot.couponShareAmount());
            orderItem.setPointShareAmount(refundSnapshot.pointShareAmount());
            orderItem.setFreightShareAmount(refundSnapshot.freightShareAmount());
            orderItem.setPromotionShareJson(writePromotionJson(promotionSharesForIndex(calculation.amount().promotionTraces(), index)));
            orderItem.setDeliveryType(item.sku().deliveryType());
            orderItem.setSupportRefund(defaultBool(item.sku().supportRefund(), true));
            orderItem.setSupportPointDeduction(defaultBool(item.sku().supportPointDeduction(), false));
            orderItem.setSnapshotVersion(defaultInt(item.sku().snapshotVersion(), 1));
            orderItem.setAftersaleQuantity(0);
            orderItem.setRefundableQuantity(item.quantity());
            orderItem.setRefundedQuantity(0);
            orderItem.setRefundAmount(0);
            orderItem.setRefundableAmount(refundSnapshot.refundableAmount());
            orderItem.setRefundStatus("NONE");
            orderItem.setCreatedAt(now);
            savedItems.add(tradeOrderItemRepository.save(orderItem));
        }

        TradeOrderAmount amount = new TradeOrderAmount();
        amount.setOrderId(savedOrder.getId());
        amount.setProductAmount(calculation.amount().productAmount());
        amount.setActivityDiscountAmount(calculation.amount().productDiscountAmount());
        amount.setCouponDiscountAmount(calculation.amount().couponDiscountAmount());
        amount.setPointDiscountAmount(calculation.amount().pointDiscountAmount());
        amount.setFreightAmount(calculation.amount().freightAmount());
        amount.setFreightDiscountAmount(calculation.amount().freightDiscountAmount());
        amount.setPayAmount(calculation.amount().payAmount());
        amount.setPromotionTraceJson(calculation.amount().promotionTraceJson());
        amount.setCreatedAt(now);
        tradeOrderAmountRepository.save(amount);

        inventoryOutbox.append(savedOrder, savedItems, InventoryIntegrationEvent.ORDER_CREATED, 1, now);
        freezePointsIfPresent(userId, savedOrder.getId(), pointDiscountPoints(savedOrder));

        return toOrderResponse(savedOrder);
    }

    private void deleteCartItemsAfterCreate(Long userId, SettlementCalculation calculation) {
        if (!CART.equals(calculation.sourceType())) {
            return;
        }
        List<Long> cartItemIds = calculation.items().stream()
            .map(SettlementItem::cartItemId)
            .filter(id -> id != null)
            .toList();
        cartClient.deleteItems(userId, cartItemIds);
    }

    private String couponLockKey(String settlementToken, String clientRequestId) {
        if (clientRequestId != null) {
            return "ORDER:" + clientRequestId;
        }
        return "SETTLEMENT:" + settlementToken;
    }

    private void useCouponIfPresent(Long userId, Long userCouponId, Long orderId) {
        if (userCouponId != null) {
            marketingClient.useCoupon(userId, userCouponId, orderId);
        }
    }

    private void releaseCouponIfPresent(Long userId, Long userCouponId, Long orderId) {
        if (userCouponId != null) {
            marketingClient.releaseCoupon(userId, userCouponId, orderId);
        }
    }

    private void releaseCouponQuietly(Long userId, Long userCouponId, Long orderId) {
        try {
            releaseCouponIfPresent(userId, userCouponId, orderId);
        } catch (RuntimeException ignored) {
            // Best-effort compensation after local order creation fails.
        }
    }

    private boolean isPaymentExpired(TradeOrder order, LocalDateTime now) {
        return OrderStateMachine.ORDER_WAIT_PAY.equals(order.getOrderStatus())
            && OrderStateMachine.PAY_UNPAID.equals(order.getPayStatus())
            && order.getPayExpireTime() != null
            && !order.getPayExpireTime().isAfter(now);
    }

    private void expirePayment(TradeOrder order, LocalDateTime now) {
        OrderStateMachine.expirePayment(order, now);
        tradeOrderRepository.save(order);
        inventoryOutbox.append(order, tradeOrderItemRepository.findByOrderId(order.getId()),
            InventoryIntegrationEvent.ORDER_CANCELLED, 2, now);
    }

    private void freezePointsIfPresent(Long userId, Long orderId, int points) {
        if (points > 0) {
            memberClient.freezePoints(userId, orderId, pointBizNo(orderId), points);
        }
    }

    private void deductPointsIfPresent(Long userId, Long orderId, int points) {
        if (points > 0) {
            memberClient.deductFrozenPoints(userId, orderId, pointBizNo(orderId), points);
        }
    }

    private void releasePointsIfPresent(Long userId, Long orderId, int points) {
        if (points > 0) {
            memberClient.releaseFrozenPoints(userId, orderId, pointBizNo(orderId), points);
        }
    }

    private void refundPointsAfterCommit(Long userId, Long orderId, int points, boolean fullRefund) {
        if (!fullRefund || points <= 0) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            memberClient.refundPoints(userId, orderId, pointBizNo(orderId), points);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                memberClient.refundPoints(userId, orderId, pointBizNo(orderId), points);
            }
        });
    }

    private int pointDiscountPoints(TradeOrder order) {
        int pointDiscountAmount = order.getPointAmount() == null ? 0 : order.getPointAmount();
        return Math.max(pointDiscountAmount, 0) * POINT_EXCHANGE_RATE;
    }

    private String pointBizNo(Long orderId) {
        return "ORDER_POINT:" + orderId;
    }

    private void refundCouponAfterCommit(Long userId, Long userCouponId, Long orderId, boolean fullRefund) {
        if (!fullRefund || userCouponId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            marketingClient.refundCoupon(userId, userCouponId, orderId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                marketingClient.refundCoupon(userId, userCouponId, orderId);
            }
        });
    }

    private OrderResponse findIdempotentOrder(Long userId, String clientRequestId) {
        return tradeOrderRepository.findByUserIdAndClientRequestId(userId, clientRequestId)
            .map(this::toOrderResponse)
            .orElse(null);
    }

    private String normalizeClientRequestId(String clientRequestId) {
        if (clientRequestId == null) {
            return null;
        }
        String trimmed = clientRequestId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private ConfirmOrderResponse toConfirmResponse(String token, SettlementCalculation calculation, String remark) {
        return new ConfirmOrderResponse(
            token,
            calculation.sourceType(),
            toAddress(calculation.address()),
            toConfirmItems(calculation),
            calculation.amount().freightAmount(),
            PriceFormatter.formatCents(calculation.amount().freightAmount()),
            calculation.availableCoupons().stream().filter(ConfirmCouponResponse::selected).findFirst().orElse(null),
            calculation.availableCoupons(),
            new PointDeductionResponse(
                calculation.pointSelection().visible(),
                calculation.pointSelection().availablePoints(),
                calculation.pointSelection().deductionAmount(),
                PriceFormatter.formatCents(calculation.pointSelection().deductionAmount()),
                calculation.pointSelection().selected()
            ),
            calculation.amount(),
            remark
        );
    }

    private List<ConfirmOrderItemResponse> toConfirmItems(SettlementCalculation calculation) {
        List<ItemRefundSnapshot> refundSnapshots = buildRefundSnapshots(calculation);
        return IntStream.range(0, calculation.items().size())
            .mapToObj(index -> toConfirmItem(
                calculation.items().get(index),
                refundSnapshots.get(index),
                promotionSharesForIndex(calculation.amount().promotionTraces(), index)
            ))
            .toList();
    }

    private ConfirmOrderItemResponse toConfirmItem(SettlementItem item, ItemRefundSnapshot refundSnapshot, List<PromotionShareResponse> promotionShares) {
        return new ConfirmOrderItemResponse(
            item.cartItemId(),
            item.sku().productId(),
            item.sku().skuId(),
            item.sku().productName(),
            item.sku().skuName(),
            item.sku().productImageUrl(),
            item.sku().salePrice(),
            PriceFormatter.formatCents(item.sku().salePrice()),
            item.quantity(),
            item.totalAmount(),
            PriceFormatter.formatCents(item.totalAmount()),
            refundSnapshot.couponShareAmount(),
            PriceFormatter.formatCents(refundSnapshot.couponShareAmount()),
            refundSnapshot.pointShareAmount(),
            PriceFormatter.formatCents(refundSnapshot.pointShareAmount()),
            refundSnapshot.freightShareAmount(),
            PriceFormatter.formatCents(refundSnapshot.freightShareAmount()),
            refundSnapshot.itemPayAmount() + refundSnapshot.freightShareAmount(),
            PriceFormatter.formatCents(refundSnapshot.itemPayAmount() + refundSnapshot.freightShareAmount()),
            promotionShares,
            item.sku().allowSingleBuy(),
            item.sku().supportPointDeduction(),
            item.sku().noticeTitle(),
            item.sku().noticeContent()
        );
    }

    private OrderSummaryResponse toOrderSummary(TradeOrder order) {
        return new OrderSummaryResponse(
            order.getId(),
            order.getOrderNo(),
            order.getUserId(),
            order.getOrderStatus(),
            order.getPayStatus(),
            order.getDeliveryStatus(),
            order.getAftersaleStatus(),
            order.getPayAmount(),
            PriceFormatter.formatCents(order.getPayAmount()),
            order.getCreatedAt()
        );
    }

    private OrderResponse toOrderResponse(TradeOrder order) {
        List<TradeOrderItem> items = tradeOrderItemRepository.findByOrderId(order.getId());
        TradeOrderAmount amount = tradeOrderAmountRepository.findByOrderId(order.getId()).orElse(null);
        return new OrderResponse(
            order.getId(),
            order.getOrderNo(),
            order.getUserId(),
            order.getOrderStatus(),
            order.getPayStatus(),
            order.getDeliveryStatus(),
            order.getAftersaleStatus(),
            order.getPayAmount(),
            PriceFormatter.formatCents(order.getPayAmount()),
            order.getReceiverName(),
            order.getReceiverMobile(),
            order.getReceiverAddress(),
            order.getRemark(),
            order.getLogisticsCompany(),
            order.getLogisticsNo(),
            order.getDeliveryRemark(),
            order.getPayExpireTime(),
            order.getPayTime(),
            order.getDeliveryTime(),
            order.getFinishTime(),
            order.getCreatedAt(),
            amount == null ? null : toAmount(amount.getProductAmount(), amount.getActivityDiscountAmount(), amount.getCouponDiscountAmount(), amount.getPointDiscountAmount(), amount.getFreightAmount(), amount.getFreightDiscountAmount(), amount.getPayAmount(), readPromotionTraces(amount.getPromotionTraceJson()), amount.getPromotionTraceJson()),
            items.stream().map(this::toOrderItem).toList()
        );
    }

    private OrderItemResponse toOrderItem(TradeOrderItem item) {
        return new OrderItemResponse(
            item.getId(),
            item.getProductId(),
            item.getSkuId(),
            item.getProductName(),
            item.getSkuName(),
            item.getSpecJson(),
            item.getCategoryId(),
            item.getBrandName(),
            item.getProductImageUrl(),
            item.getSalePrice(),
            PriceFormatter.formatCents(item.getSalePrice()),
            item.getQuantity(),
            item.getPayAmount(),
            PriceFormatter.formatCents(item.getPayAmount()),
            item.getCouponShareAmount(),
            PriceFormatter.formatCents(item.getCouponShareAmount()),
            item.getPointShareAmount(),
            PriceFormatter.formatCents(item.getPointShareAmount()),
            item.getFreightShareAmount(),
            PriceFormatter.formatCents(item.getFreightShareAmount()),
            readPromotionShares(item.getPromotionShareJson()),
            item.getDeliveryType(),
            item.getSupportRefund(),
            item.getSupportPointDeduction(),
            item.getSnapshotVersion(),
            item.getRefundableQuantity(),
            item.getRefundedQuantity(),
            item.getAftersaleQuantity(),
            item.getRefundAmount(),
            PriceFormatter.formatCents(item.getRefundAmount()),
            item.getRefundStatus()
        );
    }

    private void applyRefund(TradeOrderItem item, Integer quantity, Integer refundAmount) {
        int refundQuantity = quantity == null ? 0 : quantity;
        int availableQuantity = item.getQuantity() - item.getRefundedQuantity();
        if (refundQuantity <= 0 || refundQuantity > availableQuantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退款数量超过可退数量");
        }
        int actualRefundAmount = refundAmount == null ? 0 : Math.max(refundAmount, 0);
        if (actualRefundAmount > positive(item.getRefundableAmount())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount exceeds refundable amount");
        }
        item.setAftersaleQuantity(item.getAftersaleQuantity() + refundQuantity);
        item.setRefundedQuantity(item.getRefundedQuantity() + refundQuantity);
        item.setRefundableQuantity(Math.max(item.getQuantity() - item.getRefundedQuantity(), 0));
        item.setRefundAmount(item.getRefundAmount() + actualRefundAmount);
        item.setRefundableAmount(Math.max(positive(item.getRefundableAmount()) - actualRefundAmount, 0));
        item.setRefundStatus(item.getRefundedQuantity() >= item.getQuantity() ? "REFUNDED" : "PARTIAL_REFUNDED");
    }

    private List<ItemRefundSnapshot> buildRefundSnapshots(SettlementCalculation calculation) {
        List<SettlementItem> items = calculation.items();
        List<Integer> couponShares = allocateByProductAmount(items, calculation.amount().couponDiscountAmount());
        List<Integer> pointShares = allocateByProductAmount(items, calculation.amount().pointDiscountAmount());
        List<Integer> freightShares = allocateByProductAmount(items, calculation.amount().freightAmount() - calculation.amount().freightDiscountAmount());
        List<ItemRefundSnapshot> snapshots = new java.util.ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            int itemPayAmount = Math.max(items.get(index).totalAmount() - couponShares.get(index) - pointShares.get(index), 0);
            int freightShareAmount = Math.max(freightShares.get(index), 0);
            snapshots.add(new ItemRefundSnapshot(
                itemPayAmount,
                couponShares.get(index),
                pointShares.get(index),
                freightShareAmount,
                itemPayAmount + freightShareAmount
            ));
        }
        return snapshots;
    }

    private List<Integer> allocateByProductAmount(List<SettlementItem> items, int amount) {
        if (items.isEmpty()) {
            return List.of();
        }
        int normalizedAmount = Math.max(amount, 0);
        int productAmount = items.stream().mapToInt(SettlementItem::totalAmount).sum();
        List<Integer> shares = new java.util.ArrayList<>();
        int allocated = 0;
        for (int index = 0; index < items.size(); index++) {
            int share;
            if (index == items.size() - 1) {
                share = normalizedAmount - allocated;
            } else if (productAmount <= 0) {
                share = 0;
            } else {
                share = normalizedAmount * items.get(index).totalAmount() / productAmount;
                allocated += share;
            }
            shares.add(Math.max(share, 0));
        }
        return shares;
    }

    private List<PromotionTraceResponse> buildPromotionTraces(Long userId, List<SettlementItem> items, CouponSelection couponSelection, PointSelection pointSelection) {
        List<PromotionTraceResponse> traces = new ArrayList<>();
        if (couponSelection.discountAmount() > 0) {
            traces.add(new PromotionTraceResponse(
                "COUPON",
                couponSelection.selectedUserCouponId() == null ? null : couponSelection.selectedUserCouponId().toString(),
                couponSelection.couponId() == null ? null : couponSelection.couponId().toString(),
                couponSelection.name() == null ? "Coupon discount" : couponSelection.name(),
                couponSelection.discountAmount(),
                PriceFormatter.formatCents(couponSelection.discountAmount()),
                traceItems(items, allocateByProductAmount(items, couponSelection.discountAmount()))
            ));
        }
        if (pointSelection.deductionAmount() > 0) {
            traces.add(new PromotionTraceResponse(
                "POINT",
                "USER_POINTS:" + userId,
                "POINT_EXCHANGE_RATE_" + POINT_EXCHANGE_RATE,
                "Point deduction",
                pointSelection.deductionAmount(),
                PriceFormatter.formatCents(pointSelection.deductionAmount()),
                traceItems(items, allocateByProductAmount(items, pointSelection.deductionAmount()))
            ));
        }
        return traces;
    }

    private List<PromotionTraceItemResponse> traceItems(List<SettlementItem> items, List<Integer> shares) {
        return IntStream.range(0, items.size())
            .mapToObj(index -> new PromotionTraceItemResponse(
                items.get(index).cartItemId(),
                items.get(index).sku().productId(),
                items.get(index).sku().skuId(),
                shares.get(index),
                PriceFormatter.formatCents(shares.get(index))
            ))
            .toList();
    }

    private List<PromotionShareResponse> promotionSharesForIndex(List<PromotionTraceResponse> traces, int index) {
        if (traces == null || traces.isEmpty()) {
            return List.of();
        }
        return traces.stream()
            .filter(trace -> trace.items() != null && trace.items().size() > index && trace.items().get(index).shareAmount() > 0)
            .map(trace -> new PromotionShareResponse(
                trace.promotionType(),
                trace.sourceId(),
                trace.ruleId(),
                trace.name(),
                trace.items().get(index).shareAmount(),
                trace.items().get(index).shareAmountText()
            ))
            .toList();
    }

    private String writePromotionJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Promotion trace serialization failed", ex);
        }
    }

    private List<PromotionTraceResponse> readPromotionTraces(String value) {
        return readPromotionJson(value, new TypeReference<List<PromotionTraceResponse>>() {
        });
    }

    private List<PromotionShareResponse> readPromotionShares(String value) {
        return readPromotionJson(value, new TypeReference<List<PromotionShareResponse>>() {
        });
    }

    private <T> List<T> readPromotionJson(String value, TypeReference<List<T>> typeReference) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private OrderAddressResponse toAddress(MemberAddress address) {
        return new OrderAddressResponse(
            address.id(),
            address.receiverName(),
            address.receiverMobile(),
            address.province(),
            address.city(),
            address.district(),
            address.detailAddress(),
            address.defaultFlag()
        );
    }

    private ConfirmCouponResponse toCouponResponse(MarketingCoupon coupon) {
        return new ConfirmCouponResponse(
            coupon.userCouponId(),
            coupon.couponId(),
            coupon.name(),
            coupon.couponType(),
            coupon.thresholdAmount(),
            PriceFormatter.formatCents(coupon.thresholdAmount()),
            coupon.discountAmount(),
            PriceFormatter.formatCents(coupon.discountAmount()),
            Boolean.TRUE.equals(coupon.selected())
        );
    }

    private OrderAmountResponse toAmount(int productAmount, int productDiscountAmount, int couponDiscountAmount, int pointDiscountAmount, int freightAmount, int freightDiscountAmount, int payAmount, List<PromotionTraceResponse> promotionTraces, String promotionTraceJson) {
        return new OrderAmountResponse(
            productAmount,
            PriceFormatter.formatCents(productAmount),
            productDiscountAmount,
            PriceFormatter.formatCents(productDiscountAmount),
            couponDiscountAmount,
            PriceFormatter.formatCents(couponDiscountAmount),
            pointDiscountAmount,
            PriceFormatter.formatCents(pointDiscountAmount),
            freightAmount,
            PriceFormatter.formatCents(freightAmount),
            freightDiscountAmount,
            PriceFormatter.formatCents(freightDiscountAmount),
            payAmount,
            PriceFormatter.formatCents(payAmount),
            promotionTraces == null ? List.of() : promotionTraces,
            promotionTraceJson == null ? "[]" : promotionTraceJson
        );
    }

    private String addressText(MemberAddress address) {
        return address.province() + address.city() + address.district() + address.detailAddress();
    }

    private String normalizeSourceType(String sourceType) {
        if (BUY_NOW.equals(sourceType)) {
            return BUY_NOW;
        }
        return CART;
    }

    private String normalizeDeliveryStatus(String status) {
        return switch (status == null ? "" : status.trim().toUpperCase()) {
            case OrderStateMachine.DELIVERY_SHIPPED -> OrderStateMachine.DELIVERY_SHIPPED;
            case OrderStateMachine.DELIVERY_IN_TRANSIT -> OrderStateMachine.DELIVERY_IN_TRANSIT;
            case OrderStateMachine.DELIVERY_DELIVERED -> OrderStateMachine.DELIVERY_DELIVERED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的配送状态");
        };
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private Boolean defaultBool(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Integer defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String callbackRequestNo(String channelTradeNo) {
        UUID stableId = UUID.nameUUIDFromBytes(channelTradeNo.getBytes(StandardCharsets.UTF_8));
        return "CALLBACK:" + stableId;
    }

    private record SettlementItem(Long cartItemId, ProductSkuSnapshot sku, int quantity) {
        int totalAmount() {
            return sku.salePrice() * quantity;
        }
    }

    private record SettlementCalculation(
        String sourceType,
        MemberAddress address,
        List<SettlementItem> items,
        Long selectedUserCouponId,
        List<ConfirmCouponResponse> availableCoupons,
        PointSelection pointSelection,
        OrderAmountResponse amount
    ) {
    }


    private record CouponSelection(Long selectedUserCouponId, Long couponId, String name, String couponType, List<ConfirmCouponResponse> availableCoupons, int discountAmount) {
    }

    private record PointSelection(boolean visible, int availablePoints, int deductionAmount, boolean selected) {
    }

    private record ItemRefundSnapshot(
        int itemPayAmount,
        int couponShareAmount,
        int pointShareAmount,
        int freightShareAmount,
        int refundableAmount
    ) {
    }

    private record OrderTransition(OrderResponse response, Long couponUserId, int pointAmount, boolean expired) {
    }

    private record ExpiredOrderTransition(boolean closed, Long userId, Long orderId, Long couponUserId, int pointAmount) {
        static ExpiredOrderTransition unchanged() {
            return new ExpiredOrderTransition(false, null, null, null, 0);
        }

        static ExpiredOrderTransition closed(Long userId, Long orderId, Long couponUserId, int pointAmount) {
            return new ExpiredOrderTransition(true, userId, orderId, couponUserId, pointAmount);
        }
    }

    private record PaymentCallbackTransition(boolean duplicate, Long userId, Long orderId, Long couponUserId, int pointAmount) {
        static PaymentCallbackTransition duplicated() {
            return new PaymentCallbackTransition(true, null, null, null, 0);
        }

        static PaymentCallbackTransition processed(Long userId, Long orderId, Long couponUserId, int pointAmount) {
            return new PaymentCallbackTransition(false, userId, orderId, couponUserId, pointAmount);
        }
    }

}
