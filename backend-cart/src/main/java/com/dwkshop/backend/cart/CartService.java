package com.dwkshop.backend.cart;

import com.dwkshop.backend.cart.dto.AddCartItemRequest;
import com.dwkshop.backend.cart.dto.CartItemResponse;
import com.dwkshop.backend.cart.dto.CartItemSnapshotResponse;
import com.dwkshop.backend.cart.dto.CartResponse;
import com.dwkshop.backend.cart.dto.UpdateCartItemRequest;
import com.dwkshop.backend.domain.entity.CartItem;
import com.dwkshop.backend.domain.repository.CartItemRepository;
import com.dwkshop.backend.util.PriceFormatter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final ProductCatalogClient productCatalogClient;

    public CartService(CartItemRepository cartItemRepository, ProductCatalogClient productCatalogClient) {
        this.cartItemRepository = cartItemRepository;
        this.productCatalogClient = productCatalogClient;
    }

    @Transactional(readOnly = true)
    public CartResponse listItems(Long userId) {
        return buildCartResponse(userId, cartItemRepository.findByUserIdOrderByIdDesc(userId));
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        ProductSkuSnapshot sku = productCatalogClient.getSkuSnapshot(request.skuId());
        validateAddable(sku, request.quantity());

        LocalDateTime now = LocalDateTime.now();
        CartItem item = cartItemRepository.findByUserIdAndSkuId(userId, sku.skuId()).orElse(null);
        if (item == null) {
            item = new CartItem();
            item.setUserId(userId);
            item.setProductId(sku.productId());
            item.setSkuId(sku.skuId());
            item.setQuantity(request.quantity());
            item.setCheckedFlag(true);
            item.setItemStatus(NORMAL);
            item.setCreatedAt(now);
        } else {
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
        ProductSkuSnapshot sku = productCatalogClient.getSkuSnapshot(item.getSkuId());
        validateAddable(sku, request.quantity());
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
        CartItemState state = evaluateState(item, productCatalogClient.getSkuSnapshot(item.getSkuId()));
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
            CartItemState state = evaluateState(item, productCatalogClient.getSkuSnapshot(item.getSkuId()));
            item.setItemStatus(state.status());
            item.setCheckedFlag(Boolean.TRUE.equals(checked) && state.canCheck());
            item.setUpdatedAt(LocalDateTime.now());
        }
        cartItemRepository.saveAll(items);
        return listItems(userId);
    }

    @Transactional(readOnly = true)
    public List<CartItemSnapshotResponse> listItemSnapshots(Long userId, List<Long> itemIds) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByIdDesc(userId);
        if (itemIds != null && !itemIds.isEmpty()) {
            items = items.stream()
                .filter(item -> itemIds.contains(item.getId()))
                .toList();
        }
        return items.stream()
            .map(this::toSnapshot)
            .toList();
    }

    @Transactional
    public void deleteItemSnapshots(Long userId, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        List<CartItem> items = itemIds.stream()
            .map(id -> findUserCartItem(userId, id))
            .toList();
        cartItemRepository.deleteAll(items);
    }

    private void validateAddable(ProductSkuSnapshot sku, Integer quantity) {
        if (sku == null || Boolean.TRUE.equals(sku.deletedFlag())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product does not exist");
        }
        if (!ON_SALE.equals(sku.saleStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product is off sale");
        }
        if (!ENABLED.equals(sku.skuStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU is invalid");
        }
        validateStock(sku, quantity);
        if (!Boolean.TRUE.equals(sku.allowCart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product cannot be added to cart");
        }
    }

    private void validateStock(ProductSkuSnapshot sku, Integer quantity) {
        if (sku.stock() < quantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock is not enough");
        }
    }

    private CartResponse buildCartResponse(Long userId, List<CartItem> items) {
        Map<Long, ProductSkuSnapshot> skuMap = items.stream()
            .map(CartItem::getSkuId)
            .distinct()
            .collect(Collectors.toMap(skuId -> skuId, productCatalogClient::getSkuSnapshot));

        List<CartItemResponse> responses = items.stream()
            .map(item -> toResponse(item, skuMap.get(item.getSkuId())))
            .toList();
        Integer badgeCount = responses.stream().map(CartItemResponse::quantity).reduce(0, Integer::sum);
        Integer estimatedAmount = responses.stream()
            .filter(item -> Boolean.TRUE.equals(item.checked()) && NORMAL.equals(item.status()))
            .map(CartItemResponse::estimatedAmount)
            .reduce(0, Integer::sum);
        return new CartResponse(userId, badgeCount, estimatedAmount, PriceFormatter.formatCents(estimatedAmount), responses);
    }

    private CartItemResponse toResponse(CartItem item, ProductSkuSnapshot sku) {
        CartItemState state = evaluateState(item, sku);
        boolean checked = Boolean.TRUE.equals(item.getCheckedFlag()) && state.canCheck();
        Integer price = sku == null ? 0 : sku.salePrice();
        Integer amount = price * item.getQuantity();
        return new CartItemResponse(
            item.getId(),
            item.getProductId(),
            item.getSkuId(),
            sku == null ? null : sku.productName(),
            sku == null ? null : sku.productImageUrl(),
            sku == null ? null : sku.skuName(),
            sku == null ? null : sku.specJson(),
            price,
            PriceFormatter.formatCents(price),
            item.getQuantity(),
            sku == null ? 0 : sku.stock(),
            checked,
            sku != null && Boolean.TRUE.equals(sku.allowCart()),
            sku != null && Boolean.TRUE.equals(sku.allowSingleBuy()),
            sku != null && Boolean.TRUE.equals(sku.supportPointDeduction()),
            state.status(),
            state.message(),
            state.canCheck(),
            amount,
            PriceFormatter.formatCents(amount)
        );
    }

    private CartItemSnapshotResponse toSnapshot(CartItem item) {
        return new CartItemSnapshotResponse(
            item.getId(),
            item.getUserId(),
            item.getProductId(),
            item.getSkuId(),
            item.getQuantity(),
            item.getCheckedFlag(),
            item.getItemStatus()
        );
    }

    private CartItemState evaluateState(CartItem item, ProductSkuSnapshot sku) {
        if (sku == null || Boolean.TRUE.equals(sku.deletedFlag())) {
            return new CartItemState(OFF_SALE, "Product does not exist", false);
        }
        if (!ON_SALE.equals(sku.saleStatus())) {
            return new CartItemState(OFF_SALE, "Product is off sale", false);
        }
        if (!ENABLED.equals(sku.skuStatus())) {
            return new CartItemState(SKU_INVALID, "SKU is invalid", false);
        }
        if (!Boolean.TRUE.equals(sku.allowCart())) {
            return new CartItemState(NOT_ALLOW_CART, "Product cannot be added to cart", false);
        }
        if (sku.stock() < item.getQuantity()) {
            return new CartItemState(STOCK_NOT_ENOUGH, "Stock is not enough", false);
        }
        return new CartItemState(NORMAL, null, true);
    }

    private CartItem findUserCartItem(Long userId, Long itemId) {
        return cartItemRepository.findByIdAndUserId(itemId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item does not exist"));
    }

    private record CartItemState(String status, String message, boolean canCheck) {
    }
}
