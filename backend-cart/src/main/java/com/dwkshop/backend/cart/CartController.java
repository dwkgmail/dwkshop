package com.dwkshop.backend.cart;

import com.dwkshop.backend.auth.AuthContext;
import com.dwkshop.backend.auth.AuthException;
import com.dwkshop.backend.cart.dto.AddCartItemRequest;
import com.dwkshop.backend.cart.dto.CartResponse;
import com.dwkshop.backend.cart.dto.CheckedRequest;
import com.dwkshop.backend.cart.dto.UpdateCartItemRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart/items")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse listItems() {
        return cartService.listItems(currentUserId());
    }

    @PostMapping
    public CartResponse addItem(
        @Valid @RequestBody AddCartItemRequest request
    ) {
        return cartService.addItem(currentUserId(), request);
    }

    @PutMapping("/{id}")
    public CartResponse updateQuantity(
        @PathVariable Long id,
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.updateQuantity(currentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public CartResponse deleteItem(@PathVariable Long id) {
        return cartService.deleteItem(currentUserId(), id);
    }

    @PutMapping("/{id}/checked")
    public CartResponse updateChecked(
        @PathVariable Long id,
        @Valid @RequestBody CheckedRequest request
    ) {
        return cartService.updateChecked(currentUserId(), id, request.checked());
    }

    @PutMapping("/check-all")
    public CartResponse checkAll(
        @Valid @RequestBody CheckedRequest request
    ) {
        return cartService.checkAll(currentUserId(), request.checked());
    }

    private Long currentUserId() {
        return AuthContext.currentUserId().orElseThrow(() -> new AuthException("please login first"));
    }
}
