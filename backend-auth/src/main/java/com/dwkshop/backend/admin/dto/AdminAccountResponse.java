package com.dwkshop.backend.admin.dto;

import java.time.LocalDateTime;

public record AdminAccountResponse(
    Long id,
    String username,
    String displayName,
    String status,
    Long roleId,
    String roleName,
    LocalDateTime createdAt
) {
}
