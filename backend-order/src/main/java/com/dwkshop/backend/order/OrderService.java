package com.dwkshop.backend.order;

import com.dwkshop.backend.domain.entity.TradeOrder;
import com.dwkshop.backend.domain.entity.TradeOrderAmount;
import com.dwkshop.backend.domain.entity.TradeOrderItem;
import com.dwkshop.backend.domain.repository.TradeOrderAmountRepository;
import com.dwkshop.backend.domain.repository.TradeOrderItemRepository;
import com.dwkshop.backend.domain.repository.TradeOrderRepository;
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
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final SettlementSessionStore settlementSessionStore;
    private final CartClient cartClient;
    private final MemberClient memberClient;
    private final MarketingClient marketingClient;
    private final ProductCatalogClient productCatalogClient;
    private final OrderInventoryOutbox inventoryOutbox;
    private final Duration settlementTtl;

    public OrderService(
        TradeOrderRepository tradeOrderRepository,
        TradeOrderItemRepository tradeOrderItemRepository,
        TradeOrderAmountRepository tradeOrderAmountRepository,
        SettlementSessionStore settlementSessionStore,
        CartClient cartClient,
        MemberClient memberClient,
        MarketingClient marketingClient,
        ProductCatalogClient productCatalogClient,
        OrderInventoryOutbox inventoryOutbox,
        @Value("${dwkshop.order.settlement-ttl-minutes:30}") long settlementTtlMinutes
    ) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderItemRepository = tradeOrderItemRepository;
        this.tradeOrderAmountRepository = tradeOrderAmountRepository;
        this.settlementSessionStore = settlementSessionStore;
        this.cartClient = cartClient;
        this.memberClient = memberClient;
        this.marketingClient = marketingClient;
        this.productCatalogClient = productCatalogClient;
        this.inventoryOutbox = inventoryOutbox;
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

    @Transactional
    public OrderResponse create(Long userId, CreateOrderRequest request) {
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
            return persistOrder(userId, session.request(), calculation, request.remark());
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

    @Transactional
    public OrderResponse cancel(Long userId, Long orderId) {
        TradeOrder order = tradeOrderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        LocalDateTime now = LocalDateTime.now();
        OrderStateMachine.cancelUnpaid(order, now);
        tradeOrderRepository.save(order);
        inventoryOutbox.append(order, tradeOrderItemRepository.findByOrderId(orderId),
            InventoryIntegrationEvent.ORDER_CANCELLED, 2, now);
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse pay(Long userId, Long orderId) {
        TradeOrder order = tradeOrderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (OrderStateMachine.PAY_PAID.equals(order.getPayStatus())) {
            return toOrderResponse(order);
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getPayExpireTime() != null && order.getPayExpireTime().isBefore(now)) {
            OrderStateMachine.expirePayment(order, now);
            tradeOrderRepository.save(order);
            inventoryOutbox.append(order, tradeOrderItemRepository.findByOrderId(orderId),
                InventoryIntegrationEvent.ORDER_CANCELLED, 2, now);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单支付已超时");
        }
        OrderStateMachine.pay(order, now);
        tradeOrderRepository.save(order);
        return toOrderResponse(order);
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
            && items.stream().allMatch(item -> Boolean.TRUE.equals(item.getSupportRefund()));
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
                .map(item -> new RefundOrderItemSnapshot(item.getSkuId(), item.getProductId(), item.getQuantity(), item.getSupportRefund()))
                .toList()
        );
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
            item.setAftersaleQuantity(item.getQuantity());
        }
        tradeOrderItemRepository.saveAll(items);
        OrderStateMachine.completeAftersale(order, LocalDateTime.now());
        return toAftersaleSnapshot(tradeOrderRepository.save(order), items);
    }

    private AftersaleOrderSnapshot toAftersaleSnapshot(TradeOrder order) {
        return toAftersaleSnapshot(order, tradeOrderItemRepository.findByOrderId(order.getId()));
    }

    private AftersaleOrderSnapshot toAftersaleSnapshot(TradeOrder order, List<TradeOrderItem> items) {
        boolean refundable = !items.isEmpty()
            && items.stream().allMatch(item -> Boolean.TRUE.equals(item.getSupportRefund()));
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

    private OrderResponse persistOrder(Long userId, ConfirmOrderRequest request, SettlementCalculation calculation, String createRemark) {
        LocalDateTime now = LocalDateTime.now();
        // 先落主订单，再保存明细、金额快照和优惠券使用状态。
        TradeOrder order = new TradeOrder();
        order.setOrderNo("SO" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now) + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        order.setUserId(userId);
        OrderStateMachine.initializeCreated(order);
        order.setSourceType(calculation.sourceType());
        order.setTotalAmount(calculation.amount().productAmount());
        order.setDiscountAmount(calculation.amount().productDiscountAmount());
        order.setCouponAmount(calculation.amount().couponDiscountAmount());
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

        if (calculation.selectedUserCouponId() != null) {
            marketingClient.useCoupon(userId, calculation.selectedUserCouponId(), savedOrder.getId());
        }

        if (CART.equals(calculation.sourceType())) {
            // 购物车来源的订单创建成功后，再删除对应购物车项。
            List<Long> cartItemIds = calculation.items().stream()
                .map(SettlementItem::cartItemId)
                .filter(id -> id != null)
                .toList();
            cartClient.deleteItems(userId, cartItemIds);
        }

        return toOrderResponse(savedOrder);
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
            PriceFormatter.formatCents(item.getPayAmount())
        );
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

}
