# Production readiness runbook

This document defines the runtime baseline for DWK Shop microservices.

## Actuator exposure

All runtime services expose these actuator endpoints:

| Endpoint | Purpose | Exposure |
| --- | --- | --- |
| `/actuator/health` | dependency and application health | internal load balancer and probes |
| `/actuator/health/liveness` | process liveness | orchestrator liveness probe |
| `/actuator/health/readiness` | dependency readiness | orchestrator readiness probe |
| `/actuator/info` | service metadata | internal only |
| `/actuator/metrics` | metric discovery and spot checks | internal only |
| `/actuator/prometheus` | Prometheus scrape endpoint | Prometheus only |
| `/actuator/gateway` | gateway route diagnostics | gateway service only, internal only |

Do not expose actuator endpoints directly to the public internet. Production ingress should expose only the API gateway. Actuator traffic should stay on the cluster or private network and should be protected by network policy, service mesh policy, or platform authentication.

## Prometheus metrics

Each service publishes Prometheus metrics through `/actuator/prometheus`. The shared metric tag `application=${spring.application.name}` is configured for all services.

Recommended scrape jobs:

```yaml
scrape_configs:
  - job_name: dwkshop-gateway
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["gateway:8080"]

  - job_name: dwkshop-services
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - "auth-service:18081"
          - "product-service:18082"
          - "cart-service:18083"
          - "order-service:18084"
          - "aftersale-service:18085"
          - "member-service:18086"
          - "marketing-service:18087"
```

Minimum alerts:

| Alert | Signal | Suggested threshold |
| --- | --- | --- |
| Service down | `up{job=~"dwkshop.*"} == 0` | 1 minute |
| High HTTP error rate | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application) / sum(rate(http_server_requests_seconds_count[5m])) by (application)` | > 2% for 10 minutes |
| High p95 latency | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))` | service SLO dependent |
| JVM memory pressure | `jvm_memory_used_bytes / jvm_memory_max_bytes` | > 85% for 10 minutes |
| Rabbit listener failures | listener error logs and RabbitMQ queue depth | any growth sustained for 10 minutes |

## OpenTelemetry tracing

The services include Micrometer Tracing with the OpenTelemetry bridge and OTLP exporter. Configure the collector endpoint with:

```bash
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://otel-collector:4318/v1/traces
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.10
```

Local defaults sample all traces and export to `http://localhost:4318/v1/traces`. Production should normally sample between `0.01` and `0.10`, then temporarily raise sampling during incident investigation.

Existing `X-Trace-Id` propagation remains supported for operational correlation. OpenTelemetry trace IDs and the legacy `X-Trace-Id` are both emitted in JSON logs when present.

## JSON structured logging

All runtime services use `logback-spring.xml` with Logstash JSON encoding. Each log line includes:

| Field | Meaning |
| --- | --- |
| `@timestamp` | event timestamp |
| `level` | log level |
| `logger_name` | logger |
| `thread_name` | thread |
| `message` | event message |
| `service` | Spring application name |
| `environment` | `SERVICE_ENVIRONMENT`, defaults to `local` |
| `traceId` / `spanId` | MDC trace fields when available |

Use `LOG_LEVEL_ROOT=DEBUG` only for short incident windows. Prefer package-specific overrides from the deployment platform when available.

## Admin audit baseline

The MVP explicitly leaves fine-grained permissions, complete operation logs and exports out of scope. Before production release, admin operations must be upgraded from simple operation logs to append-only audit records with permission checks and operation constraints.

Audit coverage must include these high-risk operations:

| Area | Operations |
| --- | --- |
| Product | create product, edit product, put product on sale, take product off sale |
| SKU | update SKU price, update SKU stock |
| Order | ship order, update logistics |
| Aftersale | approve aftersale, reject aftersale, manual refund |
| Inventory | repair inventory reconciliation differences |
| Marketing | issue coupons |
| Member | adjust points |

Each audit record should capture:

| Field | Purpose |
| --- | --- |
| `operatorId` | Admin user id. |
| `operatorName` | Admin username or display name. |
| `operationType` | Stable operation code, such as `SKU_PRICE_UPDATE` or `ORDER_SHIP`. |
| `bizType` | Business object type, such as `PRODUCT`, `SKU`, `ORDER`, `AFTERSALE`, `COUPON` or `POINT`. |
| `bizId` | Business object id. |
| `beforeValue` | Minimal JSON snapshot before the change. |
| `afterValue` | Minimal JSON snapshot after the change. |
| `reason` | Operator-provided reason. Required for high-risk changes. |
| `ip` | Source IP. |
| `userAgent` | Source user agent. |
| `createdAt` | Audit creation time. |

Production constraints:

- Audit writes must be atomic with the business change, or use a reliable compensation path that makes missing audit records visible.
- High-risk operations must require explicit permissions and a non-empty reason. Do not silently fill the reason on the frontend.
- `beforeValue` and `afterValue` must avoid secrets, tokens and unnecessary plaintext PII.
- Audit records are append-only. Admin pages may search and export them, but must not edit or delete them.
- Search filters must include operator, operation type, business type, business id and time range.

## Deployment checks

1. Confirm all services return `UP` on `/actuator/health/readiness`.
2. Confirm Prometheus can scrape `/actuator/prometheus` for every service.
3. Confirm a request through the gateway creates one correlated log path across gateway and target service.
4. Confirm traces arrive in the configured OpenTelemetry backend.
5. Confirm RabbitMQ queues, DLQs, and parking lot queues exist before releasing traffic.
