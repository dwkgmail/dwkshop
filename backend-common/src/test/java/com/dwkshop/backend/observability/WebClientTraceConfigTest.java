package com.dwkshop.backend.observability;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientTraceConfigTest {

    private final WebClientTraceConfig config = new WebClientTraceConfig();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesTraceIdToOutboundWebClientRequests() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            capturedRequest.set(request);
            return Mono.just(org.springframework.web.reactive.function.client.ClientResponse
                .create(HttpStatus.OK)
                .build());
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        config.traceAwareWebClientCustomizer().customize(builder);

        MDC.put(TraceContext.TRACE_ID_MDC_KEY, "trace-456");
        try {
            builder.build()
                .get()
                .uri("http://example.test/internal/products/skus/1/snapshot")
                .retrieve()
                .toBodilessEntity()
                .block();
        } finally {
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }

        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().headers().getFirst(TraceContext.TRACE_ID_HEADER)).isEqualTo("trace-456");
    }
}
