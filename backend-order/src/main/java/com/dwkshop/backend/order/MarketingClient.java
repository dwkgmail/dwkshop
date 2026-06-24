package com.dwkshop.backend.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MarketingClient {

    private final WebClient webClient;

    public MarketingClient(
        WebClient.Builder builder,
        @Value("${dwkshop.services.marketing.base-url:http://localhost:18087}") String marketingBaseUrl
    ) {
        this.webClient = builder.baseUrl(marketingBaseUrl).build();
    }

    public MarketingCouponSelection selectCoupon(Long userId, Long requestedCouponUserId, int productAmount) {
        return webClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder.path("/internal/marketing/users/{userId}/coupons/selection")
                    .queryParam("productAmount", productAmount);
                if (requestedCouponUserId != null) {
                    builder.queryParam("requestedCouponUserId", requestedCouponUserId);
                }
                return builder.build(userId);
            })
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Coupon information is unavailable")))
            .bodyToMono(MarketingCouponSelection.class)
            .block();
    }

    public void useCoupon(Long userId, Long userCouponId, Long orderId) {
        webClient.post()
            .uri("/internal/marketing/users/{userId}/coupons/{userCouponId}/use", userId, userCouponId)
            .bodyValue(new UseCouponRequest(orderId))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Coupon use is unavailable")))
            .toBodilessEntity()
            .block();
    }

    public void lockCoupon(Long userId, Long userCouponId, String lockKey, int productAmount) {
        webClient.post()
            .uri("/internal/marketing/users/{userId}/coupons/{userCouponId}/lock", userId, userCouponId)
            .bodyValue(new LockCouponRequest(lockKey, productAmount))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Coupon lock is unavailable")))
            .toBodilessEntity()
            .block();
    }

    public void releaseCoupon(Long userId, Long userCouponId, Long orderId) {
        webClient.post()
            .uri("/internal/marketing/users/{userId}/coupons/{userCouponId}/release", userId, userCouponId)
            .bodyValue(new UseCouponRequest(orderId))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Coupon release is unavailable")))
            .toBodilessEntity()
            .block();
    }

    public void refundCoupon(Long userId, Long userCouponId, Long orderId) {
        webClient.post()
            .uri("/internal/marketing/users/{userId}/coupons/{userCouponId}/refund", userId, userCouponId)
            .bodyValue(new UseCouponRequest(orderId))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Coupon refund is unavailable")))
            .toBodilessEntity()
            .block();
    }
}
