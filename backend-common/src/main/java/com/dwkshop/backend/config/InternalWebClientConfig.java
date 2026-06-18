package com.dwkshop.backend.config;

import com.dwkshop.backend.auth.InternalServiceAuthConfig;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalWebClientConfig {

    private final InternalServiceAuthConfig internalServiceAuthConfig;

    public InternalWebClientConfig(InternalServiceAuthConfig internalServiceAuthConfig) {
        this.internalServiceAuthConfig = internalServiceAuthConfig;
    }

    @Bean
    public WebClientCustomizer internalServiceWebClientCustomizer() {
        return builder -> builder.defaultHeader(
            InternalServiceAuthConfig.INTERNAL_SECRET_HEADER,
            internalServiceAuthConfig.internalSecret()
        );
    }
}
