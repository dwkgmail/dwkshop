package com.dwkshop.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long ttlSeconds;

    public AuthTokenService(
        ObjectMapper objectMapper,
        @Value("${dwkshop.auth.secret:dwkshop-local-dev-secret-change-me}") String secret,
        @Value("${dwkshop.auth.ttl-seconds:86400}") long ttlSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(Long id, String subject, String role) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            payload.put("sub", subject);
            payload.put("role", role);
            payload.put("exp", expiresAt);
            String payloadPart = base64Url(objectMapper.writeValueAsBytes(payload));
            String signaturePart = sign(payloadPart);
            return payloadPart + "." + signaturePart;
        } catch (Exception ex) {
            throw new IllegalStateException("签发登录令牌失败", ex);
        }
    }

    public AuthPrincipal verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
                throw new AuthException("登录状态无效，请重新登录");
            }
            Map<?, ?> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            long expiresAt = ((Number) payload.get("exp")).longValue();
            if (expiresAt < Instant.now().getEpochSecond()) {
                throw new AuthException("登录已过期，请重新登录");
            }
            Long id = ((Number) payload.get("id")).longValue();
            String subject = String.valueOf(payload.get("sub"));
            String role = String.valueOf(payload.get("role"));
            return new AuthPrincipal(id, subject, role);
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
}
