package com.dwkshop.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dwkshop.web-client")
public record WebClientResilienceProperties(
    Duration connectTimeout,
    Duration responseTimeout,
    Integer maxRetries,
    Duration retryBackoff,
    int circuitBreakerFailureRateThreshold,
    int circuitBreakerMinimumCalls,
    int circuitBreakerSlidingWindowSize,
    Duration circuitBreakerOpenDuration
) {
    public WebClientResilienceProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        responseTimeout = responseTimeout == null ? Duration.ofSeconds(3) : responseTimeout;
        maxRetries = maxRetries == null ? 2 : Math.max(0, maxRetries);
        retryBackoff = retryBackoff == null ? Duration.ofMillis(100) : retryBackoff;
        circuitBreakerFailureRateThreshold = circuitBreakerFailureRateThreshold <= 0
            ? 50 : circuitBreakerFailureRateThreshold;
        circuitBreakerMinimumCalls = circuitBreakerMinimumCalls <= 0 ? 5 : circuitBreakerMinimumCalls;
        circuitBreakerSlidingWindowSize = circuitBreakerSlidingWindowSize <= 0
            ? 10 : circuitBreakerSlidingWindowSize;
        circuitBreakerOpenDuration = circuitBreakerOpenDuration == null
            ? Duration.ofSeconds(30) : circuitBreakerOpenDuration;
    }
}
