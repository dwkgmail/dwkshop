package com.dwkshop.gateway.observability;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdGlobalFilterTest {

    private final TraceIdGlobalFilter filter = new TraceIdGlobalFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesTraceIdThroughGatewayRequestAndResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/orders/1")
                .header(TraceContext.TRACE_ID_HEADER, "trace-gateway-1")
                .build()
        );
        AtomicReference<ServerWebExchange> seenExchange = new AtomicReference<>();

        filter.filter(exchange, chainedExchange -> {
            seenExchange.set(chainedExchange);
            ServerHttpRequest request = chainedExchange.getRequest();
            assertThat(request.getHeaders().getFirst(TraceContext.TRACE_ID_HEADER)).isEqualTo("trace-gateway-1");
            return Mono.empty();
        }).block();

        assertThat(seenExchange.get()).isNotNull();
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER)).isEqualTo("trace-gateway-1");
        assertThat(MDC.get(TraceContext.TRACE_ID_MDC_KEY)).isNull();
    }
}
