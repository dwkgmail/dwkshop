package com.dwkshop.backend.order;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class OrderPaymentTimeoutScheduler {
    private final OrderService orderService;

    public OrderPaymentTimeoutScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${dwkshop.order.payment-timeout-scan-interval-ms:60000}")
    public void closeExpiredUnpaidOrders() {
        orderService.closeExpiredUnpaidOrders();
    }
}
