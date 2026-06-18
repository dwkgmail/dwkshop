package com.dwkshop.backend.aftersale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderClient {

    private final WebClient webClient;

    public OrderClient(
        WebClient.Builder builder,
        @Value("${dwkshop.services.order.base-url:http://localhost:18084}") String orderBaseUrl
    ) {
        this.webClient = builder.baseUrl(orderBaseUrl).build();
    }

    public AftersaleOrderSnapshot getAftersaleSnapshot(Long orderId) {
        return webClient.get()
            .uri("/internal/orders/{orderId}/aftersale", orderId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Order information is unavailable")))
            .bodyToMono(AftersaleOrderSnapshot.class)
            .block();
    }

    public RefundOrderContext getRefundContext(Long orderId) {
        return webClient.get()
            .uri("/internal/orders/{orderId}/refund-context", orderId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Order refund context is unavailable")))
            .bodyToMono(RefundOrderContext.class)
            .block();
    }

    public AftersaleOrderSnapshot applyAftersale(Long orderId, Long userId) {
        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/internal/orders/{orderId}/aftersale/apply")
                .queryParam("userId", userId)
                .build(orderId))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Order cannot request aftersale")))
            .bodyToMono(AftersaleOrderSnapshot.class)
            .block();
    }

    public AftersaleOrderSnapshot approveAftersale(Long orderId) {
        return completeAftersale(orderId);
    }

    public AftersaleOrderSnapshot rejectAftersale(Long orderId) {
        return changeAftersaleStatus(orderId, "reject");
    }

    public AftersaleOrderSnapshot completeAftersale(Long orderId) {
        return webClient.post()
            .uri("/internal/orders/{orderId}/aftersale/approve", orderId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Order aftersale completion failed")))
            .bodyToMono(AftersaleOrderSnapshot.class)
            .block();
    }

    private AftersaleOrderSnapshot changeAftersaleStatus(Long orderId, String action) {
        return webClient.post()
            .uri("/internal/orders/{orderId}/aftersale/{action}", orderId, action)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Order aftersale status update failed")))
            .bodyToMono(AftersaleOrderSnapshot.class)
            .block();
    }
}
