package com.dwkshop.backend.admin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AdminUserStatsClient {

    private final WebClient memberClient;
    private final WebClient orderClient;
    private final WebClient marketingClient;

    public AdminUserStatsClient(
        WebClient.Builder builder,
        @Value("${dwkshop.services.member.base-url:http://localhost:18086}") String memberBaseUrl,
        @Value("${dwkshop.services.order.base-url:http://localhost:18084}") String orderBaseUrl,
        @Value("${dwkshop.services.marketing.base-url:http://localhost:18087}") String marketingBaseUrl
    ) {
        this.memberClient = builder.clone().baseUrl(memberBaseUrl).build();
        this.orderClient = builder.clone().baseUrl(orderBaseUrl).build();
        this.marketingClient = builder.clone().baseUrl(marketingBaseUrl).build();
    }

    public Map<Long, AdminUserStats> fetchStats(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = userIds.stream().distinct().toList();
        Map<Long, MutableStats> stats = new LinkedHashMap<>();
        ids.forEach(id -> stats.put(id, new MutableStats()));

        fetchPointAccounts(ids).forEach(account -> {
            MutableStats item = stats.get(account.userId());
            if (item != null) {
                item.availablePoints = account.availablePoints() == null ? 0 : account.availablePoints();
                item.lockedPoints = account.lockedPoints() == null ? 0 : account.lockedPoints();
            }
        });
        fetchOrderCounts(ids).forEach(count -> {
            MutableStats item = stats.get(count.userId());
            if (item != null) {
                item.orderCount = count.orderCount();
            }
        });
        fetchCouponCounts(ids).forEach(count -> {
            MutableStats item = stats.get(count.userId());
            if (item != null) {
                item.couponCount = count.couponCount();
            }
        });

        Map<Long, AdminUserStats> result = new LinkedHashMap<>();
        stats.forEach((userId, item) -> result.put(userId, item.toStats()));
        return result;
    }

    private List<MemberPointAccountSnapshot> fetchPointAccounts(List<Long> userIds) {
        return getList(memberClient, "/internal/members/point-accounts", userIds, new ParameterizedTypeReference<>() {});
    }

    private List<UserOrderCount> fetchOrderCounts(List<Long> userIds) {
        return getList(orderClient, "/internal/orders/user-counts", userIds, new ParameterizedTypeReference<>() {});
    }

    private List<UserCouponCount> fetchCouponCounts(List<Long> userIds) {
        return getList(marketingClient, "/internal/marketing/user-coupon-counts", userIds, new ParameterizedTypeReference<>() {});
    }

    private <T> List<T> getList(WebClient client, String path, List<Long> userIds, ParameterizedTypeReference<List<T>> type) {
        try {
            return client.get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParam("userIds", userIds.toArray()).build())
                .retrieve()
                .bodyToMono(type)
                .blockOptional()
                .orElse(List.of());
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static class MutableStats {
        int availablePoints;
        int lockedPoints;
        long orderCount;
        long couponCount;

        AdminUserStats toStats() {
            return new AdminUserStats(availablePoints, lockedPoints, orderCount, couponCount);
        }
    }

    private record MemberPointAccountSnapshot(Long userId, Integer availablePoints, Integer lockedPoints) {
    }

    private record UserOrderCount(Long userId, long orderCount) {
    }

    private record UserCouponCount(Long userId, long couponCount) {
    }
}
