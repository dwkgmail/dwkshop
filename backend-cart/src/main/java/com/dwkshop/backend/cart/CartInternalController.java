package com.dwkshop.backend.cart;

import com.dwkshop.backend.cart.dto.CartItemSnapshotResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/carts")
public class CartInternalController {

    private final CartService cartService;

    public CartInternalController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{userId}/items")
    public List<CartItemSnapshotResponse> listCartItems(
        @PathVariable Long userId,
        @RequestParam(required = false) List<Long> ids
    ) {
        return cartService.listItemSnapshots(userId, ids);
    }

    @DeleteMapping("/{userId}/items")
    public void deleteCartItems(
        @PathVariable Long userId,
        @RequestParam List<Long> ids
    ) {
        cartService.deleteItemSnapshots(userId, ids);
    }
}
