package com.dwkshop.backend.admin.dto;

import java.time.LocalDateTime;

public record AdminUserResponse(
    Long id,
    String mobile,
    String nickname,
    String status,
    Integer availablePoints,
    Integer lockedPoints,
    long orderCount,
    long couponCount,
    LocalDateTime createdAt
) {
}
