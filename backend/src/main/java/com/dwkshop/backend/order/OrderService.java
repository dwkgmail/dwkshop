package com.dwkshop.backend.order;

import com.dwkshop.backend.admin.AdminOperationLogService;
import com.dwkshop.backend.domain.entity.CartItem;
import com.dwkshop.backend.domain.entity.Coupon;
import com.dwkshop.backend.domain.entity.CouponUser;
import com.dwkshop.backend.domain.entity.Product;
import com.dwkshop.backend.domain.entity.ProductNotice;
import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.entity.TradeOrder;
import com.dwkshop.backend.domain.entity.TradeOrderAmount;
import com.dwkshop.backend.domain.entity.TradeOrderItem;
import com.dwkshop.backend.domain.entity.UserAddress;
import com.dwkshop.backend.domain.entity.UserPointAccount;
import com.dwkshop.backend.domain.repository.CartItemRepository;
import com.dwkshop.backend.domain.repository.CouponRepository;
import com.dwkshop.backend.domain.repository.CouponUserRepository;
import com.dwkshop.backend.domain.repository.ProductNoticeRepository;
import com.dwkshop.backend.domain.repository.ProductRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.domain.repository.TradeOrderAmountRepository;
import com.dwkshop.backend.domain.repository.TradeOrderItemRepository;
import com.dwkshop.backend.domain.repository.TradeOrderRepository;
import com.dwkshop.backend.domain.repository.UserAddressRepository;
import com.dwkshop.backend.domain.repository.UserPointAccountRepository;
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
import com.dwkshop.backend.order.dto.PromotionShareResponse;
import com.dwkshop.backend.order.dto.PromotionTraceItemResponse;
import com.dwkshop.backend.order.dto.PromotionTraceResponse;
import com.dwkshop.backend.product.PriceFormatter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    private static final String DELIVERY_UNSHIPPED = "UNSHIPPED";
    private static final String DELIVERY_SHIPPED = "SHIPPED";
    private static final String DELIVERY_IN_TRANSIT = "IN_TRANSIT";
    private static final String DELIVERY_DELIVERED = "DELIVERED";

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductNoticeRepository productNoticeRepository;
    private final UserAddressRepository userAddressRepository;
    private final CouponUserRepository couponUserRepository;
    private final CouponRepository couponRepository;
    private final UserPointAccountRepository userPointAccountRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderItemRepository tradeOrderItemRepository;
    private final TradeOrderAmountRepository tradeOrderAmountRepository;
    private final SettlementSessionStore settlementSessionStore;
    private final ApplicationEventPublisher eventPublisher;
    private final AdminOperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final Duration settlementTtl;

    public OrderService(
        CartItemRepository cartItemRepository,
        ProductRepository productRepository,
        ProductSkuRepository productSkuRepository,
        ProductNoticeRepository productNoticeRepository,
        UserAddressRepository userAddressRepository,
        CouponUserRepository couponUserRepository,
        CouponRepository couponRepository,
        UserPointAccountRepository userPointAccountRepository,
        TradeOrderRepository tradeOrderRepository,
        TradeOrderItemRepository tradeOrderItemRepository,
        TradeOrderAmountRepository tradeOrderAmountRepository,
        SettlementSessionStore settlementSessionStore,
        ApplicationEventPublisher eventPublisher,
        AdminOperationLogService operationLogService,
        ObjectMapper objectMapper,
        @Value("${dwkshop.order.settlement-ttl-minutes:30}") long settlementTtlMinutes
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.productSkuRepository = productSkuRepository;
        this.productNoticeRepository = productNoticeRepository;
        this.userAddressRepository = userAddressRepository;
        this.couponUserRepository = couponUserRepository;
        this.couponRepository = couponRepository;
        this.userPointAccountRepository = userPointAccountRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderItemRepository = tradeOrderItemRepository;
        this.tradeOrderAmountRepository = tradeOrderAmountRepository;
        this.settlementSessionStore = settlementSessionStore;
        this.eventPublisher = eventPublisher;
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
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
            OrderResponse order = persistOrder(userId, session.request(), calculation, request.remark(), clientRequestId);
            eventPublisher.publishEvent(new OrderCreatedEvent(
                order.id(),
                order.orderNo(),
                order.userId(),
                order.payAmount(),
                order.createdAt()
            ));
            return order;
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
        if (!"WAIT_PAY".equals(order.getOrderStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前订单不可取消");
        }
        order.setOrderStatus("CANCELED");
        order.setPayStatus("CLOSED");
        order.setCancelTime(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        tradeOrderRepository.save(order);
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse pay(Long userId, Long orderId) {
        TradeOrder order = tradeOrderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if ("PAID".equals(order.getPayStatus())) {
            return toOrderResponse(order);
        }
        if (!"WAIT_PAY".equals(order.getOrderStatus()) || !"UNPAID".equals(order.getPayStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前订单不可支付");
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getPayExpireTime() != null && order.getPayExpireTime().isBefore(now)) {
            order.setOrderStatus("CANCELED");
            order.setPayStatus("CLOSED");
            order.setCancelTime(now);
            order.setUpdatedAt(now);
            tradeOrderRepository.save(order);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单支付已超时");
        }
        order.setOrderStatus("WAIT_SHIP");
        order.setPayStatus("PAID");
        order.setDeliveryStatus(DELIVERY_UNSHIPPED);
        order.setPayTime(now);
        order.setUpdatedAt(now);
        tradeOrderRepository.save(order);
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse shipOrder(Long orderId, AdminShipOrderRequest request) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "?????"));
        if (!"WAIT_SHIP".equals(order.getOrderStatus()) || !"PAID".equals(order.getPayStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "????????");
        }
        Map<String, Object> beforeSnapshot = snapshotOrder(order);
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus("WAIT_RECEIVE");
        order.setDeliveryStatus(DELIVERY_SHIPPED);
        order.setLogisticsCompany(normalizeOptionalText(request.logisticsCompany()));
        order.setLogisticsNo(normalizeOptionalText(request.logisticsNo()));
        order.setDeliveryRemark(normalizeOptionalText(request.deliveryRemark()));
        order.setDeliveryTime(now);
        order.setUpdatedAt(now);
        tradeOrderRepository.save(order);
        operationLogService.record("ORDER_SHIP", "ORDER", orderId, beforeSnapshot, snapshotOrder(order), "????");
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse updateDeliveryStatus(Long orderId, AdminUpdateDeliveryStatusRequest request) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "?????"));
        if (DELIVERY_UNSHIPPED.equals(order.getDeliveryStatus()) || order.getDeliveryTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "??????");
        }
        String targetStatus = normalizeDeliveryStatus(request.deliveryStatus());
        Map<String, Object> beforeSnapshot = snapshotOrder(order);
        LocalDateTime now = LocalDateTime.now();
        order.setDeliveryStatus(targetStatus);
        order.setDeliveryRemark(normalizeOptionalText(request.deliveryRemark()));
        if (DELIVERY_DELIVERED.equals(targetStatus)) {
            order.setOrderStatus("FINISHED");
            order.setFinishTime(now);
        } else {
            order.setOrderStatus("WAIT_RECEIVE");
        }
        order.setUpdatedAt(now);
        tradeOrderRepository.save(order);
        operationLogService.record("ORDER_DELIVERY_STATUS_UPDATE", "ORDER", orderId, beforeSnapshot, snapshotOrder(order), "????");
        return toOrderResponse(order);
    }

    private Map<String, Object> snapshotOrder(TradeOrder order) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", order.getId());
        snapshot.put("orderNo", order.getOrderNo());
        snapshot.put("orderStatus", order.getOrderStatus());
        snapshot.put("payStatus", order.getPayStatus());
        snapshot.put("deliveryStatus", order.getDeliveryStatus());
        snapshot.put("logisticsCompany", order.getLogisticsCompany());
        snapshot.put("logisticsNo", order.getLogisticsNo());
        snapshot.put("deliveryRemark", order.getDeliveryRemark());
        snapshot.put("deliveryTime", order.getDeliveryTime());
        snapshot.put("finishTime", order.getFinishTime());
        return snapshot;
    }

    private SettlementCalculation calculate(Long userId, ConfirmOrderRequest request) {
        String sourceType = normalizeSourceType(request.sourceType());
        // 先把购物车/立即购买两种入口统一整理成结算项，后续金额逻辑都基于它。
        List<SettlementItem> items = resolveItems(userId, sourceType, request);
        validateItems(items);
        UserAddress address = resolveAddress(userId, request.addressId());
        int productAmount = items.stream().mapToInt(SettlementItem::totalAmount).sum();
        int productDiscountAmount = 0;
        int freightAmount = calculateFreight(items);
        CouponSelection couponSelection = selectCoupon(userId, request.couponUserId(), productAmount);
        PointSelection pointSelection = selectPoints(userId, request.usePoints(), items, productAmount - productDiscountAmount - couponSelection.discountAmount());
        int payAmount = productAmount - productDiscountAmount - couponSelection.discountAmount() - pointSelection.deductionAmount() + freightAmount;
        payAmount = Math.max(payAmount, 0);
        List<PromotionTraceResponse> promotionTraces = buildPromotionTraces(userId, items, couponSelection, pointSelection);
        OrderAmountResponse amount = toAmount(productAmount, productDiscountAmount, couponSelection.discountAmount(), pointSelection.deductionAmount(), freightAmount, 0, payAmount, promotionTraces, writePromotionJson(promotionTraces));
        return new SettlementCalculation(sourceType, address, items, couponSelection.selectedCouponUser(), couponSelection.availableCoupons(), pointSelection, amount);
    }

    private List<SettlementItem> resolveItems(Long userId, String sourceType, ConfirmOrderRequest request) {
        if (CART.equals(sourceType)) {
            // 购物车结算未指定条目时，默认结算当前用户购物车中的全部商品。
            List<CartItem> cartItems = request.cartItemIds() == null || request.cartItemIds().isEmpty()
                ? cartItemRepository.findByUserIdOrderByIdDesc(userId)
                : request.cartItemIds().stream()
                    .map(id -> cartItemRepository.findByIdAndUserId(id, userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "购物车商品不存在")))
                    .toList();
            if (cartItems.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要结算的商品");
            }
            return cartItems.stream().map(item -> toSettlementItem(item, item.getSkuId(), item.getQuantity())).toList();
        }
        int quantity = request.quantity() == null ? 1 : request.quantity();
        if (request.skuId() == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择商品规格和数量");
        }
        return List.of(toSettlementItem(null, request.skuId(), quantity));
    }

    private SettlementItem toSettlementItem(CartItem cartItem, Long skuId, int quantity) {
        ProductSku sku = productSkuRepository.findById(skuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品规格已失效"));
        Product product = productRepository.findById(sku.getProductId())
            .filter(item -> !Boolean.TRUE.equals(item.getDeletedFlag()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不存在"));
        ProductNotice notice = productNoticeRepository.findByProductIdAndEnabledFlagTrue(product.getId()).orElse(null);
        return new SettlementItem(cartItem == null ? null : cartItem.getId(), product, sku, notice, quantity);
    }

    private void validateItems(List<SettlementItem> items) {
        for (SettlementItem item : items) {
            // 结算前统一检查商品状态、SKU 状态和库存，避免脏数据进入下单流程。
            if (!ON_SALE.equals(item.product().getSaleStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部分商品已下架，请重新确认");
            }
            if (!ENABLED.equals(item.sku().getSkuStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部分商品规格已失效，请重新选择");
            }
            if (item.sku().getStock() < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足，请修改购买数量");
            }
        }
        boolean hasNormalSingleBuy = items.stream().anyMatch(item -> Boolean.TRUE.equals(item.product().getAllowSingleBuy()));
        boolean hasOnlyCannotSingleBuy = items.stream().allMatch(item -> !Boolean.TRUE.equals(item.product().getAllowSingleBuy()));
        // 不可单独购买的商品不能脱离搭售场景单独下单。
        if (hasOnlyCannotSingleBuy || (!hasNormalSingleBuy && items.size() > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不可单独购买");
        }
        long deliveryTypeCount = items.stream()
            .map(item -> item.product().getDeliveryType())
            .filter(deliveryType -> deliveryType != null && !deliveryType.isBlank())
            .distinct()
            .count();
        if (deliveryTypeCount > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Different delivery types cannot be checked out together");
        }
    }

    private UserAddress resolveAddress(Long userId, Long addressId) {
        if (addressId != null) {
            return userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "收货地址不存在"));
        }
        return userAddressRepository.findFirstByUserIdAndDefaultFlagTrue(userId)
            .or(() -> userAddressRepository.findFirstByUserIdOrderByIdAsc(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先添加收货地址"));
    }

    private int calculateFreight(List<SettlementItem> items) {
        boolean hasColdChain = items.stream().anyMatch(item -> "COLD_CHAIN".equals(item.product().getDeliveryType()));
        return hasColdChain ? COLD_CHAIN_FREIGHT : NORMAL_FREIGHT;
    }

    private CouponSelection selectCoupon(Long userId, Long requestedCouponUserId, int productAmount) {
        List<CouponUser> userCoupons = couponUserRepository.findByUserIdAndUserCouponStatus(userId, "UNUSED");
        Map<Long, Coupon> couponMap = couponRepository.findAllById(userCoupons.stream().map(CouponUser::getCouponId).toList()).stream()
            .collect(Collectors.toMap(Coupon::getId, coupon -> coupon));
        // 仅保留状态有效、满足门槛且处于可用时间窗内的优惠券。
        List<CouponCandidate> candidates = userCoupons.stream()
            .map(userCoupon -> new CouponCandidate(userCoupon, couponMap.get(userCoupon.getCouponId())))
            .filter(candidate -> candidate.coupon() != null)
            .filter(candidate -> "ENABLED".equals(candidate.coupon().getCouponStatus()))
            .filter(candidate -> productAmount >= candidate.coupon().getThresholdAmount())
            .filter(candidate -> {
                LocalDateTime now = LocalDateTime.now();
                return !now.isBefore(candidate.coupon().getUseStartTime()) && !now.isAfter(candidate.coupon().getUseEndTime());
            })
            .toList();
        CouponCandidate selected;
        if (requestedCouponUserId != null) {
            selected = candidates.stream()
                .filter(candidate -> candidate.userCoupon().getId().equals(requestedCouponUserId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券不可用"));
        } else {
            // 用户未指定时自动选最优券：先比优惠金额，再优先快过期的券。
            selected = candidates.stream()
                .max(Comparator
                    .comparing((CouponCandidate candidate) -> candidate.coupon().getDiscountAmount())
                    .thenComparing(candidate -> candidate.coupon().getUseEndTime(), Comparator.reverseOrder()))
                .orElse(null);
        }
        CouponCandidate finalSelected = selected;
        List<ConfirmCouponResponse> responses = candidates.stream()
            .map(candidate -> toCouponResponse(candidate.userCoupon(), candidate.coupon(), finalSelected != null && finalSelected.userCoupon().getId().equals(candidate.userCoupon().getId())))
            .toList();
        return new CouponSelection(
            selected == null ? null : selected.userCoupon(),
            responses,
            selected == null ? 0 : selected.coupon().getDiscountAmount(),
            selected == null ? null : selected.coupon().getId(),
            selected == null ? null : selected.coupon().getName()
        );
    }

    private PointSelection selectPoints(Long userId, Boolean usePoints, List<SettlementItem> items, int remainingAmount) {
        boolean visible = items.stream().anyMatch(item -> Boolean.TRUE.equals(item.product().getSupportPointDeduction()));
        UserPointAccount account = userPointAccountRepository.findByUserId(userId).orElse(null);
        int availablePoints = account == null ? 0 : account.getAvailablePoints();
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
        order.setOrderStatus("WAIT_PAY");
        order.setPayStatus("UNPAID");
        order.setDeliveryStatus(DELIVERY_UNSHIPPED);
        order.setAftersaleStatus("NONE");
        order.setSourceType(calculation.sourceType());
        order.setTotalAmount(calculation.amount().productAmount());
        order.setDiscountAmount(calculation.amount().productDiscountAmount());
        order.setCouponAmount(calculation.amount().couponDiscountAmount());
        order.setPointAmount(calculation.amount().pointDiscountAmount());
        order.setFreightAmount(calculation.amount().freightAmount());
        order.setPayAmount(calculation.amount().payAmount());
        order.setReceiverName(calculation.address().getReceiverName());
        order.setReceiverMobile(calculation.address().getReceiverMobile());
        order.setReceiverAddress(addressText(calculation.address()));
        order.setRemark(createRemark == null ? request.remark() : createRemark);
        order.setPayExpireTime(now.plusMinutes(30));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        TradeOrder savedOrder = tradeOrderRepository.save(order);

        List<ItemRefundSnapshot> refundSnapshots = buildRefundSnapshots(calculation);
        for (int index = 0; index < calculation.items().size(); index++) {
            SettlementItem item = calculation.items().get(index);
            ItemRefundSnapshot refundSnapshot = refundSnapshots.get(index);
            // 通过行级锁再次扣减库存并增加锁定库存，避免并发下单超卖。
            ProductSku sku = productSkuRepository.findByIdForUpdate(item.sku().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品规格已失效"));
            if (!ENABLED.equals(sku.getSkuStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部分商品规格已失效，请重新选择");
            }
            if (sku.getStock() < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足，请修改购买数量");
            }
            sku.setStock(sku.getStock() - item.quantity());
            sku.setLockedStock(sku.getLockedStock() + item.quantity());
            sku.setUpdatedAt(now);
            productSkuRepository.save(sku);

            TradeOrderItem orderItem = new TradeOrderItem();
            orderItem.setOrderId(savedOrder.getId());
            orderItem.setProductId(item.product().getId());
            orderItem.setSkuId(sku.getId());
            orderItem.setProductName(item.product().getName());
            orderItem.setSkuName(sku.getSkuName());
            orderItem.setSpecJson(sku.getSpecJson());
            orderItem.setCategoryId(item.product().getCategoryId());
            orderItem.setBrandName(item.product().getBrandName());
            orderItem.setProductImageUrl(item.product().getMainImageUrl());
            orderItem.setSalePrice(sku.getSalePrice());
            orderItem.setQuantity(item.quantity());
            orderItem.setTotalAmount(item.totalAmount());
            orderItem.setDiscountAmount(0);
            orderItem.setPayAmount(refundSnapshot.itemPayAmount());
            orderItem.setCouponShareAmount(refundSnapshot.couponShareAmount());
            orderItem.setPointShareAmount(refundSnapshot.pointShareAmount());
            orderItem.setFreightShareAmount(refundSnapshot.freightShareAmount());
            orderItem.setPromotionShareJson(writePromotionJson(promotionSharesForIndex(calculation.amount().promotionTraces(), index)));
            orderItem.setDeliveryType(item.product().getDeliveryType());
            orderItem.setSupportRefund(defaultBool(item.product().getSupportRefund(), true));
            orderItem.setSupportPointDeduction(defaultBool(item.product().getSupportPointDeduction(), false));
            orderItem.setSnapshotVersion(defaultInt(item.product().getSnapshotVersion(), 1));
            orderItem.setAftersaleQuantity(0);
            orderItem.setRefundableQuantity(item.quantity());
            orderItem.setRefundedQuantity(0);
            orderItem.setRefundAmount(0);
            orderItem.setRefundableAmount(refundSnapshot.refundableAmount());
            orderItem.setRefundStatus("NONE");
            orderItem.setCreatedAt(now);
            tradeOrderItemRepository.save(orderItem);
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

        if (calculation.selectedCouponUser() != null) {
            // 优惠券在订单真正创建成功后再核销，避免只在结算页就占用。
            CouponUser couponUser = calculation.selectedCouponUser();
            couponUser.setUserCouponStatus("USED");
            couponUser.setUsedAt(now);
            couponUser.setOrderId(savedOrder.getId());
            couponUserRepository.save(couponUser);
        }

        if (CART.equals(calculation.sourceType())) {
            // 购物车来源的订单创建成功后，再删除对应购物车项。
            List<Long> cartItemIds = calculation.items().stream()
                .map(SettlementItem::cartItemId)
                .filter(id -> id != null)
                .toList();
            cartItemRepository.deleteAllById(cartItemIds);
        }

        return toOrderResponse(savedOrder);
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
        ProductNotice notice = item.notice();
        return new ConfirmOrderItemResponse(
            item.cartItemId(),
            item.product().getId(),
            item.sku().getId(),
            item.product().getName(),
            item.sku().getSkuName(),
            item.product().getMainImageUrl(),
            item.sku().getSalePrice(),
            PriceFormatter.formatCents(item.sku().getSalePrice()),
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
            item.product().getAllowSingleBuy(),
            item.product().getSupportPointDeduction(),
            notice == null ? null : notice.getNoticeTitle(),
            notice == null ? null : notice.getNoticeContent()
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
            item.getSnapshotVersion()
        );
    }

    private OrderAddressResponse toAddress(UserAddress address) {
        return new OrderAddressResponse(
            address.getId(),
            address.getReceiverName(),
            address.getReceiverMobile(),
            address.getProvince(),
            address.getCity(),
            address.getDistrict(),
            address.getDetailAddress(),
            address.getDefaultFlag()
        );
    }

    private ConfirmCouponResponse toCouponResponse(CouponUser userCoupon, Coupon coupon, boolean selected) {
        return new ConfirmCouponResponse(
            userCoupon.getId(),
            coupon.getId(),
            coupon.getName(),
            coupon.getCouponType(),
            coupon.getThresholdAmount(),
            PriceFormatter.formatCents(coupon.getThresholdAmount()),
            coupon.getDiscountAmount(),
            PriceFormatter.formatCents(coupon.getDiscountAmount()),
            selected
        );
    }

    private List<PromotionTraceResponse> buildPromotionTraces(Long userId, List<SettlementItem> items, CouponSelection couponSelection, PointSelection pointSelection) {
        List<PromotionTraceResponse> traces = new ArrayList<>();
        if (couponSelection.discountAmount() > 0) {
            traces.add(new PromotionTraceResponse(
                "COUPON",
                couponSelection.selectedCouponUser() == null ? null : couponSelection.selectedCouponUser().getId().toString(),
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
                items.get(index).product().getId(),
                items.get(index).sku().getId(),
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

    private String addressText(UserAddress address) {
        return address.getProvince() + address.getCity() + address.getDistrict() + address.getDetailAddress();
    }

    private String normalizeSourceType(String sourceType) {
        if (BUY_NOW.equals(sourceType)) {
            return BUY_NOW;
        }
        return CART;
    }

    private String normalizeDeliveryStatus(String status) {
        return switch (status == null ? "" : status.trim().toUpperCase()) {
            case DELIVERY_SHIPPED -> DELIVERY_SHIPPED;
            case DELIVERY_IN_TRANSIT -> DELIVERY_IN_TRANSIT;
            case DELIVERY_DELIVERED -> DELIVERY_DELIVERED;
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

    private Boolean defaultBool(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Integer defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private record SettlementItem(Long cartItemId, Product product, ProductSku sku, ProductNotice notice, int quantity) {
        int totalAmount() {
            return sku.getSalePrice() * quantity;
        }
    }

    private record SettlementCalculation(
        String sourceType,
        UserAddress address,
        List<SettlementItem> items,
        CouponUser selectedCouponUser,
        List<ConfirmCouponResponse> availableCoupons,
        PointSelection pointSelection,
        OrderAmountResponse amount
    ) {
    }

    private record CouponCandidate(CouponUser userCoupon, Coupon coupon) {
    }

    private record CouponSelection(CouponUser selectedCouponUser, List<ConfirmCouponResponse> availableCoupons, int discountAmount, Long couponId, String name) {
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

}
