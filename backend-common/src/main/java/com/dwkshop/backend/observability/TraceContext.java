package com.dwkshop.backend.observability;

import java.util.UUID;

public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private TraceContext() {
    }

    public static String normalize(String candidate) {
        if (candidate == null) {
            return generate();
        }
        String normalized = candidate.trim();
        return normalized.isEmpty() ? generate() : normalized;
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
