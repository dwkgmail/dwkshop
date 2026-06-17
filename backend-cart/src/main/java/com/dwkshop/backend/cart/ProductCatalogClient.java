package com.dwkshop.backend.cart;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProductCatalogClient {

    private final WebClient webClient;

    public ProductCatalogClient(
        WebClient.Builder builder,
        @Value("${dwkshop.services.product.base-url:http://localhost:18082}") String productBaseUrl
    ) {
        this.webClient = builder.baseUrl(productBaseUrl).build();
    }

    public ProductSkuSnapshot getSkuSnapshot(Long skuId) {
        return webClient.get()
            .uri("/internal/products/skus/{skuId}/snapshot", skuId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Product information is unavailable")))
            .bodyToMono(ProductSkuSnapshot.class)
            .block();
    }
}
