package com.dwkshop.backend.observability;

import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientTraceConfig {

    @Bean
    public WebClientCustomizer traceAwareWebClientCustomizer() {
        return builder -> builder.filter(tracePropagationFilter());
    }

    private ExchangeFilterFunction tracePropagationFilter() {
        return (request, next) -> {
            String traceId = MDC.get(TraceContext.TRACE_ID_MDC_KEY);
            ClientRequest tracedRequest = traceId == null
                ? request
                : ClientRequest.from(request)
                    .headers(headers -> headers.set(TraceContext.TRACE_ID_HEADER, traceId))
                    .build();
            long startNanos = System.nanoTime();
            return next.exchange(tracedRequest)
                .doOnSuccess(response -> logRequest(
                    tracedRequest,
                    response == null ? "UNKNOWN" : String.valueOf(response.statusCode().value()),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                    traceId
                ))
                .doOnError(error -> logError(
                    tracedRequest,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                    traceId,
                    error
                ));
        };
    }

    private void logRequest(ClientRequest request, String status, long durationMs, String traceId) {
        withTraceId(traceId, () -> logger().info(
            "service_call method={} uri={} status={} durationMs={} traceId={}",
            request.method(),
            request.url(),
            status,
            durationMs,
            traceId == null ? "-" : traceId
        ));
    }

    private void logError(ClientRequest request, long durationMs, String traceId, Throwable error) {
        withTraceId(traceId, () -> logger().warn(
            "service_call method={} uri={} status=ERROR durationMs={} traceId={}",
            request.method(),
            request.url(),
            durationMs,
            traceId == null ? "-" : traceId,
            error
        ));
    }

    private void withTraceId(String traceId, Runnable action) {
        if (traceId == null) {
            action.run();
            return;
        }
        MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId);
        try {
            action.run();
        } finally {
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }

    private org.slf4j.Logger logger() {
        return org.slf4j.LoggerFactory.getLogger(WebClientTraceConfig.class);
    }
}
