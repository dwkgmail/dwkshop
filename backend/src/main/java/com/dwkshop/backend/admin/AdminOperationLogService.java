package com.dwkshop.backend.admin;

import com.dwkshop.backend.admin.dto.AdminOperationLogResponse;
import com.dwkshop.backend.auth.AuthContext;
import com.dwkshop.backend.auth.AuthPrincipal;
import com.dwkshop.backend.domain.entity.AdminOperationLog;
import com.dwkshop.backend.domain.repository.AdminOperationLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AdminOperationLogService {

    private final AdminOperationLogRepository adminOperationLogRepository;
    private final ObjectMapper objectMapper;

    public AdminOperationLogService(AdminOperationLogRepository adminOperationLogRepository, ObjectMapper objectMapper) {
        this.adminOperationLogRepository = adminOperationLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(String module, String action, String targetType, Long targetId, String detail) {
        AuthPrincipal principal = AuthContext.current().orElse(null);
        HttpServletRequest request = currentRequest();
        AdminOperationLog log = new AdminOperationLog();
        log.setOperatorId(principal == null ? null : principal.id());
        log.setOperatorName(principal == null ? "system" : principal.subject());
        log.setOperationType(action);
        log.setBizType(targetType);
        log.setBizId(targetId);
        log.setBeforeValue(null);
        log.setAfterValue(null);
        log.setReason(normalizeText(detail));
        log.setIp(resolveIp(request));
        log.setUserAgent(resolveUserAgent(request));
        log.setModule(defaultText(module, targetType));
        log.setAction(defaultText(action, module));
        log.setTargetType(defaultText(targetType, module));
        log.setTargetId(targetId);
        log.setDetail(normalizeText(detail));
        log.setCreatedAt(LocalDateTime.now());
        adminOperationLogRepository.save(log);
    }

    public void record(String operationType, String bizType, Long bizId, Object beforeValue, Object afterValue, String reason) {
        AuthPrincipal principal = AuthContext.current().orElse(null);
        HttpServletRequest request = currentRequest();
        AdminOperationLog log = new AdminOperationLog();
        log.setOperatorId(principal == null ? null : principal.id());
        log.setOperatorName(principal == null ? "system" : principal.subject());
        log.setOperationType(operationType);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setBeforeValue(serialize(beforeValue));
        log.setAfterValue(serialize(afterValue));
        log.setReason(normalizeText(reason));
        log.setIp(resolveIp(request));
        log.setUserAgent(resolveUserAgent(request));
        log.setModule(defaultText(bizType, operationType));
        log.setAction(defaultText(operationType, bizType));
        log.setTargetType(defaultText(bizType, operationType));
        log.setTargetId(bizId);
        log.setDetail(normalizeText(reason));
        log.setCreatedAt(LocalDateTime.now());
        adminOperationLogRepository.save(log);
    }

    public List<AdminOperationLogResponse> listRecent() {
        return adminOperationLogRepository.findTop100ByOrderByIdDesc().stream()
            .map(log -> new AdminOperationLogResponse(
                log.getId(),
                log.getOperatorId(),
                log.getOperatorName(),
                log.getOperationType(),
                log.getBizType(),
                log.getBizId(),
                log.getBeforeValue(),
                log.getAfterValue(),
                log.getReason(),
                log.getIp(),
                log.getUserAgent(),
                log.getCreatedAt(),
                log.getModule(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetail()
            ))
            .toList();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null || userAgent.isBlank() ? null : userAgent;
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String defaultText(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}
