package com.dwkshop.backend.cart;

import com.dwkshop.backend.cart.dto.CartResponse;
import com.dwkshop.backend.domain.entity.CartItem;
import com.dwkshop.backend.domain.repository.CartItemRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    private final CartItemRepository cartItemRepository = mock(CartItemRepository.class);
    private final ProductCatalogClient productCatalogClient = mock(ProductCatalogClient.class);
    private final CartService cartService = new CartService(cartItemRepository, productCatalogClient);

    @Test
    void listItemsPersistsOffSaleStateAndUnchecksInvalidCartItem() {
        CartItem item = new CartItem();
        item.setId(1L);
        item.setUserId(10L);
        item.setProductId(100L);
        item.setSkuId(200L);
        item.setQuantity(2);
        item.setCheckedFlag(true);
        item.setItemStatus("NORMAL");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        when(cartItemRepository.findByUserIdOrderByIdDesc(10L)).thenReturn(List.of(item));
        when(productCatalogClient.getSkuSnapshot(200L)).thenReturn(new ProductSkuSnapshot(
            100L,
            200L,
            1L,
            "Off Sale Product",
            "Brand",
            "/images/product.png",
            "OFF_SALE",
            "NORMAL",
            false,
            true,
            true,
            false,
            true,
            1,
            null,
            null,
            "Default",
            "{}",
            1000,
            10,
            "ENABLED"
        ));

        CartResponse response = cartService.listItems(10L);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).status()).isEqualTo("OFF_SALE");
        assertThat(response.items().get(0).checked()).isFalse();
        assertThat(item.getItemStatus()).isEqualTo("OFF_SALE");
        assertThat(item.getCheckedFlag()).isFalse();
        verify(cartItemRepository).saveAll(List.of(item));
    }

    @Test
    void listItemsMarksCheckoutUnavailableWhenOnlyBundleItemsAreSelected() {
        CartItem item = cartItem(1L, 100L, 200L, 1, true);

        when(cartItemRepository.findByUserIdOrderByIdDesc(10L)).thenReturn(List.of(item));
        when(productCatalogClient.getSkuSnapshot(200L)).thenReturn(snapshot(100L, 200L, "NORMAL", false));

        CartResponse response = cartService.listItems(10L);

        assertThat(response.checkoutAvailable()).isFalse();
        assertThat(response.checkoutMessage()).isEqualTo("Product cannot be purchased separately");
        assertThat(response.invalidItemCount()).isZero();
        assertThat(response.selectedItemCount()).isEqualTo(1);
    }

    @Test
    void listItemsMarksCheckoutUnavailableWhenSelectedItemsHaveDifferentDeliveryTypes() {
        CartItem normalItem = cartItem(1L, 100L, 200L, 1, true);
        CartItem coldChainItem = cartItem(2L, 101L, 201L, 1, true);

        when(cartItemRepository.findByUserIdOrderByIdDesc(10L)).thenReturn(List.of(normalItem, coldChainItem));
        when(productCatalogClient.getSkuSnapshot(200L)).thenReturn(snapshot(100L, 200L, "NORMAL", true));
        when(productCatalogClient.getSkuSnapshot(201L)).thenReturn(snapshot(101L, 201L, "COLD_CHAIN", true));

        CartResponse response = cartService.listItems(10L);

        assertThat(response.checkoutAvailable()).isFalse();
        assertThat(response.checkoutMessage()).isEqualTo("Different delivery types cannot be checked out together");
        assertThat(response.invalidItemCount()).isZero();
        assertThat(response.selectedItemCount()).isEqualTo(2);
    }

    private CartItem cartItem(Long id, Long productId, Long skuId, int quantity, boolean checked) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setUserId(10L);
        item.setProductId(productId);
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        item.setCheckedFlag(checked);
        item.setItemStatus("NORMAL");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }

    private ProductSkuSnapshot snapshot(Long productId, Long skuId, String deliveryType, boolean allowSingleBuy) {
        return new ProductSkuSnapshot(
            productId,
            skuId,
            1L,
            "Product " + productId,
            "Brand",
            "/images/product.png",
            "ON_SALE",
            deliveryType,
            false,
            true,
            allowSingleBuy,
            false,
            true,
            1,
            null,
            null,
            "Default",
            "{}",
            1000,
            10,
            "ENABLED"
        );
    }
}
