# Database ownership and schema split

This inventory is the source of truth for relational table ownership. A column
containing another service's identifier is an integration reference, not a JPA
relationship and not a database foreign key.

| Schema | Owner | Tables | External identifiers (no FK) |
| --- | --- | --- | --- |
| `dwkshop_auth` | auth-service | `user`, `admin_user` | none |
| `dwkshop_product` | product-service | `product_category`, `product`, `product_sku`, `product_notice`, `product_refund_command`, `inventory_order_item_state`, `inventory_consumed_event`, `inventory_reconciliation_repair_record` | none |
| `dwkshop_cart` | cart-service | `cart_item` | `user_id`, `product_id`, `sku_id` |
| `dwkshop_member` | member-service | `user_address`, `user_point_account`, `user_point_flow` | `user_id`, `biz_id` |
| `dwkshop_marketing` | marketing-service | `coupon`, `coupon_user` | `user_id`, `order_id` |
| `dwkshop_order` | order-service | `trade_order`, `trade_order_item`, `trade_order_amount`, `order_outbox_event` | `user_id`, `product_id`, `sku_id` |
| `dwkshop_aftersale` | aftersale-service | `aftersale_order`, `aftersale_order_item`, `aftersale_refund_flow`, `aftersale_outbox_event` | `order_id`, `user_id`, `product_id`, `sku_id` |

Foreign keys are retained only inside one owner/schema. Cross-service consistency
is enforced through internal APIs, commands, events, idempotency keys and local
snapshots.

## Exception compensation coverage

The following failure paths must stay covered by automated tests because they
protect the order, inventory, refund and search boundaries after the schema split.

| Scenario | Consistency guard | Required test coverage |
| --- | --- | --- |
| Inventory lock fails while creating an order | Product-service row lock rejects insufficient stock and the order transaction keeps no partial order/outbox data. | Stock lock failure and order-create rollback tests. |
| Inventory events are consumed more than once | Product-service records `eventId + skuId` and business state by `orderId + skuId + eventType`. | Duplicate delivery and duplicate business-key consumer tests. |
| Refund approval is submitted more than once | Aftersale-service locks the aftersale row, returns the already refunded state, and does not append another refund outbox event. | Duplicate approval test with a single `REFUND_APPROVED` outbox row. |
| The same settlement is submitted concurrently | Order-service consumes the `settlementToken` once and persists a single order/outbox pair. | Concurrent order creation test using one token. |
| RabbitMQ is temporarily unavailable | Order and aftersale outbox rows remain `PENDING`, increase `retry_count`, set `next_retry_at`, then become `SENT` after broker recovery. | Outbox publisher failure-then-success compensation test. |
| Elasticsearch is unavailable during search | Product-service treats ES as optional and falls back to database name search. | Search gateway failure/fallback test. |

## Cut-over behavior

Migration `V9__split_service_schemas.sql` creates the seven schemas, grants the
application account access, copies existing data, and restores owner-local foreign
keys. The old `dwkshop` tables are intentionally retained as a rollback source.
After row-count reconciliation and an agreed rollback window, remove them in a
separate, explicitly approved migration.

The migrator must run with a database administrator account because schema creation
and grants are DDL administration operations. Runtime services continue to use the
restricted `dwkshop` account and connect only to their owned schema.

## Verification gates

- Service modules must not use JPA relationship annotations (`@ManyToOne`,
  `@OneToMany`, `@OneToOne`, `@ManyToMany`, `@JoinColumn`).
- Every `@Table` in an extracted service must occur in exactly one row above.
- SQL foreign keys in split schemas may reference only tables in that same schema.
- Services may exchange external identifiers, but resolve them through APIs/events.

These gates are executable in `DatabaseOwnershipTest` and run with the root Maven
test lifecycle. The test parses this table directly; do not maintain a second
ownership list in code.
