package com.dwkshop.backend.order;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CartClient {

    private final WebClient webClient;

    public CartClient(
        WebClient.Builder builder,
        @Value("${dwkshop.services.cart.base-url:http://localhost:18083}") String cartBaseUrl
    ) {
        this.webClient = builder.baseUrl(cartBaseUrl).build();
    }

    public List<CartItemSnapshot> listItems(Long userId, List<Long> itemIds) {
        return webClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder.path("/internal/carts/{userId}/items");
                if (itemIds != null && !itemIds.isEmpty()) {
                    builder.queryParam("ids", itemIds.toArray());
                }
                return builder.build(userId);
            })
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Cart information is unavailable")))
            .bodyToMono(new ParameterizedTypeReference<List<CartItemSnapshot>>() {
            })
            .block();
    }

    public void deleteItems(Long userId, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        webClient.delete()
            .uri(uriBuilder -> uriBuilder
                .path("/internal/carts/{userId}/items")
                .queryParam("ids", itemIds.toArray())
                .build(userId))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Cart cleanup is unavailable")))
            .toBodilessEntity()
            .block();
    }
}
