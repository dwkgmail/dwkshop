package com.dwkshop.backend.order;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
public class OrderInternalController {

    private final OrderService orderService;

    public OrderInternalController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{orderId}/aftersale")
    public AftersaleOrderSnapshot getAftersaleSnapshot(@PathVariable Long orderId) {
        return orderService.getAftersaleSnapshot(orderId);
    }

    @GetMapping("/{orderId}/refund-context")
    public RefundOrderContext getRefundContext(@PathVariable Long orderId) {
        return orderService.getRefundContext(orderId);
    }

    @GetMapping("/inventory-reconciliation/summaries")
    public List<InventoryOrderSummary> getInventoryOrderSummaries(@RequestParam List<Long> orderIds) {
        return orderService.getInventoryOrderSummaries(orderIds);
    }

    @GetMapping("/inventory-reconciliation/health")
    public InventoryOrderHealth getInventoryOrderHealth(@RequestParam(defaultValue = "10") int pendingMinutes) {
        return orderService.getInventoryOrderHealth(pendingMinutes);
    }

    @PostMapping("/{orderId}/aftersale/apply")
    public AftersaleOrderSnapshot applyAftersale(@PathVariable Long orderId, @RequestParam Long userId) {
        return orderService.applyAftersale(orderId, userId);
    }

    @PostMapping("/{orderId}/aftersale/approve")
    public AftersaleOrderSnapshot approveAftersale(@PathVariable Long orderId) {
        return orderService.completeAftersale(orderId);
    }

    @PostMapping("/{orderId}/aftersale/reject")
    public AftersaleOrderSnapshot rejectAftersale(@PathVariable Long orderId) {
        return orderService.rejectAftersale(orderId);
    }
}
