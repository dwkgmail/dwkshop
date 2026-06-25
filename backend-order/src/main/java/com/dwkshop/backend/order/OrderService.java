package com.dwkshop.backend.order;

import com.dwkshop.backend.domain.entity.TradeOrder;
import com.dwkshop.backend.domain.entity.TradeOrderAmount;
import com.dwkshop.backend.domain.entity.TradeOrderItem;
import com.dwkshop.backend.domain.repository.OrderOutboxEventRepository;
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
import com.dwkshop.backend.order.dto.PointDeductionResponse;
import com.dwkshop.backend.util.PriceFormatter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
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
    private final SettlementSessionStore settlementSessionStore;
    private final CartClient cartClient;
    private final MemberClient memberClient;
    private final MarketingClient marketingClient;
    private final ProductCatalogClient productCatalogClient;
    private final OrderInventoryOutbox inventoryOutbox;
    private final TransactionTemplate transactionTemplate;
    private final Duration settlementTtl;

    public OrderService(
        TradeOrderRepository tradeOrderRepository,
        TradeOrderItemRepository tradeOrderItemRepository,
        TradeOrderAmountRepository tradeOrderAmountRepository,
        OrderOutboxEventRepository orderOutboxEventRepository,
        SettlementSessionStore settlementSessionStore,
        CartClient cartClient,
        MemberClient memberClient,
        MarketingClient marketingClient,
        ProductCatalogClient productCatalogClient,
        OrderInventoryOutbox inventoryOutbox,
        TransactionTemplate transactionTemplate,
        @Value("${dwkshop.order.settlement-ttl-minutes:30}") long settlementTtlMinutes
    ) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderItemRepository = tradeOrderItemRepository;
        this.tradeOrderAmountRepository = tradeOrderAmountRepository;
        this.orderOutboxEventRepository = orderOutboxEventRepository;
        this.settlementSessionStore = settlementSessionStore;
        this.cartClient = cartClient;
        this.memberClient = memberClient;
        this.marketingClient = marketingClient;
        this.productCatalogClient = productCatalogClient;
        this.inventoryOutbox = inventoryOutbox;
        this.transactionTemplate = transactionTemplate;
        this.settlementTtl = Duration.ofMinutes(settlementTtlMinutes);
    }

    @Transactional(readOnly = true)
    public ConfirmOrderResponse confirm(Long userId, ConfirmOrderRequest request) {
        // 提交订单前先完成一次结算，后续创建订单必须携带本次结算 token。
        SettlementCalculation calculation = calculate(userId, request);
        String token = "SETTLE-" + UUID.randomUUID();
        ConfirmOrderResponse response = toConfirmResponse(token, calculation, request.remark());
        // 在内存中暂存本次结算快照，用于校验金额并拦截重复提交。
        settlementSessionStore.save(token, new SettlementSession(userId, request, response.amount().payAmount()), settlementTtl);
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
            // 创建订单前重新结算一次，确保库存、优惠券、积分等实时数据未发生变化。
            SettlementCalculation calculation = calculate(userId, session.request());
            if (!calculation.amount().payAmount().equals(request.expectedPayAmount())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单金额已变化，请重新确认");
            }
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
            if (order.getPayExpireTime() != null && order.getPayExpireTime().isBefore(now)) {
                OrderStateMachine.expirePayment(order, now);
                tradeOrderRepository.save(order);
                inventoryOutbox.append(order, tradeOrderItemRepository.findByOrderId(orderId),
                    InventoryIntegrationEvent.ORDER_CANCELLED, 2, now);
                return new OrderTransition(null, order.getCouponUserId(), pointDiscountPoints(order), true);
            }
            OrderStateMachine.pay(order, now);
            tradeOrderRepository.save(order);
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
                    item.getRefundAmount(),
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
            applyRefund(item, item.getRefundableQuantity(), item.getPayAmount() - item.getRefundAmount());
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
                applyRefund(item, item.getRefundableQuantity(), item.getPayAmount() - item.getRefundAmount());
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
        OrderAmountResponse amount = toAmount(productAmount, productDiscountAmount, couponSelection.discountAmount(), pointSelection.deductionAmount(), freightAmount, 0, payAmount);
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
            return new CouponSelection(null, List.of(), 0);
        }
        List<ConfirmCouponResponse> responses = selection.availableCoupons() == null
            ? List.of()
            : selection.availableCoupons().stream().map(this::toCouponResponse).toList();
        return new CouponSelection(selection.selectedUserCouponId(), responses, selection.discountAmount() == null ? 0 : selection.discountAmount());
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
        for (SettlementItem item : calculation.items()) {
            TradeOrderItem orderItem = new TradeOrderItem();
            orderItem.setOrderId(savedOrder.getId());
            orderItem.setProductId(item.sku().productId());
            orderItem.setSkuId(item.sku().skuId());
            orderItem.setProductName(item.sku().productName());
            orderItem.setSkuName(item.sku().skuName());
            orderItem.setProductImageUrl(item.sku().productImageUrl());
            orderItem.setSalePrice(item.sku().salePrice());
            orderItem.setQuantity(item.quantity());
            orderItem.setTotalAmount(item.totalAmount());
            orderItem.setDiscountAmount(0);
            orderItem.setPayAmount(item.totalAmount());
            orderItem.setSupportRefund(true);
            orderItem.setAftersaleQuantity(0);
            orderItem.setRefundableQuantity(item.quantity());
            orderItem.setRefundedQuantity(0);
            orderItem.setRefundAmount(0);
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
            calculation.items().stream().map(this::toConfirmItem).toList(),
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

    private ConfirmOrderItemResponse toConfirmItem(SettlementItem item) {
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
            amount == null ? null : toAmount(amount.getProductAmount(), amount.getActivityDiscountAmount(), amount.getCouponDiscountAmount(), amount.getPointDiscountAmount(), amount.getFreightAmount(), amount.getFreightDiscountAmount(), amount.getPayAmount()),
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
            item.getProductImageUrl(),
            item.getSalePrice(),
            PriceFormatter.formatCents(item.getSalePrice()),
            item.getQuantity(),
            item.getPayAmount(),
            PriceFormatter.formatCents(item.getPayAmount()),
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
        item.setAftersaleQuantity(item.getAftersaleQuantity() + refundQuantity);
        item.setRefundedQuantity(item.getRefundedQuantity() + refundQuantity);
        item.setRefundableQuantity(Math.max(item.getQuantity() - item.getRefundedQuantity(), 0));
        item.setRefundAmount(item.getRefundAmount() + (refundAmount == null ? 0 : Math.max(refundAmount, 0)));
        item.setRefundStatus(item.getRefundedQuantity() >= item.getQuantity() ? "REFUNDED" : "PARTIAL_REFUNDED");
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

    private OrderAmountResponse toAmount(int productAmount, int productDiscountAmount, int couponDiscountAmount, int pointDiscountAmount, int freightAmount, int freightDiscountAmount, int payAmount) {
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
            PriceFormatter.formatCents(payAmount)
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


    private record CouponSelection(Long selectedUserCouponId, List<ConfirmCouponResponse> availableCoupons, int discountAmount) {
    }

    private record PointSelection(boolean visible, int availablePoints, int deductionAmount, boolean selected) {
    }

    private record OrderTransition(OrderResponse response, Long couponUserId, int pointAmount, boolean expired) {
    }

}
