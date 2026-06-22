package com.dwkshop.backend.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebClientResilienceConfigTest {

    private final WebClientResilienceConfig config = new WebClientResilienceConfig();

    @Test
    void retriesServerErrorsForGetRequestsOnly() {
        WebClientResilienceProperties properties = properties(2, 10, 10);
        CircuitBreakerRegistry registry = config.internalServiceCircuitBreakerRegistry(properties);
        AtomicInteger calls = new AtomicInteger();
        WebClient client = WebClient.builder()
            .exchangeFunction(request -> {
                calls.incrementAndGet();
                return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
            })
            .filter(config.resilienceFilter(properties, registry))
            .build();

        assertThatThrownBy(() -> client.get().uri("http://product:8080/test").retrieve().toBodilessEntity().block())
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
        assertThat(calls).hasValue(3);

        calls.set(0);
        assertThatThrownBy(() -> client.post().uri("http://product:8080/test").retrieve().toBodilessEntity().block())
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void opensCircuitAfterConfiguredFailureThreshold() {
        WebClientResilienceProperties properties = properties(0, 2, 2);
        CircuitBreakerRegistry registry = config.internalServiceCircuitBreakerRegistry(properties);
        WebClient client = WebClient.builder()
            .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).build()))
            .filter(config.resilienceFilter(properties, registry))
            .build();

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.get().uri("http://member:8080/test").retrieve().toBodilessEntity().block())
                .isInstanceOf(WebClientResponseException.BadGateway.class);
        }
        assertThatThrownBy(() -> client.get().uri("http://member:8080/test").retrieve().toBodilessEntity().block())
            .isInstanceOf(CallNotPermittedException.class);
    }

    private WebClientResilienceProperties properties(int retries, int minimumCalls, int windowSize) {
        return new WebClientResilienceProperties(
            Duration.ofSeconds(1), Duration.ofSeconds(2), retries, Duration.ofMillis(1),
            50, minimumCalls, windowSize, Duration.ofSeconds(30)
        );
    }
}
