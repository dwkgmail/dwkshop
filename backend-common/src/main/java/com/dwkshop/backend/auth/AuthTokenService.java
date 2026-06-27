package com.dwkshop.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String ACCESS_TOKEN = "ACCESS";
    private static final String REFRESH_TOKEN = "REFRESH";

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long ttlSeconds;
    private final long refreshTtlSeconds;

    public AuthTokenService(
        ObjectMapper objectMapper,
        @Value("${dwkshop.auth.secret:dwkshop-local-dev-secret-change-me}") String secret,
        @Value("${dwkshop.auth.ttl-seconds:86400}") long ttlSeconds,
        @Value("${dwkshop.auth.refresh-ttl-seconds:604800}") long refreshTtlSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String issue(Long id, String subject, String role) {
        return issue(id, subject, role, defaultPermissions(role));
    }

    public String issue(Long id, String subject, String role, Collection<String> permissions) {
        return issue(id, subject, role, permissions, ACCESS_TOKEN, ttlSeconds);
    }

    public String issueRefresh(Long id, String subject, String role) {
        return issueRefresh(id, subject, role, defaultPermissions(role));
    }

    public String issueRefresh(Long id, String subject, String role, Collection<String> permissions) {
        return issue(id, subject, role, permissions, REFRESH_TOKEN, refreshTtlSeconds);
    }

    public AuthPrincipal verify(String token) {
        return verify(token, ACCESS_TOKEN);
    }

    public AuthPrincipal verifyRefresh(String token) {
        return verify(token, REFRESH_TOKEN);
    }

    private String issue(Long id, String subject, String role, Collection<String> permissions, String type, long ttlSeconds) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
            // 这里实现的是轻量自定义 token：payload 编码后再做 HMAC-SHA256 签名。
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            payload.put("sub", subject);
            payload.put("role", role);
            payload.put("permissions", normalizePermissions(permissions));
            payload.put("typ", type);
            payload.put("exp", expiresAt);
            String payloadPart = base64Url(objectMapper.writeValueAsBytes(payload));
            String signaturePart = sign(payloadPart);
            return payloadPart + "." + signaturePart;
        } catch (Exception ex) {
            throw new IllegalStateException("签发登录令牌失败", ex);
        }
    }

    private AuthPrincipal verify(String token, String expectedType) {
        try {
            String[] parts = token.split("\\.");
            // 先校验签名，再解析内容，防止客户端直接篡改 token 载荷。
            if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
                throw new AuthException("登录状态无效，请重新登录");
            }
            Map<?, ?> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            long expiresAt = ((Number) payload.get("exp")).longValue();
            if (expiresAt < Instant.now().getEpochSecond()) {
                throw new AuthException("登录已过期，请重新登录");
            }
            String type = String.valueOf(payload.containsKey("typ") ? payload.get("typ") : ACCESS_TOKEN);
            if (!expectedType.equals(type)) {
                throw new AuthException("登录状态无效，请重新登录");
            }
            // token 中只恢复最小身份信息，账号是否仍可用要靠业务层继续校验。
            Long id = ((Number) payload.get("id")).longValue();
            String subject = String.valueOf(payload.get("sub"));
            String role = String.valueOf(payload.get("role"));
            return new AuthPrincipal(id, subject, role, parsePermissions(payload.get("permissions"), role));
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("登录状态无效，请重新登录");
        }
    }

    private String sign(String payloadPart) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
        return base64Url(mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Set<String> parsePermissions(Object raw, String role) {
        if (raw instanceof Collection<?> collection) {
            return normalizePermissions(collection.stream().map(String::valueOf).toList());
        }
        if (raw instanceof String text && !text.isBlank()) {
            return normalizePermissions(List.of(text.split(",")));
        }
        return defaultPermissions(role);
    }

    private Set<String> defaultPermissions(String role) {
        return ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) ? Set.of("*") : Set.of();
    }

    private Set<String> normalizePermissions(Collection<?> permissions) {
        Set<String> normalized = new LinkedHashSet<>();
        if (permissions == null) {
            return normalized;
        }
        for (Object permission : permissions) {
            String text = String.valueOf(permission).trim();
            if (!text.isEmpty()) {
                normalized.add(text);
            }
        }
        return normalized;
    }
}
