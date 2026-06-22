# 库存事件链路观测与补偿

本文面向生产值班、故障排查和人工补偿，覆盖订单创建/取消以及售后审批触发的库存事件。事件链路如下：

```text
order-service
  -> dwkshop_order.order_outbox_event
  -> dwkshop.inventory.exchange
  -> dwkshop.inventory.product
  -> product-service
  -> product_sku + inventory_order_item_state + inventory_consumed_event

aftersale-service
  -> dwkshop_aftersale.aftersale_outbox_event
  -> dwkshop.refund.exchange
  -> dwkshop.refund.approved.product
  -> product-service（适配为 REFUND_APPROVED 库存事件）
  -> 同上三张商品库表
```

订单和 outbox 在订单库的同一事务内写入；商品服务在商品库的同一事务内完成 SKU 库存变更、订单 SKU 状态推进和成功消费记录写入。因此应先定位事件停在哪一段，再决定是否需要补偿。

## 1. 事件语义

统一库存事件载荷包含：`eventId`、`eventType`、`eventVersion`、`orderId`、`orderNo`、`occurredAt` 和 `items(skuId, quantity)`。

| eventType | version | 作用 | 成功后的状态 |
| --- | ---: | --- | --- |
| `ORDER_CREATED` | 1 | `stock -= quantity`，`locked_stock += quantity` | `LOCKED` |
| `ORDER_CANCELLED` | 2 | 释放已锁定库存 | `RELEASED` |
| `REFUND_APPROVED` | 3 | 待发货退款审批后释放已锁定库存 | `RELEASED` |

`ORDER_CANCELLED` 先到时会写入 `RELEASED` tombstone，但不修改 SKU 库存；迟到的 version 1 创建事件会被视为旧事件，不再锁库存。成功处理的旧事件仍会写入消费记录。

## 2. 数据表说明

### 2.1 `dwkshop_order.order_outbox_event`

| 字段 | 含义 |
| --- | --- |
| `id` | outbox 自增主键，也是发布扫描顺序 |
| `event_id` | 全局事件 ID；broker confirm 的 correlation id；唯一 |
| `aggregate_id` | 订单 `trade_order.id` |
| `event_type` | `ORDER_CREATED` 或 `ORDER_CANCELLED` |
| `routing_key` | `inventory.order-created` 或 `inventory.order-cancelled` |
| `payload_json` | 完整的 `InventoryIntegrationEvent` JSON |
| `publish_status` | 当前只有 `PENDING`、`SENT` |
| `retry_count` | 发布失败次数；成功后不会清零 |
| `next_retry_at` | 下一次允许扫描发布的时间 |
| `last_error` | 最近一次发布错误，最多 255 字符；成功后清空 |
| `published_at` | 收到 broker ack 且确认可路由后置为发送时间 |
| `created_at` / `updated_at` | 创建和最后一次发布尝试更新时间 |

约束：`event_id` 唯一；同一订单的同一 `event_type` 只允许一条记录。

发布器默认每 1 秒扫描一次到期的 `PENDING`，每批最多 50 条。发送后最多等待 5 秒 broker confirm；broker nack、超时、连接失败或消息不可路由都会保留 `PENDING`，按指数退避重试，最长间隔 300 秒。

售后释放使用结构相同的 `dwkshop_aftersale.aftersale_outbox_event`。其 `aggregate_id` 是 `aftersale_order.id`，事件类型为 `REFUND_APPROVED`；exchange 和 routing key 由发布器配置，不单独存储在表中。

### 2.2 `dwkshop_product.inventory_order_item_state`

| 字段 | 含义 |
| --- | --- |
| `id` | 自增主键 |
| `order_id` / `sku_id` | 订单 SKU 业务键，组合唯一 |
| `quantity` | 该事件链路处理的商品数量 |
| `state` | `LOCKED` 或 `RELEASED` |
| `last_event_version` | 已处理到的最高事件版本，正常为 1、2 或 3 |
| `updated_at` | 状态最后更新时间 |

