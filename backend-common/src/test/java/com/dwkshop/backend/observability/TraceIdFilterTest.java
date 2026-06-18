package com.dwkshop.backend.observability;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesIncomingTraceIdAndPublishesItToMdcAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/1");
        request.addHeader(TraceContext.TRACE_ID_HEADER, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenTraceId = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            seenTraceId.set(MDC.get(TraceContext.TRACE_ID_MDC_KEY));
            assertThat(((HttpServletRequest) servletRequest).getHeader(TraceContext.TRACE_ID_HEADER)).isEqualTo("trace-123");
        });

        assertThat(seenTraceId.get()).isEqualTo("trace-123");
        assertThat(response.getHeader(TraceContext.TRACE_ID_HEADER)).isEqualTo("trace-123");
        assertThat(MDC.get(TraceContext.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void generatesTraceIdWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenTraceId = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            seenTraceId.set(MDC.get(TraceContext.TRACE_ID_MDC_KEY));
            assertThat(((HttpServletRequest) servletRequest).getHeader(TraceContext.TRACE_ID_HEADER)).isNull();
        });

        assertThat(seenTraceId.get()).isNotBlank();
        assertThat(response.getHeader(TraceContext.TRACE_ID_HEADER)).isEqualTo(seenTraceId.get());
        assertThat(MDC.get(TraceContext.TRACE_ID_MDC_KEY)).isNull();
    }
}
