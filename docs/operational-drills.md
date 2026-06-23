# Operational drills

Run these drills in staging before major releases and at least once per quarter. Record date, operator, environment, observations, and remediation items.

## Drill 1: Service dependency failure

Goal: verify readiness probes, gateway behavior, logs, metrics, and traces when a dependency is unavailable.

Steps:

1. Start the full microservice stack.
2. Stop `product-service`.
3. Call product, cart, and order APIs through the gateway.
4. Confirm gateway and dependent services emit JSON error logs with `traceId`.
5. Confirm `/actuator/health/readiness` is not `UP` where the dependency is required.
6. Confirm Prometheus shows increased 5xx or client error metrics.
7. Restart `product-service` and verify recovery without manual data repair.

Pass criteria:

- failed requests are bounded and observable;
- healthy services remain reachable;
- readiness returns to `UP` after recovery;
- no outbox rows or RabbitMQ queues remain unexpectedly stuck.

## Drill 2: RabbitMQ consumer failure

Goal: verify retry, DLQ, and parking lot handling.

Steps:

1. Configure a staging message that the consumer rejects, such as an inventory event with an unknown SKU.
2. Publish it to the original exchange and routing key.
3. Confirm listener retries according to `DWKSHOP_MQ_RETRY_*`.
4. Confirm the message lands in the expected `.dead` queue.
5. Move the message to the matching `.parking-lot` queue and record an incident audit row or ticket.
6. Publish one known-good message and confirm normal consumption still works.

Pass criteria:

- poison messages do not block healthy messages;
- DLQ and parking lot queue names match `docs/rabbitmq-resilience.md`;
- replay or quarantine action is audited.

## Drill 3: OpenTelemetry outage

Goal: verify service availability when the trace collector is unavailable.

Steps:

1. Point `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` to an unreachable collector.
2. Send normal user and admin API traffic.
3. Confirm application requests continue to succeed.
4. Confirm exporter failures are visible but not noisy enough to hide business errors.
5. Restore the collector endpoint and confirm traces resume.

Pass criteria:

- request latency and error rate stay within staging SLO;
- application logs remain JSON and queryable by `traceId`;
- no service restart is required after collector recovery.

## Drill 4: Database backup and restore

Goal: prove that MySQL backup artifacts are restorable and match application expectations.

Backup steps:

1. Freeze write traffic or run against a read replica/snapshot-capable platform.
2. Capture schema and data for every service schema: `dwkshop_auth`, `dwkshop_product`, `dwkshop_cart`, `dwkshop_member`, `dwkshop_marketing`, `dwkshop_order`, and `dwkshop_aftersale`.
3. Store the backup with timestamp, git commit, migration version, checksum, and retention class.
4. Verify backup upload and checksum.

Restore rehearsal:

1. Create a clean staging database instance.
2. Restore all service schemas from the backup.
3. Run `db-migrator` for the target release.
4. Start all services with runtime accounts, not the migrator account.
5. Run smoke tests for login, product search, cart, order creation, payment simulation, shipment, and refund.
6. Rebuild Elasticsearch product index from MySQL.

Pass criteria:

- restored schemas start without Flyway drift;
- key business flows pass;
- outbox tables do not unexpectedly replay old already-sent events;
- RTO and RPO are recorded.

## Drill 5: Elasticsearch rebuild

Goal: verify product search can be recovered from MySQL.

Steps:

1. Confirm MySQL product data is healthy.
2. Delete or rename the product index in staging.
3. Restart `product-service`.
4. Confirm startup reindexes products.
5. Compare product detail and search results for a sample set.

Pass criteria:

- search recovers without manual SQL writes;
- users receive either correct search results or a controlled degradation path during rebuild.
