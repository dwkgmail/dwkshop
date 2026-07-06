package com.dwkshop.backend.admin;

public record AdminUserStats(
    int availablePoints,
    int lockedPoints,
    long orderCount,
    long couponCount
) {
    public static AdminUserStats empty() {
        return new AdminUserStats(0, 0, 0, 0);
    }
}
