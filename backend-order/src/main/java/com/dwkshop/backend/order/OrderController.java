package com.dwkshop.backend.order;

import com.dwkshop.backend.auth.AuthContext;
import com.dwkshop.backend.auth.AuthException;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/confirm")
    public ConfirmOrderResponse confirm(
        @RequestBody ConfirmOrderRequest request
    ) {
        return orderService.confirm(currentUserId(), request);
    }

    @PostMapping("/create")
    public OrderResponse create(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        return orderService.create(currentUserId(), request.withClientRequestId(idempotencyKey));
    }

    @GetMapping
    public List<OrderSummaryResponse> list() {
        return orderService.listOrders(currentUserId());
    }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable Long id) {
        return orderService.getOrder(currentUserId(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.cancel(currentUserId(), id);
    }

    @PostMapping("/{id}/pay")
    public OrderResponse pay(@PathVariable Long id) {
        return orderService.pay(currentUserId(), id);
    }

    private Long currentUserId() {
        return AuthContext.currentUserId().orElseThrow(() -> new AuthException("please login first"));
    }
}
