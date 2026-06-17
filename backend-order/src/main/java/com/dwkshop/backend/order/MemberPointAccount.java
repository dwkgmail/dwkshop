package com.dwkshop.backend.order;

public record MemberPointAccount(
    Long userId,
    Integer availablePoints
) {
}
