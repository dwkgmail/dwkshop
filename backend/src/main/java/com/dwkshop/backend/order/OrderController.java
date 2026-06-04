package com.dwkshop.backend.order;

import com.dwkshop.backend.auth.AuthContext;
import com.dwkshop.backend.order.dto.ConfirmOrderRequest;
import com.dwkshop.backend.order.dto.ConfirmOrderResponse;
import com.dwkshop.backend.order.dto.CreateOrderRequest;
import com.dwkshop.backend.order.dto.OrderResponse;
import com.dwkshop.backend.order.dto.OrderSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Long DEFAULT_USER_ID = 1L;

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/confirm")
    public ConfirmOrderResponse confirm(
        @RequestParam(required = false) Long userId,
        @RequestBody ConfirmOrderRequest request
    ) {
        return orderService.confirm(resolveUserId(userId), request);
    }

    @PostMapping("/create")
    public OrderResponse create(
        @RequestParam(required = false) Long userId,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        return orderService.create(resolveUserId(userId), request);
    }

    @GetMapping
    public List<OrderSummaryResponse> list(@RequestParam(required = false) Long userId) {
        return orderService.listOrders(resolveUserId(userId));
    }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        return orderService.getOrder(resolveUserId(userId), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        return orderService.cancel(resolveUserId(userId), id);
    }

    private Long resolveUserId(Long userId) {
        if (userId != null) {
            return userId;
        }
        return AuthContext.currentUserId().orElse(DEFAULT_USER_ID);
    }
}
