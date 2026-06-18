# Database ownership and schema split

This inventory is the source of truth for relational table ownership. A column
containing another service's identifier is an integration reference, not a JPA
relationship and not a database foreign key.

| Schema | Owner | Tables | External identifiers (no FK) |
| --- | --- | --- | --- |
| `dwkshop_auth` | auth-service | `user`, `admin_user` | none |
| `dwkshop_product` | product-service | `product_category`, `product`, `product_sku`, `product_notice`, `product_refund_command` | none |
| `dwkshop_cart` | cart-service | `cart_item` | `user_id`, `product_id`, `sku_id` |
| `dwkshop_member` | member-service | `user_address`, `user_point_account`, `user_point_flow` | `user_id`, `biz_id` |
| `dwkshop_marketing` | marketing-service | `coupon`, `coupon_user` | `user_id`, `order_id` |
| `dwkshop_order` | order-service | `trade_order`, `trade_order_item`, `trade_order_amount` | `user_id`, `product_id`, `sku_id` |
| `dwkshop_aftersale` | aftersale-service | `aftersale_order`, `aftersale_refund_flow`, `aftersale_outbox_event` | `order_id`, `user_id` |

Foreign keys are retained only inside one owner/schema. Cross-service consistency
is enforced through internal APIs, commands, events, idempotency keys and local
snapshots.

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
