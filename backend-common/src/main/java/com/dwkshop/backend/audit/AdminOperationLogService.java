package com.dwkshop.backend.audit;

import com.dwkshop.backend.auth.AuthContext;
import com.dwkshop.backend.auth.AuthPrincipal;
import com.dwkshop.backend.audit.dto.AdminOperationLogResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AdminOperationLogService {

    private static final String AUDIT_TABLE = "dwkshop_audit.admin_operation_log";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminOperationLogService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(String operationType, String bizType, Long bizId, Object beforeValue, Object afterValue, String reason) {
        AuthPrincipal principal = AuthContext.current().orElse(null);
        HttpServletRequest request = currentRequest();
        jdbcTemplate.update("""
            INSERT INTO dwkshop_audit.admin_operation_log (
                operator_id, operator_name, operation_type, biz_type, biz_id,
                before_value, after_value, reason, ip, user_agent, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            principal == null ? null : principal.id(),
            principal == null ? "system" : principal.subject(),
            text(operationType),
            text(bizType),
            bizId,
            json(beforeValue),
            json(afterValue),
            text(reason),
            resolveIp(request),
            resolveUserAgent(request),
            LocalDateTime.now()
        );
    }

    public void record(String module, String action, String targetType, Long targetId, String detail) {
        record(action, targetType, targetId, null, null, detail);
    }

    public List<AdminOperationLogResponse> listRecent() {
        return jdbcTemplate.query("""
            SELECT operator_id, operator_name, operation_type, biz_type, biz_id,
                   before_value, after_value, reason, ip, user_agent, created_at
            FROM dwkshop_audit.admin_operation_log
            ORDER BY id DESC
            LIMIT 100
            """, (rs, rowNum) -> new AdminOperationLogResponse(
            rs.getObject("operator_id", Long.class),
            rs.getString("operator_name"),
            rs.getString("operation_type"),
            rs.getString("biz_type"),
            rs.getObject("biz_id", Long.class),
            rs.getString("before_value"),
            rs.getString("after_value"),
            rs.getString("reason"),
            rs.getString("ip"),
            rs.getString("user_agent"),
            rs.getTimestamp("created_at").toLocalDateTime()
        ));
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

    private String json(Object value) {
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

    private String text(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