该表是每个订单 SKU 的库存状态投影，不是事件历史。`RELEASED` 同时承担乱序消息 tombstone 的作用。

### 2.3 `dwkshop_product.inventory_consumed_event`

| 字段 | 含义 |
| --- | --- |
| `id` | 自增主键 |
| `event_id` | 原始事件 ID |
| `order_id` / `sku_id` | 消费对应的订单 SKU |
| `event_type` | 成功处理的事件类型 |
| `consumed_at` | 商品库事务成功提交的消费时间 |

幂等键有两组：`event_id + sku_id` 和 `order_id + sku_id + event_type`。重复消息会直接跳过，既不重复修改库存，也不新增消费记录。

这张表只记录成功消费。消费异常时，库存、状态和消费记录会在同一事务中回滚，所以不能用“没有消费记录”区分仍在重试、已死信或从未到达，必须结合队列和日志判断。

## 3. RabbitMQ 拓扑

所有 exchange、queue 都是 durable，exchange 类型为 direct。

| Exchange | Routing key | Queue | 消费方/用途 |
| --- | --- | --- | --- |
| `dwkshop.inventory.exchange` | `inventory.order-created` | `dwkshop.inventory.product` | 商品服务锁库存 |
| `dwkshop.inventory.exchange` | `inventory.order-cancelled` | `dwkshop.inventory.product` | 商品服务释放库存 |
| `dwkshop.inventory.exchange` | `inventory.product.dead` | `dwkshop.inventory.product.dead` | 库存消费死信 |
| `dwkshop.refund.exchange` | `refund.approved` | `dwkshop.refund.approved.product` | 商品服务释放库存 |
| `dwkshop.refund.exchange` | `refund.approved.product.dead` | `dwkshop.refund.approved.product.dead` | 商品侧退款消费死信 |
| `dwkshop.refund.exchange` | `refund.approved` | `dwkshop.refund.approved.order` | 订单服务处理退款结果 |
| `dwkshop.refund.exchange` | `refund.approved.order.dead` | `dwkshop.refund.approved.order.dead` | 订单侧退款消费死信 |

商品库存消费者采用 500ms 起始、2 倍递增、单次最多 5 秒的退避，合计最多尝试 4 次。全部失败后拒绝且不回原队列，由队列的 dead-letter 参数路由至对应 `.dead` 队列。

## 4. 日常观测和告警建议

RabbitMQ 管理台默认地址为 `http://localhost:15672`；本地默认账号见 `docker-compose.yml`，生产环境使用实际密钥。管理台重点查看 Queues and Streams 页面的 Ready、Unacked、Total、Consumers、Incoming 和 Deliver/Get。

也可在部署目录执行：

```bash
docker compose exec rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers arguments
docker compose exec rabbitmq rabbitmqctl list_bindings source_name destination_name routing_key
```

微服务 Compose 的日志检查示例：

```bash
docker compose -f docker-compose.microservices.yml logs --since 30m order-service
docker compose -f docker-compose.microservices.yml logs --since 30m product-service
docker compose -f docker-compose.microservices.yml logs --since 30m aftersale-service
```

建议至少建立以下告警，阈值应根据实际吞吐校准：

| 指标 | 建议初始条件 | 含义 |
| --- | --- | --- |
| order outbox 到期积压 | `PENDING` 且 `next_retry_at <= NOW()` 持续 5 分钟 | 发布器未工作、RabbitMQ 异常或持续 nack |
| 最老 outbox 年龄 | 最老 `PENDING` 超过 5 分钟 | 链路端到端延迟风险 |
| 主队列 Ready | 持续增长 5 分钟 | 消费者不可用或消费能力不足 |
| 主队列 Consumers | 等于 0 持续 1 分钟 | 商品服务未启动、监听器未启动或连接断开 |
| 主队列 Unacked | 长时间不下降 | 消费卡住、数据库锁等待或慢事务 |
| 任一库存相关死信队列 | `messages > 0` | 存在必须排查的最终消费失败 |
| 发布失败日志/`retry_count` | 短时间连续增长 | broker confirm、路由或连接异常 |

