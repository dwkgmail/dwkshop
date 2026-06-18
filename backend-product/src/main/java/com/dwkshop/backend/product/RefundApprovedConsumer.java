package com.dwkshop.backend.product;

import com.dwkshop.backend.event.RefundApprovedEvent;
import com.dwkshop.backend.product.dto.RefundStockItemRequest;
import com.dwkshop.backend.product.dto.RefundStockRequest;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RefundApprovedConsumer {
    private final ProductService productService;

    public RefundApprovedConsumer(ProductService productService) {
        this.productService = productService;
    }

    @RabbitListener(queues = "${dwkshop.mq.refund-approved-product-queue}")
    public void consume(RefundApprovedEvent event) {
        if (event.items() == null || event.items().isEmpty()) {
            return;
        }
        productService.releaseRefundStock(new RefundStockRequest(
            event.commandNo(), "RELEASE",
            event.items().stream().map(item -> new RefundStockItemRequest(item.skuId(), item.quantity())).toList()
        ));
    }
}
