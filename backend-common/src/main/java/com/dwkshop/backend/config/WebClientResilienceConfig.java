package com.dwkshop.backend.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.netty.channel.ChannelOption;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Configuration
@EnableConfigurationProperties(WebClientResilienceProperties.class)
public class WebClientResilienceConfig {

    @Bean
    public CircuitBreakerRegistry internalServiceCircuitBreakerRegistry(WebClientResilienceProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(properties.circuitBreakerFailureRateThreshold())
            .minimumNumberOfCalls(properties.circuitBreakerMinimumCalls())
            .slidingWindowSize(properties.circuitBreakerSlidingWindowSize())
            .waitDurationInOpenState(properties.circuitBreakerOpenDuration())
            .recordException(this::isCircuitBreakerFailure)
            .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public WebClientCustomizer resilientWebClientCustomizer(
        WebClientResilienceProperties properties,
        CircuitBreakerRegistry circuitBreakers
    ) {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeout().toMillis()))
            .responseTimeout(properties.responseTimeout());

        return builder -> builder
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .filter(resilienceFilter(properties, circuitBreakers));
    }

    ExchangeFilterFunction resilienceFilter(
        WebClientResilienceProperties properties,
        CircuitBreakerRegistry circuitBreakers
    ) {
        return (request, next) -> {
            CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker(circuitBreakerName(request.url()));
            var exchange = reactor.core.publisher.Mono.defer(() -> next.exchange(request))
                .flatMap(this::failOnServerError);

            // Retrying mutating requests can duplicate stock, coupon, or order operations.
            if (isIdempotent(request.method()) && properties.maxRetries() > 0) {
                exchange = exchange.retryWhen(Retry.backoff(properties.maxRetries(), properties.retryBackoff())
                    .maxBackoff(properties.retryBackoff().multipliedBy(4))
                    .filter(this::isRetryable)
                    .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
            }

            return exchange.transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
        };
    }

    private boolean isIdempotent(HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS;
    }

    private reactor.core.publisher.Mono<ClientResponse> failOnServerError(ClientResponse response) {
        if (response.statusCode().is5xxServerError()) {
            return response.createException().flatMap(reactor.core.publisher.Mono::error);
        }
        return reactor.core.publisher.Mono.just(response);
    }

    private boolean isRetryable(Throwable error) {
        if (error instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        return isNetworkFailure(error);
    }

    private boolean isCircuitBreakerFailure(Throwable error) {
        return isRetryable(error);
    }

    private boolean isNetworkFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WebClientRequestException
                || current instanceof ConnectException
                || current instanceof TimeoutException
                || current instanceof io.netty.handler.timeout.ReadTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String circuitBreakerName(URI uri) {
        String host = uri.getHost() == null ? "unknown" : uri.getHost();
        int port = uri.getPort();
        return (port < 0 ? host : host + "-" + port)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]", "-");
    }
}
