# 库存集成事件链路

订单服务不再同步调用商品服务扣减或释放库存。订单创建、订单取消均与订单数据在同一事务中写入 `order_outbox_event`，由发布器确认 RabbitMQ broker ack 后标记为已发送。售后审批继续使用售后 outbox，并由商品服务适配为统一库存事件。

## 契约

统一载荷 `InventoryIntegrationEvent` 包含 `eventId`、`eventType`、`eventVersion`、`orderId`、`orderNo`、`occurredAt` 和 `items(skuId, quantity)`。

- `ORDER_CREATED`（版本 1）：锁定库存，可用库存减少、锁定库存增加。
- `ORDER_CANCELLED`（版本 2）：释放锁定库存。若先于创建到达，写入 `RELEASED` tombstone，迟到的创建不再扣减。
- `REFUND_APPROVED`（版本 3）：审批通过后释放锁定库存；兼容迁移前没有订单 SKU 状态记录的存量订单。

订单侧需要单独保存库存状态 `inventoryStatus`，不要只依赖主订单状态推断库存是否已经锁定：

| 状态 | 含义 |
| --- | --- |
| `LOCK_PENDING` | 订单已创建，`ORDER_CREATED` 已写入 outbox，等待商品服务锁库存结果。 |
| `LOCKED` | 商品服务已成功锁定订单所有 SKU 库存。 |
| `LOCK_FAILED` | 商品服务确认锁库存失败，订单不可继续支付或履约。 |
| `RELEASE_PENDING` | 订单取消或退款审批后，释放库存事件已写入 outbox，等待商品服务释放结果。 |
| `RELEASED` | 商品服务已释放该订单占用的锁定库存。 |

订单创建后先进入 `orderStatus=WAIT_PAY`、`inventoryStatus=LOCK_PENDING`。商品服务消费 `ORDER_CREATED` 成功后回传 `INVENTORY_LOCKED`，订单服务将 `inventoryStatus` 推进为 `LOCKED`；商品服务确认库存不足、SKU 不可用或消息最终进入死信且被判定不可履约时，回传或补偿触发 `INVENTORY_LOCK_FAILED`，订单服务将 `inventoryStatus` 推进为 `LOCK_FAILED` 并自动关闭订单。

`INVENTORY_LOCK_FAILED` 的订单侧处理必须与取消订单保持一致：`orderStatus=CANCELED`、`payStatus=CLOSED`、记录取消原因“库存不足，订单已关闭”，释放已锁定优惠券和已预占积分，并禁止继续支付。用户侧展示“库存不足，订单已关闭”。如果支付回调晚于锁失败到达，支付处理必须重新读取订单并因 `orderStatus=CANCELED` 或 `inventoryStatus=LOCK_FAILED` 拒绝推进到待发货，进入支付冲正/退款流程。

## 可靠性与幂等

商品服务在同一数据库事务内完成库存变更、`inventory_order_item_state` 状态推进和 `inventory_consumed_event` 消费记录写入。消费同时按 `eventId + skuId` 与 `orderId + skuId + eventType` 去重。

RabbitMQ 消费采用 500ms 起始指数退避，最多 4 次；耗尽后拒绝消息，由队列死信参数路由至 `dwkshop.inventory.product.dead`。订单/售后 outbox 发布失败也按指数退避重试，最长 300 秒。
