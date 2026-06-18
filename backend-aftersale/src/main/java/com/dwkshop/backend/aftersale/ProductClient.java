package com.dwkshop.backend.aftersale;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProductClient {

    private final WebClient webClient;

    public ProductClient(
        WebClient.Builder builder,
        @Value("${dwkshop.services.product.base-url:http://localhost:18082}") String productBaseUrl
    ) {
        this.webClient = builder.baseUrl(productBaseUrl).build();
    }

    public RefundStockResponse releaseRefundStock(String commandNo, List<RefundStockItemRequest> items) {
        return refundStock(commandNo, "RELEASE", items, "/internal/products/refunds/release");
    }

    public RefundStockResponse restoreRefundStock(String commandNo, List<RefundStockItemRequest> items) {
        return refundStock(commandNo, "RESTORE", items, "/internal/products/refunds/restore");
    }

    private RefundStockResponse refundStock(String commandNo, String commandType, List<RefundStockItemRequest> items, String path) {
        return webClient.post()
            .uri(path)
            .bodyValue(new RefundStockRequest(commandNo, commandType, items))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Product refund command failed")))
            .bodyToMono(RefundStockResponse.class)
            .block();
    }
}
