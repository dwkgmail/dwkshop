package com.dwkshop.backend.cart;

import com.dwkshop.backend.auth.AuthContext;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart/items")
public class CartController {

    private static final Long DEFAULT_USER_ID = 1L;

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse listItems(@RequestParam(required = false) Long userId) {
        return cartService.listItems(resolveUserId(userId));
    }

    @PostMapping
    public CartResponse addItem(
        @RequestParam(required = false) Long userId,
        @Valid @RequestBody AddCartItemRequest request
    ) {
        return cartService.addItem(resolveUserId(userId), request);
    }

    @PutMapping("/{id}")
    public CartResponse updateQuantity(
        @PathVariable Long id,
        @RequestParam(required = false) Long userId,
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.updateQuantity(resolveUserId(userId), id, request);
    }

    @DeleteMapping("/{id}")
    public CartResponse deleteItem(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        return cartService.deleteItem(resolveUserId(userId), id);
    }

    @PutMapping("/{id}/checked")
    public CartResponse updateChecked(
        @PathVariable Long id,
        @RequestParam(required = false) Long userId,
        @Valid @RequestBody CheckedRequest request
    ) {
        return cartService.updateChecked(resolveUserId(userId), id, request.checked());
    }

    @PutMapping("/check-all")
    public CartResponse checkAll(
        @RequestParam(required = false) Long userId,
        @Valid @RequestBody CheckedRequest request
    ) {
        return cartService.checkAll(resolveUserId(userId), request.checked());
    }

    private Long resolveUserId(Long userId) {
        if (userId != null) {
            return userId;
        }
        return AuthContext.currentUserId().orElse(DEFAULT_USER_ID);
    }
}
