package com.dwkshop.backend.cart;

import com.dwkshop.backend.cart.dto.AddCartItemRequest;
import com.dwkshop.backend.cart.dto.CartItemResponse;
import com.dwkshop.backend.cart.dto.CartResponse;
import com.dwkshop.backend.cart.dto.UpdateCartItemRequest;
import com.dwkshop.backend.domain.entity.CartItem;
import com.dwkshop.backend.domain.entity.Product;
import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.repository.CartItemRepository;
import com.dwkshop.backend.domain.repository.ProductRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.product.PriceFormatter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CartService {

    private static final String ON_SALE = "ON_SALE";
    private static final String ENABLED = "ENABLED";
    private static final String NORMAL = "NORMAL";
    private static final String OFF_SALE = "OFF_SALE";
    private static final String SKU_INVALID = "SKU_INVALID";
    private static final String STOCK_NOT_ENOUGH = "STOCK_NOT_ENOUGH";
    private static final String NOT_ALLOW_CART = "NOT_ALLOW_CART";

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;

    public CartService(
        CartItemRepository cartItemRepository,
        ProductRepository productRepository,
        ProductSkuRepository productSkuRepository
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.productSkuRepository = productSkuRepository;
    }

    @Transactional
    public CartResponse listItems(Long userId) {
        return buildCartResponse(userId, cartItemRepository.findByUserIdOrderByIdDesc(userId));
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        ProductSku sku = productSkuRepository.findById(request.skuId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品规格不存在"));
        Product product = productRepository.findById(sku.getProductId())
            .filter(item -> !Boolean.TRUE.equals(item.getDeletedFlag()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不存在"));

        validateAddable(product, sku, request.quantity());

        LocalDateTime now = LocalDateTime.now();
        CartItem item = cartItemRepository.findByUserIdAndSkuId(userId, sku.getId()).orElse(null);
        if (item == null) {
            // 同一用户同一 SKU 在购物车中只保留一条记录，首次加入时直接创建。
            item = new CartItem();
            item.setUserId(userId);
            item.setProductId(product.getId());
            item.setSkuId(sku.getId());
            item.setQuantity(request.quantity());
            item.setCheckedFlag(true);
            item.setItemStatus(NORMAL);
            item.setCreatedAt(now);
        } else {
            // 已存在则合并数量，避免购物车出现重复行。
            int mergedQuantity = item.getQuantity() + request.quantity();
            validateStock(sku, mergedQuantity);
            item.setQuantity(mergedQuantity);
            item.setCheckedFlag(true);
            item.setItemStatus(NORMAL);
        }
        item.setUpdatedAt(now);
        cartItemRepository.save(item);
        return listItems(userId);
    }

    @Transactional
    public CartResponse updateQuantity(Long userId, Long itemId, UpdateCartItemRequest request) {
        CartItem item = findUserCartItem(userId, itemId);
        ProductSku sku = productSkuRepository.findById(item.getSkuId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品规格已失效"));
        Product product = productRepository.findById(item.getProductId())
            .filter(candidate -> !Boolean.TRUE.equals(candidate.getDeletedFlag()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不存在"));
        validateAddable(product, sku, request.quantity());
        item.setQuantity(request.quantity());
        item.setItemStatus(NORMAL);
        item.setCheckedFlag(true);
        item.setUpdatedAt(LocalDateTime.now());
        cartItemRepository.save(item);
        return listItems(userId);
    }

    @Transactional
    public CartResponse deleteItem(Long userId, Long itemId) {
        CartItem item = findUserCartItem(userId, itemId);
        cartItemRepository.delete(item);
        return listItems(userId);
    }

    @Transactional
    public CartResponse updateChecked(Long userId, Long itemId, Boolean checked) {
        CartItem item = findUserCartItem(userId, itemId);
        CartItemState state = evaluateState(item, loadProduct(item.getProductId()), loadSku(item.getSkuId()));
        if (Boolean.TRUE.equals(checked) && !state.canCheck()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, state.message());
        }
        item.setCheckedFlag(checked);
        item.setItemStatus(state.status());
        item.setUpdatedAt(LocalDateTime.now());
        cartItemRepository.save(item);
        return listItems(userId);
    }

    @Transactional
    public CartResponse checkAll(Long userId, Boolean checked) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByIdDesc(userId);
        for (CartItem item : items) {
            CartItemState state = evaluateState(item, loadProduct(item.getProductId()), loadSku(item.getSkuId()));
            item.setItemStatus(state.status());
            item.setCheckedFlag(Boolean.TRUE.equals(checked) && state.canCheck());
            item.setUpdatedAt(LocalDateTime.now());
        }
        cartItemRepository.saveAll(items);
        return listItems(userId);
    }

    private void validateAddable(Product product, ProductSku sku, Integer quantity) {
        if (!ON_SALE.equals(product.getSaleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品已下架");
        }
        if (!ENABLED.equals(sku.getSkuStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品规格已失效");
        }
        validateStock(sku, quantity);
        if (!Boolean.TRUE.equals(product.getAllowCart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该商品不允许加入购物车");
        }
    }

    private void validateStock(ProductSku sku, Integer quantity) {
        if (sku.getStock() < quantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足");
        }
    }

    private CartResponse buildCartResponse(Long userId, List<CartItem> items) {
        // 批量加载商品和 SKU，统一组装购物车响应，减少数据库往返次数。
        Map<Long, Product> productMap = productRepository.findAllById(items.stream().map(CartItem::getProductId).toList())
            .stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductSku> skuMap = productSkuRepository.findAllById(items.stream().map(CartItem::getSkuId).toList())
            .stream()
            .collect(Collectors.toMap(ProductSku::getId, Function.identity()));

        syncItemStates(items, productMap, skuMap);

        List<CartItemResponse> responses = items.stream()
            .map(item -> toResponse(item, productMap.get(item.getProductId()), skuMap.get(item.getSkuId())))
            .toList();
        Integer badgeCount = responses.stream().map(CartItemResponse::quantity).reduce(0, Integer::sum);
        Integer estimatedAmount = responses.stream()
            .filter(item -> Boolean.TRUE.equals(item.checked()) && NORMAL.equals(item.status()))
            .map(CartItemResponse::estimatedAmount)
            .reduce(0, Integer::sum);
        CartCheckoutState checkoutState = evaluateCheckout(responses, productMap);
        return new CartResponse(
            userId,
            badgeCount,
            estimatedAmount,
            PriceFormatter.formatCents(estimatedAmount),
            checkoutState.available(),
            checkoutState.message(),
            checkoutState.invalidItemCount(),
            checkoutState.selectedItemCount(),
            responses
        );
    }

    private void syncItemStates(List<CartItem> items, Map<Long, Product> productMap, Map<Long, ProductSku> skuMap) {
        LocalDateTime now = LocalDateTime.now();
        List<CartItem> changedItems = items.stream()
            .filter(item -> {
                CartItemState state = evaluateState(item, productMap.get(item.getProductId()), skuMap.get(item.getSkuId()));
                boolean checked = Boolean.TRUE.equals(item.getCheckedFlag()) && state.canCheck();
                return !state.status().equals(item.getItemStatus()) || !Boolean.valueOf(checked).equals(item.getCheckedFlag());
            })
            .peek(item -> {
                CartItemState state = evaluateState(item, productMap.get(item.getProductId()), skuMap.get(item.getSkuId()));
                item.setItemStatus(state.status());
                item.setCheckedFlag(Boolean.TRUE.equals(item.getCheckedFlag()) && state.canCheck());
                item.setUpdatedAt(now);
            })
            .toList();
        if (!changedItems.isEmpty()) {
            cartItemRepository.saveAll(changedItems);
        }
    }

    private CartItemResponse toResponse(CartItem item, Product product, ProductSku sku) {
        CartItemState state = evaluateState(item, product, sku);
        boolean checked = Boolean.TRUE.equals(item.getCheckedFlag()) && state.canCheck();
        Integer price = sku == null ? 0 : sku.getSalePrice();
        Integer amount = price * item.getQuantity();
        return new CartItemResponse(
            item.getId(),
            item.getProductId(),
            item.getSkuId(),
            product == null ? null : product.getName(),
            product == null ? null : product.getMainImageUrl(),
            sku == null ? null : sku.getSkuName(),
            sku == null ? null : sku.getSpecJson(),
            price,
            PriceFormatter.formatCents(price),
            item.getQuantity(),
            sku == null ? 0 : sku.getStock(),
            checked,
            product != null && Boolean.TRUE.equals(product.getAllowCart()),
            product != null && Boolean.TRUE.equals(product.getAllowSingleBuy()),
            product != null && Boolean.TRUE.equals(product.getSupportPointDeduction()),
            state.status(),
            state.message(),
            state.canCheck(),
            amount,
            PriceFormatter.formatCents(amount)
        );
    }

    private CartItemState evaluateState(CartItem item, Product product, ProductSku sku) {
        // 购物车项是否可勾选，由商品状态、SKU 状态和库存实时共同决定。
        if (product == null || Boolean.TRUE.equals(product.getDeletedFlag())) {
            return new CartItemState(OFF_SALE, "商品不存在", false);
        }
        if (!ON_SALE.equals(product.getSaleStatus())) {
            return new CartItemState(OFF_SALE, "商品已下架", false);
        }
        if (sku == null || !ENABLED.equals(sku.getSkuStatus())) {
            return new CartItemState(SKU_INVALID, "商品规格已失效", false);
        }
        if (!Boolean.TRUE.equals(product.getAllowCart())) {
            return new CartItemState(NOT_ALLOW_CART, "该商品不允许加入购物车", false);
        }
        if (sku.getStock() < item.getQuantity()) {
            return new CartItemState(STOCK_NOT_ENOUGH, "库存不足", false);
        }
        return new CartItemState(NORMAL, null, true);
    }

    private CartCheckoutState evaluateCheckout(List<CartItemResponse> responses, Map<Long, Product> productMap) {
        int invalidItemCount = (int) responses.stream()
            .filter(item -> !NORMAL.equals(item.status()))
            .count();
        List<CartItemResponse> selectedItems = responses.stream()
            .filter(item -> Boolean.TRUE.equals(item.checked()) && Boolean.TRUE.equals(item.canCheck()))
            .toList();
        if (selectedItems.isEmpty()) {
            return new CartCheckoutState(false, "Please select items to checkout", invalidItemCount, 0);
        }
        if (selectedItems.stream().allMatch(item -> !Boolean.TRUE.equals(item.allowSingleBuy()))) {
            return new CartCheckoutState(false, "Product cannot be purchased separately", invalidItemCount, selectedItems.size());
        }
        long deliveryTypeCount = selectedItems.stream()
            .map(item -> productMap.get(item.productId()))
            .filter(product -> product != null && product.getDeliveryType() != null)
            .map(Product::getDeliveryType)
            .distinct()
            .count();
        if (deliveryTypeCount > 1) {
            return new CartCheckoutState(false, "Different delivery types cannot be checked out together", invalidItemCount, selectedItems.size());
        }
        return new CartCheckoutState(true, null, invalidItemCount, selectedItems.size());
    }

    private CartItem findUserCartItem(Long userId, Long itemId) {
        return cartItemRepository.findByIdAndUserId(itemId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "购物车商品不存在"));
    }

    private Product loadProduct(Long productId) {
        return productRepository.findById(productId).orElse(null);
    }

    private ProductSku loadSku(Long skuId) {
        return productSkuRepository.findById(skuId).orElse(null);
    }

    private record CartItemState(String status, String message, boolean canCheck) {
    }

    private record CartCheckoutState(boolean available, String message, int invalidItemCount, int selectedItemCount) {
    }
}
