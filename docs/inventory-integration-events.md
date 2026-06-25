# 库存集成事件链路

订单服务不再同步调用商品服务扣减或释放库存。订单创建、订单取消均与订单数据在同一事务中写入 `order_outbox_event`，由发布器确认 RabbitMQ broker ack 后标记为已发送。售后审批继续使用售后 outbox，并由商品服务适配为统一库存事件。

## 契约

统一载荷 `InventoryIntegrationEvent` 包含 `eventId`、`eventType`、`eventVersion`、`orderId`、`orderNo`、`occurredAt` 和 `items(skuId, quantity)`。

库存字段口径：

| 字段 | 含义 |
| --- | --- |
| `availableStock` | 可售库存。当前数据库字段为 `product_sku.stock`；下单锁库时减少，未支付取消或发货前退款释放时增加。 |
| `lockedStock` | 已被订单占用但尚未最终出库/结算的库存。当前数据库字段为 `product_sku.locked_stock`；包含下单未支付和已支付待发货的占用。 |
| `soldStock` | 已支付确认销售或已出库的数量。当前未单独落库，支付成功后应从 `lockedStock` 转入 `soldStock` 或更细的待履约占用字段。 |
| `returnedStock` | 退货入库或待质检的退回数量。当前未单独落库；发货后退货应按质检结果决定进入 `availableStock` 或 `returnedStock`。 |
| `safetyStock` | 安全库存，不应对外可售。当前未单独落库；售卖校验应使用 `availableStock - safetyStock`。 |
| `oversellLimit` | 可选超卖额度。当前不支持；若启用，锁库校验可放宽到 `availableStock - safetyStock + oversellLimit`。 |

事件语义：

- `ORDER_CREATED`（版本 1）：下单锁库存，`availableStock -= quantity`、`lockedStock += quantity`。当前实现对应 `stock -= quantity`、`locked_stock += quantity`。
- `ORDER_CANCELLED`（版本 2）：取消未支付订单或支付前关闭订单，释放锁定库存，`availableStock += quantity`、`lockedStock -= quantity`。若先于创建到达，写入 `RELEASED` tombstone，迟到的创建不再扣减。
- `PAYMENT_SUCCEEDED`（建议新增）：支付成功后不应继续只停留在“可释放锁定库存”语义，应将库存从 `lockedStock` 转为 `soldStock`，或转入单独的待履约占用字段。当前系统尚未发布该库存事件，已支付待发货订单仍复用 `lockedStock` 表达占用。
- `REFUND_APPROVED`（版本 3）：当前实现仅适合发货前退款，释放已锁定库存，兼容迁移前没有订单 SKU 状态记录的存量订单。发货后售后不应默认恢复可售库存，需要区分“仅退款不退货”和“退货入库”。
- `RETURN_RECEIVED`（建议新增）：发货后退货实际入库或质检完成后再调整库存；可二次销售时 `availableStock += quantity`，不可直接销售时 `returnedStock += quantity`。

订单侧需要单独保存库存状态 `inventoryStatus`，不要只依赖主订单状态推断库存是否已经锁定：

| 状态 | 含义 |
| --- | --- |
| `LOCK_PENDING` | 订单已创建，`ORDER_CREATED` 已写入 outbox，等待商品服务锁库存结果。 |
| `LOCKED` | 商品服务已成功锁定订单所有 SKU 库存。 |
| `LOCK_FAILED` | 商品服务确认锁库存失败，订单不可继续支付或履约。 |
| `RELEASE_PENDING` | 订单取消或退款审批后，释放库存事件已写入 outbox，等待商品服务释放结果。 |
| `RELEASED` | 商品服务已释放该订单占用的锁定库存。 |

订单创建后先进入 `orderStatus=WAIT_PAY`、`inventoryStatus=LOCK_PENDING`。商品服务消费 `ORDER_CREATED` 成功后回传 `INVENTORY_LOCKED`，订单服务将 `inventoryStatus` 推进为 `LOCKED`；商品服务确认库存不足、SKU 不可用或消息最终进入死信且被判定不可履约时，回传或补偿触发 `INVENTORY_LOCK_FAILED`，订单服务将 `inventoryStatus` 推进为 `LOCK_FAILED` 并自动关闭订单。

支付成功时，订单会进入 `orderStatus=WAIT_SHIP`、`payStatus=PAID`。从业务口径看，这一步应把库存占用从“支付前锁定”升级为“已售/待履约占用”；当前实现尚未单独记录 `soldStock`，因此已支付待发货库存仍留在 `lockedStock`，只有取消未支付订单或发货前退款才应释放回 `availableStock`。

`INVENTORY_LOCK_FAILED` 的订单侧处理必须与取消订单保持一致：`orderStatus=CANCELED`、`payStatus=CLOSED`、记录取消原因“库存不足，订单已关闭”，释放已锁定优惠券和已预占积分，并禁止继续支付。用户侧展示“库存不足，订单已关闭”。如果支付回调晚于锁失败到达，支付处理必须重新读取订单并因 `orderStatus=CANCELED` 或 `inventoryStatus=LOCK_FAILED` 拒绝推进到待发货，进入支付冲正/退款流程。

## 可靠性与幂等

商品服务在同一数据库事务内完成库存变更、`inventory_order_item_state` 状态推进和 `inventory_consumed_event` 消费记录写入。消费同时按 `eventId + skuId` 与 `orderId + skuId + eventType` 去重。

RabbitMQ 消费采用 500ms 起始指数退避，最多 4 次；耗尽后拒绝消息，由队列死信参数路由至 `dwkshop.inventory.product.dead`。订单/售后 outbox 发布失败也按指数退避重试，最长 300 秒。
