# RabbitMQ DLQ, retry, and parking lot policy

DWK Shop uses RabbitMQ for inventory and refund integration events. Producers use the outbox pattern; consumers use listener retry, DLQ routing, and explicit parking lot queues for manual quarantine.

## Topology

| Flow | Exchange | Routing key | Consumer queue | DLQ | Parking lot |
| --- | --- | --- | --- | --- | --- |
| Order created inventory lock | `dwkshop.inventory.exchange` | `inventory.order-created` | `dwkshop.inventory.product` | `dwkshop.inventory.product.dead` | `dwkshop.inventory.product.parking-lot` |
| Order cancelled inventory release | `dwkshop.inventory.exchange` | `inventory.order-cancelled` | `dwkshop.inventory.product` | `dwkshop.inventory.product.dead` | `dwkshop.inventory.product.parking-lot` |
| Refund approved to product | `dwkshop.refund.exchange` | `refund.approved` | `dwkshop.refund.approved.product` | `dwkshop.refund.approved.product.dead` | `dwkshop.refund.approved.product.parking-lot` |
| Refund approved to order | `dwkshop.refund.exchange` | `refund.approved` | `dwkshop.refund.approved.order` | `dwkshop.refund.approved.order.dead` | `dwkshop.refund.approved.order.parking-lot` |

## Producer retry

Order and aftersale publishers use database outbox tables. Publishing is retried from `PENDING` rows with exponential backoff up to 300 seconds. A message is marked `SENT` only after publisher confirm acknowledgement and no returned unroutable message.

Operational checks:

```sql
SELECT publish_status, COUNT(*) FROM order_outbox_event GROUP BY publish_status;
SELECT publish_status, COUNT(*) FROM aftersale_outbox_event GROUP BY publish_status;
```

Investigate any `PENDING` row whose `next_retry_at` is older than the current time and whose `retry_count` keeps growing.

## Consumer retry

Consumer listener retry is configurable:

```bash
DWKSHOP_MQ_RETRY_MAX_ATTEMPTS=4
DWKSHOP_MQ_RETRY_INITIAL_INTERVAL_MS=500
DWKSHOP_MQ_RETRY_MULTIPLIER=2.0
DWKSHOP_MQ_RETRY_MAX_INTERVAL_MS=5000
```

After local retry is exhausted, the message is rejected without requeue and RabbitMQ routes it to the queue DLQ through the configured dead-letter exchange and routing key.

## DLQ handling

DLQ messages mean automatic consumer retry has failed. The on-call engineer should:

1. Inspect the message payload and exception logs by `eventId`, `orderId`, and `traceId`.
2. Classify the failure as transient dependency, bad payload, duplicate/out-of-order event, or data inconsistency.
3. Fix the root cause before replay.
4. Replay only messages that are safe under the consumer idempotency rules.

Idempotency anchors:

| Consumer | Idempotency |
| --- | --- |
| `InventoryIntegrationEventConsumer` | `eventId + skuId` and `orderId + skuId + eventType` |
| `RefundApprovedConsumer` in order service | order aftersale status transition guards |

## Parking lot policy

Parking lot queues are durable manual quarantine queues. Move a DLQ message to parking lot when:

- the payload is invalid and cannot be safely replayed;
- the business state has been manually repaired;
- replay would cause duplicate compensation or inventory movement;
- the incident requires later audit instead of immediate processing.

Do not delete messages directly from DLQ during an incident. Move them to parking lot with an incident ID in the audit record.

Recommended audit fields:

| Field | Example |
| --- | --- |
| incident_id | `INC-2026-06-23-001` |
| source_queue | `dwkshop.inventory.product.dead` |
| parking_lot_queue | `dwkshop.inventory.product.parking-lot` |
| event_id | event UUID |
| order_id | trade order ID |
| operator | on-call engineer |
| reason | bad payload, manually compensated |
| action_time | timestamp |

## Replay guardrails

Replay from DLQ to the original exchange only after:

1. The service version with the fix is deployed.
2. The target service readiness probe is `UP`.
3. Queue consumers are healthy and not already backlogged.
4. A single message replay has been verified before bulk replay.
5. Metrics and logs are watched for at least one retry window.
