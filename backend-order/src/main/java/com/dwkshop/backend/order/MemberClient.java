package com.dwkshop.backend.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MemberClient {

    private final WebClient webClient;

    public MemberClient(
        WebClient.Builder builder,
        @Value("${dwkshop.services.member.base-url:http://localhost:18086}") String memberBaseUrl
    ) {
        this.webClient = builder.baseUrl(memberBaseUrl).build();
    }

    public MemberAddress resolveAddress(Long userId, Long addressId) {
        return webClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder.path("/internal/members/{userId}/addresses/resolved");
                if (addressId != null) {
                    builder.queryParam("addressId", addressId);
                }
                return builder.build(userId);
            })
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Member address is unavailable")))
            .bodyToMono(MemberAddress.class)
            .block();
    }

    public MemberPointAccount getPointAccount(Long userId) {
        return webClient.get()
            .uri("/internal/members/{userId}/point-account", userId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Member point account is unavailable")))
            .bodyToMono(MemberPointAccount.class)
            .block();
    }

    public void freezePoints(Long userId, Long orderId, String bizNo, Integer points) {
        changePoints(userId, "/internal/members/{userId}/points/freeze", orderId, bizNo, points);
    }

    public void deductFrozenPoints(Long userId, Long orderId, String bizNo, Integer points) {
        changePoints(userId, "/internal/members/{userId}/points/deduct", orderId, bizNo, points);
    }

    public void releaseFrozenPoints(Long userId, Long orderId, String bizNo, Integer points) {
        changePoints(userId, "/internal/members/{userId}/points/release", orderId, bizNo, points);
    }

    public void refundPoints(Long userId, Long orderId, String bizNo, Integer points) {
        changePoints(userId, "/internal/members/{userId}/points/refund", orderId, bizNo, points);
    }

    private void changePoints(Long userId, String path, Long orderId, String bizNo, Integer points) {
        webClient.post()
            .uri(path, userId)
            .bodyValue(new MemberPointCommandRequest(orderId, bizNo, points))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                .map(ex -> new ResponseStatusException(response.statusCode(), "Member point account is unavailable")))
            .toBodilessEntity()
            .block();
    }
}