### outbox 积压 SQL

```sql
-- 当前积压规模、最老事件和最大重试次数
SELECT publish_status,
       COUNT(*) AS event_count,
       MIN(created_at) AS oldest_created_at,
       TIMESTAMPDIFF(SECOND, MIN(created_at), NOW()) AS oldest_age_seconds,
       MAX(retry_count) AS max_retry_count
FROM dwkshop_order.order_outbox_event
GROUP BY publish_status;

-- 已到期但仍未发送的事件
SELECT id, event_id, aggregate_id AS order_id, event_type, routing_key,
       retry_count, next_retry_at, last_error, created_at, updated_at
FROM dwkshop_order.order_outbox_event
WHERE publish_status = 'PENDING'
  AND (next_retry_at IS NULL OR next_retry_at <= NOW())
ORDER BY id
LIMIT 200;

-- 售后 outbox 同类检查
SELECT id, event_id, aggregate_id AS aftersale_id, event_type,
       retry_count, next_retry_at, last_error, created_at, updated_at
FROM dwkshop_aftersale.aftersale_outbox_event
WHERE publish_status = 'PENDING'
  AND (next_retry_at IS NULL OR next_retry_at <= NOW())
ORDER BY id
LIMIT 200;
```

`next_retry_at IS NULL` 的 `PENDING` 不会被当前发布器扫描，应视为异常数据。

## 5. 按订单排查

先用订单号定位 `order_id`，再按下面顺序判断链路停点。不要只看订单状态或单张库存表。

```sql
SET @order_no = '替换为订单号';

SELECT id, order_no, order_status, pay_status, delivery_status,
       aftersale_status, created_at, cancel_time, updated_at
FROM dwkshop_order.trade_order
WHERE order_no = @order_no;

SET @order_id = (
  SELECT id FROM dwkshop_order.trade_order WHERE order_no = @order_no LIMIT 1
);

-- 订单商品和期望数量
SELECT id, order_id, product_id, sku_id, quantity, support_refund, aftersale_quantity
FROM dwkshop_order.trade_order_item
WHERE order_id = @order_id
ORDER BY id;

-- 是否生成、发布了创建/取消事件；payload_json 可核对 sku 和数量
SELECT id, event_id, event_type, routing_key, publish_status, retry_count,
       next_retry_at, last_error, published_at, created_at, payload_json
FROM dwkshop_order.order_outbox_event
WHERE aggregate_id = @order_id
ORDER BY id;

-- 商品服务当前状态投影
SELECT s.order_id, s.sku_id, s.quantity, s.state, s.last_event_version,
       s.updated_at, sku.stock, sku.locked_stock, sku.sku_status
FROM dwkshop_product.inventory_order_item_state s
LEFT JOIN dwkshop_product.product_sku sku ON sku.id = s.sku_id
WHERE s.order_id = @order_id
ORDER BY s.sku_id;

-- 商品服务成功消费历史
SELECT id, event_id, order_id, sku_id, event_type, consumed_at
FROM dwkshop_product.inventory_consumed_event
WHERE order_id = @order_id
ORDER BY consumed_at, id;
```

判定顺序：

1. 没有 outbox：订单事务未生成事件、历史订单不在事件化范围，或数据被异常修改。
2. outbox 为 `PENDING`：看 `last_error`、`retry_count`、`next_retry_at` 和订单服务日志。
3. outbox 为 `SENT` 但没有消费记录：看主队列、死信队列和商品服务日志。
4. 有消费记录但状态不符：检查事件版本、每个 SKU 是否都成功消费，以及是否有人工改库；单纯重放通常无效，因为幂等记录会阻止二次执行。
5. 状态正确但 SKU 汇总库存可疑：需要结合其他订单的 `LOCKED` 状态做全量对账，不能把某个 SKU 的 `locked_stock` 全部归因于当前订单。

SKU 锁定库存对账参考：

