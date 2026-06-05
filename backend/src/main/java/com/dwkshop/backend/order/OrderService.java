package com.dwkshop.backend.order;

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
import com.dwkshop.backend.product.PriceFormatter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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
    private final Map<String, SettlementSession> settlementSessions = new ConcurrentHashMap<>();

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
        TradeOrderAmountRepository tradeOrderAmountRepository
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
    }

    @Transactional(readOnly = true)
    public ConfirmOrderResponse confirm(Long userId, ConfirmOrderRequest request) {
        SettlementCalculation calculation = calculate(userId, request);
        String token = "SETTLE-" + UUID.randomUUID();
        ConfirmOrderResponse response = toConfirmResponse(token, calculation, request.remark());
        settlementSessions.put(token, new SettlementSession(userId, request, response.amount().payAmount(), false));
        return response;
    }

    @Transactional
    public OrderResponse create(Long userId, CreateOrderRequest request) {
        SettlementSession session = settlementSessions.get(request.settlementToken());
        if (session == null || !session.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "璁㈠崟淇℃伅宸茶繃鏈燂紝璇烽噸鏂扮‘璁?");
        }
        synchronized (session) {
            if (session.used()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "璁㈠崟宸插垱寤猴紝璇峰嬁閲嶅鎻愪氦");
            }
            SettlementCalculation calculation = calculate(userId, session.request());
            if (!calculation.amount().payAmount().equals(request.expectedPayAmount())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "璁㈠崟閲戦宸插彉鍖栵紝璇烽噸鏂扮‘璁?");
            }
            if (!session.expectedPayAmount().equals(request.expectedPayAmount())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "璁㈠崟閲戦宸插彉鍖栵紝璇烽噸鏂扮‘璁?");
            }
            OrderResponse order = persistOrder(userId, session.request(), calculation, request.remark());
            session.markUsed();
            settlementSessions.remove(request.settlementToken());
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "璁㈠崟涓嶅瓨鍦?"));
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "璁㈠崟涓嶅瓨鍦?"));
        if (!"WAIT_PAY".equals(order.getOrderStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "褰撳墠璁㈠崟涓嶅彲鍙栨秷");
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if ("PAID".equals(order.getPayStatus())) {
            return toOrderResponse(order);
        }
        if (!"WAIT_PAY".equals(order.getOrderStatus()) || !"UNPAID".equals(order.getPayStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order cannot be paid");
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getPayExpireTime() != null && order.getPayExpireTime().isBefore(now)) {
            order.setOrderStatus("CANCELED");
            order.setPayStatus("CLOSED");
            order.setCancelTime(now);
            order.setUpdatedAt(now);
            tradeOrderRepository.save(order);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order payment expired");
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!"WAIT_SHIP".equals(order.getOrderStatus()) || !"PAID".equals(order.getPayStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前订单不可发货");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus("WAIT_RECEIVE");
        order.setDeliveryStatus(DELIVERY_SHIPPED);
        order.setLogisticsCompany(normalizeOptionalText(request.logisticsCompany()));
        order.setLogisticsNo(normalizeOptionalText(request.logisticsNo()));
        order.setDeliveryRemark(normalizeOptionalText(request.deliveryRemark()));
        order.setDeliveryTime(now);
        order.setUpdatedAt(now);
        tradeOrderRepository.save(order);
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse updateDeliveryStatus(Long orderId, AdminUpdateDeliveryStatusRequest request) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (DELIVERY_UNSHIPPED.equals(order.getDeliveryStatus()) || order.getDeliveryTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单尚未发货");
        }
        String targetStatus = normalizeDeliveryStatus(request.deliveryStatus());
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
        return toOrderResponse(order);
    }

    private SettlementCalculation calculate(Long userId, ConfirmOrderRequest request) {
        String sourceType = normalizeSourceType(request.sourceType());
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
        OrderAmountResponse amount = toAmount(productAmount, productDiscountAmount, couponSelection.discountAmount(), pointSelection.deductionAmount(), freightAmount, 0, payAmount);
        return new SettlementCalculation(sourceType, address, items, couponSelection.selectedCouponUser(), couponSelection.availableCoupons(), pointSelection, amount);
    }

    private List<SettlementItem> resolveItems(Long userId, String sourceType, ConfirmOrderRequest request) {
        if (CART.equals(sourceType)) {
            List<CartItem> cartItems = request.cartItemIds() == null || request.cartItemIds().isEmpty()
                ? cartItemRepository.findByUserIdOrderByIdDesc(userId)
                : request.cartItemIds().stream()
                    .map(id -> cartItemRepository.findByIdAndUserId(id, userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "璐墿杞﹀晢鍝佷笉瀛樺湪")))
                    .toList();
            if (cartItems.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "璇烽€夋嫨瑕佺粨绠楃殑鍟嗗搧");
            }
            return cartItems.stream().map(item -> toSettlementItem(item, item.getSkuId(), item.getQuantity())).toList();
        }
        int quantity = request.quantity() == null ? 1 : request.quantity();
        if (request.skuId() == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "璇烽€夋嫨鍟嗗搧瑙勬牸鍜屾暟閲?");
        }
        return List.of(toSettlementItem(null, request.skuId(), quantity));
    }

    private SettlementItem toSettlementItem(CartItem cartItem, Long skuId, int quantity) {
        ProductSku sku = productSkuRepository.findById(skuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "鍟嗗搧瑙勬牸宸插け鏁?"));
        Product product = productRepository.findById(sku.getProductId())
            .filter(item -> !Boolean.TRUE.equals(item.getDeletedFlag()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "鍟嗗搧涓嶅瓨鍦?"));
        ProductNotice notice = productNoticeRepository.findByProductIdAndEnabledFlagTrue(product.getId()).orElse(null);
        return new SettlementItem(cartItem == null ? null : cartItem.getId(), product, sku, notice, quantity);
    }

    private void validateItems(List<SettlementItem> items) {
        for (SettlementItem item : items) {
            if (!ON_SALE.equals(item.product().getSaleStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "閮ㄥ垎鍟嗗搧宸蹭笅鏋讹紝璇烽噸鏂扮‘璁?");
            }
            if (!ENABLED.equals(item.sku().getSkuStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "閮ㄥ垎鍟嗗搧瑙勬牸宸插け鏁堬紝璇烽噸鏂伴€夋嫨");
            }
            if (item.sku().getStock() < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搴撳瓨涓嶈冻锛岃淇敼璐拱鏁伴噺");
            }
        }
        boolean hasNormalSingleBuy = items.stream().anyMatch(item -> Boolean.TRUE.equals(item.product().getAllowSingleBuy()));
        boolean hasOnlyCannotSingleBuy = items.stream().allMatch(item -> !Boolean.TRUE.equals(item.product().getAllowSingleBuy()));
        if (hasOnlyCannotSingleBuy || (!hasNormalSingleBuy && items.size() > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "鍟嗗搧涓嶅彲鍗曠嫭璐拱");
        }
    }

    private UserAddress resolveAddress(Long userId, Long addressId) {
        if (addressId != null) {
            return userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "鏀惰揣鍦板潃鏃犳晥"));
        }
        return userAddressRepository.findFirstByUserIdAndDefaultFlagTrue(userId)
            .or(() -> userAddressRepository.findFirstByUserIdOrderByIdAsc(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "璇烽€夋嫨鏀惰揣鍦板潃"));
    }

    private int calculateFreight(List<SettlementItem> items) {
        boolean hasColdChain = items.stream().anyMatch(item -> "COLD_CHAIN".equals(item.product().getDeliveryType()));
        return hasColdChain ? COLD_CHAIN_FREIGHT : NORMAL_FREIGHT;
    }

    private CouponSelection selectCoupon(Long userId, Long requestedCouponUserId, int productAmount) {
        List<CouponUser> userCoupons = couponUserRepository.findByUserIdAndUserCouponStatus(userId, "UNUSED");
        Map<Long, Coupon> couponMap = couponRepository.findAllById(userCoupons.stream().map(CouponUser::getCouponId).toList()).stream()
            .collect(Collectors.toMap(Coupon::getId, coupon -> coupon));
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "浼樻儬鍒镐笉鍙敤"));
        } else {
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
        return new CouponSelection(selected == null ? null : selected.userCoupon(), responses, selected == null ? 0 : selected.coupon().getDiscountAmount());
    }

    private PointSelection selectPoints(Long userId, Boolean usePoints, List<SettlementItem> items, int remainingAmount) {
        boolean visible = items.stream().anyMatch(item -> Boolean.TRUE.equals(item.product().getSupportPointDeduction()));
        UserPointAccount account = userPointAccountRepository.findByUserId(userId).orElse(null);
        int availablePoints = account == null ? 0 : account.getAvailablePoints();
        boolean selected = visible && Boolean.TRUE.equals(usePoints) && availablePoints > 0;
        int deduction = selected ? Math.min(availablePoints / POINT_EXCHANGE_RATE, Math.max(remainingAmount, 0)) : 0;
        return new PointSelection(visible, availablePoints, deduction, selected);
    }

    private OrderResponse persistOrder(Long userId, ConfirmOrderRequest request, SettlementCalculation calculation, String createRemark) {
        LocalDateTime now = LocalDateTime.now();
        TradeOrder order = new TradeOrder();
        order.setOrderNo("SO" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now) + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        order.setUserId(userId);
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

        for (SettlementItem item : calculation.items()) {
            ProductSku sku = productSkuRepository.findByIdForUpdate(item.sku().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "鍟嗗搧瑙勬牸宸插け鏁?"));
            if (!ENABLED.equals(sku.getSkuStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "閮ㄥ垎鍟嗗搧瑙勬牸宸插け鏁堬紝璇烽噸鏂伴€夋嫨");
            }
            if (sku.getStock() < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搴撳瓨涓嶈冻锛岃淇敼璐拱鏁伴噺");
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
            orderItem.setProductImageUrl(item.product().getMainImageUrl());
            orderItem.setSalePrice(sku.getSalePrice());
            orderItem.setQuantity(item.quantity());
            orderItem.setTotalAmount(item.totalAmount());
            orderItem.setDiscountAmount(0);
            orderItem.setPayAmount(item.totalAmount());
            orderItem.setSupportRefund(true);
            orderItem.setAftersaleQuantity(0);
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
        amount.setCreatedAt(now);
        tradeOrderAmountRepository.save(amount);

        if (calculation.selectedCouponUser() != null) {
            CouponUser couponUser = calculation.selectedCouponUser();
            couponUser.setUserCouponStatus("USED");
            couponUser.setUsedAt(now);
            couponUser.setOrderId(savedOrder.getId());
            couponUserRepository.save(couponUser);
        }

        if (CART.equals(calculation.sourceType())) {
            List<Long> cartItemIds = calculation.items().stream()
                .map(SettlementItem::cartItemId)
                .filter(id -> id != null)
                .toList();
            cartItemRepository.deleteAllById(cartItemIds);
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

    private record CouponSelection(CouponUser selectedCouponUser, List<ConfirmCouponResponse> availableCoupons, int discountAmount) {
    }

    private record PointSelection(boolean visible, int availablePoints, int deductionAmount, boolean selected) {
    }

    private static final class SettlementSession {
        private final Long userId;
        private final ConfirmOrderRequest request;
        private final Integer expectedPayAmount;
        private boolean used;

        private SettlementSession(Long userId, ConfirmOrderRequest request, Integer expectedPayAmount, boolean used) {
            this.userId = userId;
            this.request = request;
            this.expectedPayAmount = expectedPayAmount;
            this.used = used;
        }

        Long userId() {
            return userId;
        }

        ConfirmOrderRequest request() {
            return request;
        }

        Integer expectedPayAmount() {
            return expectedPayAmount;
        }

        boolean used() {
            return used;
        }

        void markUsed() {
            this.used = true;
        }
    }
}
