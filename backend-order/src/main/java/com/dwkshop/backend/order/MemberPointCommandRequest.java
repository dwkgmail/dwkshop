package com.dwkshop.backend.order;

public record MemberPointCommandRequest(
    Long orderId,
    String bizNo,
    Integer points
) {
}