```sql
SELECT sku.id AS sku_id,
       sku.stock,
       sku.locked_stock,
       COALESCE(SUM(CASE WHEN s.state = 'LOCKED' THEN s.quantity ELSE 0 END), 0) AS projected_locked,
       sku.locked_stock - COALESCE(SUM(CASE WHEN s.state = 'LOCKED' THEN s.quantity ELSE 0 END), 0) AS difference
FROM dwkshop_product.product_sku sku
LEFT JOIN dwkshop_product.inventory_order_item_state s ON s.sku_id = sku.id
GROUP BY sku.id, sku.stock, sku.locked_stock
HAVING difference <> 0
ORDER BY ABS(difference) DESC;
```

## 6. 常见异常

### 6.1 outbox 长期 `PENDING`

常见原因：订单服务实例/调度器未运行、RabbitMQ 连接或认证失败、broker confirm 超时/nack、exchange 不存在、routing key 无绑定、载荷反序列化失败，或 `next_retry_at` 为 NULL/异常未来时间。

处理：先检查订单服务健康和日志，再核对 exchange、binding、消费者数量。修复根因后发布器会自动重试。积压很大时要观察每批 50 条的排空速度，避免同时手工批量重放造成重复流量。

### 6.2 publisher confirm 失败

- `Broker rejected message: ...`：broker nack，检查 RabbitMQ 日志、资源告警和 exchange 状态。
- confirm 等待超时/连接异常：检查网络、RabbitMQ 节点和连接数。
- `Message was unroutable`：消息到达 exchange 但没有匹配 binding；核对 exchange、routing key 和队列声明。

confirm 失败时记录仍为 `PENDING`。即使发生“broker 已收但客户端未确认”的不确定结果，自动重试也是安全路径，商品消费者会按两个幂等键去重。

### 6.3 商品服务消费失败

当前没有单独的消费失败表。前 4 次失败从 `product-service` 日志观察；最终失败从 `.dead` 队列观察。失败消息对应的商品库存、状态投影和消费记录均会回滚。

常见错误包括：事件/商品项字段非法、未知 SKU、数据库异常、库存不足、锁定库存不足和不支持的事件类型。先修复可重试的业务/数据原因，再重放原消息。

### 6.4 库存不足

`ORDER_CREATED` 要求 `product_sku.stock >= quantity`，否则抛出 `Insufficient stock for sku ...`。一个事件包含多个 SKU 时，任一 SKU 失败会回滚整条消息已做的所有变更，重试 4 次后进入 `dwkshop.inventory.product.dead`。

不要通过删除消费记录或临时把数量改小来绕过。应先确认订单是否仍应履约：若应履约，按正式入库/库存校准流程补足可用库存后重放；若不应履约，由订单侧走取消流程并保留审计记录。

### 6.5 取消消息先到

这是被支持的乱序场景。商品服务写入 `RELEASED`、version 2 tombstone，不改变 SKU 库存；后到的 `ORDER_CREATED` version 1 会被标记成功消费但不会锁库存。若看到该组合，无需人工补库存。

### 6.6 重复消息

同一个 `event_id + sku_id`，或同一个 `order_id + sku_id + event_type` 的重复消息都会被忽略。重复投递本身不是故障，通常来自 confirm 结果不确定后的 outbox 重试。

若消费记录存在但库存状态错误，重放不会修复；不要先删除 `inventory_consumed_event` 再试，因为这可能重复扣减或释放库存。

## 7. 死信排查与安全重放

在 RabbitMQ 管理台检查以下队列：

- `dwkshop.inventory.product.dead`
- `dwkshop.refund.approved.product.dead`
- `dwkshop.refund.approved.order.dead`

打开死信队列后，用 Get messages 的 requeue 方式读取样本，检查 payload 及 `x-death` 头中的原队列、reason、count 和原 routing key。读取排查时不要使用会删除消息的 ack 模式。

安全重放步骤：

