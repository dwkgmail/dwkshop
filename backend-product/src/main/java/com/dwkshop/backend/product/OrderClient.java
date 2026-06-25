package com.dwkshop.backend.product;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClient.class);

    private final WebClient webClient;

    public OrderClient(
        WebClient.Builder builder,
        @Value("${dwkshop.services.order.base-url:http://localhost:18084}") String orderBaseUrl
    ) {
        this.webClient = builder.baseUrl(orderBaseUrl).build();
    }

    public List<InventoryOrderSummary> getInventoryOrderSummaries(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        try {
            List<InventoryOrderSummary> response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/internal/orders/inventory-reconciliation/summaries")
                    .queryParam("orderIds", orderIds.toArray())
                    .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<InventoryOrderSummary>>() {
                })
                .block();
            return response == null ? List.of() : response;
        } catch (RuntimeException ex) {
            log.debug("order summaries are unavailable for inventory reconciliation: {}", ex.getMessage());
            return List.of();
        }
    }

    public InventoryOrderHealth getInventoryOrderHealth(int pendingMinutes) {
        try {
            InventoryOrderHealth response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/internal/orders/inventory-reconciliation/health")
                    .queryParam("pendingMinutes", pendingMinutes)
                    .build())
                .retrieve()
                .bodyToMono(InventoryOrderHealth.class)
                .block();
            return response == null ? InventoryOrderHealth.empty() : response;
        } catch (RuntimeException ex) {
            log.debug("order health is unavailable for inventory reconciliation: {}", ex.getMessage());
            return InventoryOrderHealth.empty();
        }
    }
}
