package com.dwkshop.backend.order;

import com.dwkshop.backend.order.dto.AdminShipOrderRequest;
import com.dwkshop.backend.order.dto.AdminUpdateDeliveryStatusRequest;
import com.dwkshop.backend.order.dto.OrderResponse;
import com.dwkshop.backend.order.dto.OrderSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderSummaryResponse> listOrders() {
        return orderService.listAdminOrders();
    }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable Long id) {
        return orderService.getAdminOrder(id);
    }

    @PostMapping("/{id}/ship")
    public OrderResponse ship(@PathVariable Long id, @Valid @RequestBody AdminShipOrderRequest request) {
        return orderService.shipOrder(id, request);
    }

    @PostMapping("/{id}/delivery-status")
    public OrderResponse updateDeliveryStatus(
        @PathVariable Long id,
        @Valid @RequestBody AdminUpdateDeliveryStatusRequest request
    ) {
        return orderService.updateDeliveryStatus(id, request);
    }
}