1. 记录事件 ID、订单 ID、SKU、死信原因、原 exchange/routing key 和处理人。
2. 用本文 SQL 确认该事件尚未成功消费，或确认重复消费会被幂等保护。
3. 修复根因；未修复就重放只会再次死信。
4. 将原始 payload 原样发布到原 exchange 和原 routing key，不要发布到 dead routing key，也不要改 `eventId`。
5. 确认主队列清空、对应消费记录和状态落库、SKU 变化符合预期。
6. 最后删除/ack 原死信，并把前后证据附到工单。若重放后仍失败，保留死信继续分析。

管理台手工“取出后再发布”不是原子操作。应先以 requeue 方式复制 payload，成功发布并验证后再删除原死信，避免在两步之间丢消息。批量死信应使用受控重放脚本/任务，逐条保留结果，禁止在管理台盲目全量重发。

## 8. 人工补偿原则

优先级固定为：自动重试 > 原事件重放 > 受控业务补偿 > 最后才是人工改库。

### 8.1 允许的低风险操作

确认根因已修复后，可以让仍为 `PENDING` 的 outbox 立即进入下一轮扫描：

```sql
-- 执行前先按 event_id 查询并在工单留存原值
UPDATE dwkshop_order.order_outbox_event
SET next_retry_at = NOW(), updated_at = NOW()
WHERE event_id = '替换为事件ID'
  AND publish_status = 'PENDING';
```

该操作只调整重试时间，不改 `payload_json`、`event_id`、`retry_count` 或业务数据。售后 outbox 可采用同样方式操作对应表。

### 8.2 禁止直接执行的操作

- 不要把 `PENDING` 直接改成 `SENT`；`SENT` 代表已经获得 broker ack 且可路由。
- 不要把 `SENT` 改回 `PENDING`，除非已经证明消息未成功消费、死信不存在，并完成影响评估；通常应重放原消息。
- 不要删除 `inventory_consumed_event` 来强迫重放。
- 不要单独修改 `inventory_order_item_state`，它必须与 `product_sku.stock/locked_stock` 一致。
- 不要修改历史 `payload_json` 或复用事件 ID 表达一个新的业务动作。
- 不要为缺失 outbox 临时手写一条记录；应通过可审计的补偿接口/任务生成符合契约的新事件。

### 8.3 必须人工校准库存时

仅当事件已成功消费但数据库状态仍因历史数据、旧版本缺陷或人工操作而不一致，且重放受幂等保护无法修复时，才进入人工校准：

1. 暂停该订单/SKU 的相关业务写入或取得必要锁，防止校准期间继续变化。
2. 保存订单、outbox、消费记录、状态投影和 SKU 的完整前置快照。
3. 根据所有 `LOCKED` 状态做 SKU 级对账，明确 `stock`、`locked_stock`、状态和版本各自的目标值。
4. 由两人复核补偿 SQL，在单个数据库事务内同时修正相关数据；不得只改一个字段。
5. 提交后重新执行订单排查和 SKU 对账 SQL，并观察队列、日志。
6. 工单记录原因、事件 ID、执行 SQL、影响行数、前后值、执行人和复核人。

当前模型没有独立的库存流水表，直接改库存的可追溯性有限。因此生产上更推荐补充专用库存校准命令/接口和审计流水，而不是沉淀通用的裸 SQL 改库脚本。

## 9. 快速决策表

| 现象 | 下一步 |
| --- | --- |
| outbox `PENDING`，`last_error` 有值 | 修复发布错误，等待自动重试或仅提前 `next_retry_at` |
| outbox `SENT`，主队列 Ready 增长 | 恢复/扩容商品消费者，检查数据库慢事务 |
| outbox `SENT`，无消费记录，死信有消息 | 修复消费原因后原样重放 |
| outbox `SENT`，无消费记录，主队列/死信均无消息 | 核对 exchange binding、RabbitMQ 消息历史和日志，按事件 ID 做受控重放 |
| 有消费记录且状态正确 | 事件链路正常，不要补偿 |
| 有消费记录但状态/SKU 对账异常 | 重放通常无效，进入受控库存校准 |
| 取消先于创建且最终为 `RELEASED` version 2 | 预期行为，无需补偿 |
| 重复消息 | 由幂等消费吸收；观察即可 |
