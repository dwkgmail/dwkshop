package com.dwkshop.backend.admin.dto;

import java.time.LocalDateTime;

public record AdminOperationLogResponse(
    Long id,
    Long adminUserId,
    String adminUsername,
    String module,
    String action,
    String targetType,
    Long targetId,
    String detail,
    LocalDateTime createdAt
) {
}
