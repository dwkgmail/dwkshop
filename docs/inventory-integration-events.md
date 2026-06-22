# 库存集成事件链路

订单服务不再同步调用商品服务扣减或释放库存。订单创建、订单取消均与订单数据在同一事务中写入 `order_outbox_event`，由发布器确认 RabbitMQ broker ack 后标记为已发送。售后审批继续使用售后 outbox，并由商品服务适配为统一库存事件。

## 契约

统一载荷 `InventoryIntegrationEvent` 包含 `eventId`、`eventType`、`eventVersion`、`orderId`、`orderNo`、`occurredAt` 和 `items(skuId, quantity)`。

- `ORDER_CREATED`（版本 1）：锁定库存，可用库存减少、锁定库存增加。
- `ORDER_CANCELLED`（版本 2）：释放锁定库存。若先于创建到达，写入 `RELEASED` tombstone，迟到的创建不再扣减。
- `REFUND_APPROVED`（版本 3）：审批通过后释放锁定库存；兼容迁移前没有订单 SKU 状态记录的存量订单。

## 可靠性与幂等

商品服务在同一数据库事务内完成库存变更、`inventory_order_item_state` 状态推进和 `inventory_consumed_event` 消费记录写入。消费同时按 `eventId + skuId` 与 `orderId + skuId + eventType` 去重。

RabbitMQ 消费采用 500ms 起始指数退避，最多 4 次；耗尽后拒绝消息，由队列死信参数路由至 `dwkshop.inventory.product.dead`。订单/售后 outbox 发布失败也按指数退避重试，最长 300 秒。
