package com.dwkshop.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class InternalServiceAuthConfig {

    public static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final String internalSecret;

    public InternalServiceAuthConfig(
        @Value("${dwkshop.internal.secret:dwkshop-local-internal-secret-change-me}") String secret
    ) {
        this.internalSecret = secret;
    }

    public String internalSecret() {
        return internalSecret;
    }
}
