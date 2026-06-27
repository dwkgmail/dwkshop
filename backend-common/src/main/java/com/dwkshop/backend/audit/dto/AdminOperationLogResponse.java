package com.dwkshop.backend.audit.dto;

import java.time.LocalDateTime;

public record AdminOperationLogResponse(
    Long operatorId,
    String operatorName,
    String operationType,
    String bizType,
    Long bizId,
    String beforeValue,
    String afterValue,
    String reason,
    String ip,
    String userAgent,
    LocalDateTime createdAt
) {
}
