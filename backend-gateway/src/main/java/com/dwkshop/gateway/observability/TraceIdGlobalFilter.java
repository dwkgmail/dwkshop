package com.dwkshop.gateway.observability;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdGlobalFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = TraceContext.normalize(exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER));
        ServerHttpRequest tracedRequest = exchange.getRequest().mutate()
            .headers(headers -> headers.set(TraceContext.TRACE_ID_HEADER, traceId))
            .build();
        exchange.getResponse().getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
        long startNanos = System.nanoTime();

        return chain.filter(exchange.mutate().request(tracedRequest).build())
            .doOnSuccess(ignored -> logAccess(exchange, traceId, startNanos))
            .doOnError(error -> logError(exchange, traceId, startNanos, error));
    }

    private void logAccess(ServerWebExchange exchange, String traceId, long startNanos) {
        int status = exchange.getResponse().getStatusCode() == null ? 200 : exchange.getResponse().getStatusCode().value();
        withTraceId(traceId, () -> log.info(
            "gateway_request method={} path={} status={} durationMs={} traceId={}",
            exchange.getRequest().getMethod(),
            exchange.getRequest().getURI().getPath(),
            status,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
            traceId
        ));
    }

    private void logError(ServerWebExchange exchange, String traceId, long startNanos, Throwable error) {
        withTraceId(traceId, () -> log.warn(
            "gateway_request method={} path={} status=ERROR durationMs={} traceId={}",
            exchange.getRequest().getMethod(),
            exchange.getRequest().getURI().getPath(),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
            traceId,
            error
        ));
    }

    private void withTraceId(String traceId, Runnable action) {
        MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId);
        try {
            action.run();
        } finally {
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }
}
