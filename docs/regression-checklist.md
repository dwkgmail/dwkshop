# 业务回归清单

本文用于固化 DWK Shop 微服务模式下的业务回归步骤。执行人应逐项勾选，并在末尾记录版本、环境和异常；除特别说明外，命令均在仓库根目录执行。

## 1. 回归信息

- [ ] 分支或提交：`________________`
- [ ] 执行人：`________________`
- [ ] 执行时间：`________________`
- [ ] 环境：`本地 / 测试环境 / 其他：________________`
- [ ] 本次是否重建数据卷：`是 / 否`

## 2. 前置条件

- Java 21、Maven 3.9+、Node.js 20+ 和 Docker Desktop 可用。
- `8080`、`3306`、`6379`、`5672`、`15672`、`9200` 端口未被其他程序占用。
- 微服务网关与 legacy 单体不能同时启动，两者默认都占用 `8080`。
- 测试账号：用户 `13800000001 / user123`，管理员 `admin / admin123`。

如需完全干净的数据环境，先执行以下命令。该命令会删除本地业务数据，仅在确认数据可丢弃时使用：

```powershell
docker compose -f docker-compose.microservices.yml down -v
```

## 3. 自动化门禁

以下检查应全部通过，且不能只依赖手工回归替代：

- [ ] 后端单元测试、集成测试及数据库所有权检查通过。

  ```powershell
  mvn test
  ```

- [ ] 管理后台依赖安装和生产构建通过。

  ```powershell
  npm --prefix frontend-admin ci
  npm --prefix frontend-admin run build
  ```

- [ ] 用户端依赖安装和生产构建通过。

  ```powershell
  npm --prefix frontend-mobile ci
  npm --prefix frontend-mobile run build
  ```

重点确认 `DatabaseOwnershipTest` 未报错；服务间只能传递外部 ID，不得恢复跨服务 JPA 关系或数据库外键。

## 4. 启动与健康检查

- [ ] 构建并启动完整微服务环境。

  ```powershell
  docker compose -f docker-compose.microservices.yml up -d --build
  docker compose -f docker-compose.microservices.yml ps
  ```

- [ ] `db-migrator` 状态为成功退出（`Exited (0)`），其余服务均处于运行状态，且没有反复重启。
- [ ] 网关健康检查返回成功响应。

  ```powershell
  curl.exe -f http://localhost:8080/api/health
  ```

- [ ] 售后服务日志中没有 YAML 重复键、配置解析或启动失败错误。
- [ ] 订单服务日志中没有调度器、RabbitMQ 或数据库连接错误。

  ```powershell
  docker compose -f docker-compose.microservices.yml logs --tail=100 aftersale-service order-service
  ```

## 5. 准备登录态

在 PowerShell 中执行以下命令，后续请求直接复用 `$userHeaders` 和 `$adminHeaders`：

```powershell
$userLogin = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/login' `
  -ContentType 'application/json' -Body '{"mobile":"13800000001","password":"user123"}'
$adminLogin = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/admin/auth/login' `
  -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
$userHeaders = @{ Authorization = "Bearer $($userLogin.token)" }
$adminHeaders = @{ Authorization = "Bearer $($adminLogin.token)" }
```

- [ ] 用户登录成功并返回 `token`、用户 ID 和名称。
- [ ] 管理员登录成功并返回 `token`，使用该 Token 可访问 `/admin/**`。
- [ ] 不携带管理员 Token 访问 `/admin/products` 时被拒绝。

## 6. 商品与搜索

- [ ] `GET /api/products` 返回上架商品列表。
- [ ] `GET /api/categories` 返回分类列表。
- [ ] `GET /api/products/{id}` 返回商品详情及 SKU；库存为 `0` 或禁用的 SKU 显示为不可选。
- [ ] 搜索 `AirPods` 能返回匹配商品。

  ```powershell
  Invoke-RestMethod 'http://localhost:8080/api/products'
  Invoke-RestMethod 'http://localhost:8080/api/categories'
  Invoke-RestMethod 'http://localhost:8080/api/search/products?keyword=AirPods'
  ```

- [ ] 管理后台修改商品后，商品可正常写入 Elasticsearch，搜索接口无日期反序列化异常。
- [ ] 兼容旧索引文档：`updatedAt` 为仅日期格式（例如 `2026-06-22`）时，查询仍成功。
- [ ] 新写入索引的 `updatedAt` 为完整 ISO 本地日期时间（例如 `2026-06-22T13:45:12`）。

日期兼容至少应由以下专项测试覆盖：

```powershell
mvn -pl backend-product -am -Dtest=ElasticsearchProductSearchGatewayTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## 7. 购物车与订单主链路

- [ ] 将一个可售且有库存的 SKU 加入购物车，数量、勾选状态、角标和预估金额正确。
- [ ] 修改数量后金额同步变化；取消勾选后不计入结算；删除后条目消失。
- [ ] 不允许加入购物车、已下架、SKU 失效或库存不足的商品被正确拦截或标记。
- [ ] 立即购买或购物车结算的 `/api/orders/confirm` 返回地址、金额明细、优惠信息和 `settlementToken`。
- [ ] 使用确认结果中的 `settlementToken` 和 `payAmount` 调用 `/api/orders/create`，只创建一笔订单。
- [ ] 重复使用同一个 `settlementToken` 不会重复创建订单。
- [ ] 新订单出现在订单列表和详情中，金额、收货信息、商品快照一致。
- [ ] 模拟支付后订单从待支付流转为待发货。

可用以下方式执行“立即购买”主链路；先从商品详情中选取可售的 `$skuId`：

```powershell
$confirmBody = @{ sourceType='BUY_NOW'; skuId=$skuId; quantity=1; addressId=1; usePoints=$false } | ConvertTo-Json
$confirmed = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/orders/confirm' `
  -Headers $userHeaders -ContentType 'application/json' -Body $confirmBody
$createBody = @{ settlementToken=$confirmed.settlementToken; expectedPayAmount=$confirmed.payAmount } | ConvertTo-Json
$order = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/orders/create' `
  -Headers $userHeaders -ContentType 'application/json' -Body $createBody
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/orders/$($order.id)/pay" -Headers $userHeaders
```

## 8. 订单事件与库存专项

本节用于验证订单服务已启用定时调度，outbox 事件能够自动发布，而不是永久停留在 `PENDING`。

- [ ] 创建订单后，`dwkshop_order.order_outbox_event` 产生 `ORDER_CREATED` 事件。
- [ ] 等待数秒后事件状态变为 `SENT`，`published_at` 非空，`last_error` 为空。
- [ ] 商品库存状态已消费该事件，可用库存减少、锁定库存增加，且消费记录只产生一次。
- [ ] 取消一笔待支付订单后产生 `ORDER_CANCELLED` 事件，事件最终为 `SENT`，锁定库存被释放。
- [ ] 重复投递同一事件不会重复扣减或释放库存。
- [ ] RabbitMQ 暂时不可用时，outbox 保持可重试状态并增加 `retry_count`；RabbitMQ 恢复后最终发送成功。

可通过 MySQL 容器核验最近事件：

```powershell
docker exec dwkshop-mysql mysql -udwkshop -pdwkshop -e `
  "SELECT id,event_type,publish_status,retry_count,published_at,last_error FROM dwkshop_order.order_outbox_event ORDER BY id DESC LIMIT 10;"
docker exec dwkshop-mysql mysql -udwkshop -pdwkshop -e `
  "SELECT event_id,event_type,order_id,sku_id FROM dwkshop_product.inventory_consumed_event ORDER BY id DESC LIMIT 10;"
```

## 9. 售后退款链路

使用上一节已支付的 `$order.id` 创建售后：

```powershell
$aftersaleBody = @{ orderId=$order.id; reason='业务回归退款' } | ConvertTo-Json
$aftersale = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/aftersales' `
  -Headers $userHeaders -ContentType 'application/json' -Body $aftersaleBody
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/admin/aftersales/$($aftersale.id)/approve" `
  -Headers $adminHeaders
```

- [ ] 用户提交售后成功，状态为 `APPLYING`，订单号、退款金额和原因正确。
- [ ] 同一订单存在处理中或已退款售后时，重复申请被拒绝。
- [ ] 管理后台能查询到该售后。
- [ ] 管理员审核通过后，售后状态为 `REFUNDED`，订单退款状态同步更新。
- [ ] `dwkshop_aftersale.aftersale_outbox_event` 产生 `REFUND_APPROVED`，数秒后状态变为 `SENT`。
- [ ] 商品服务消费退款事件后释放对应锁定库存，且重复消费不重复释放。
- [ ] 另建一笔订单验证“拒绝售后”：状态变为 `REJECTED`，拒绝原因保存，不能再审核通过。

```powershell
docker exec dwkshop-mysql mysql -udwkshop -pdwkshop -e `
  "SELECT id,event_type,publish_status,retry_count,published_at,last_error FROM dwkshop_aftersale.aftersale_outbox_event ORDER BY id DESC LIMIT 10;"
```

## 10. 管理后台与用户端冒烟

- [ ] 用户端首页、分类、搜索、商品详情、购物车、确认订单、订单列表和订单详情均可打开，无控制台报错。
- [ ] 管理后台登录、工作台、商品列表、商品编辑、上下架、订单列表和订单详情均可打开。
- [ ] 管理后台商品修改能在用户端正确体现。
- [ ] 浏览器网络请求均通过网关 `http://localhost:8080`，无持续 `401`、`404` 或 `502`。

## 11. 结果与清理

- [ ] 回归期间未出现服务异常退出或持续重启。
- [ ] `order-service`、`aftersale-service`、`product-service` 日志中无未处理异常。
- [ ] 所有失败项已记录复现步骤、请求参数、响应、日志和关联缺陷编号。

```powershell
docker compose -f docker-compose.microservices.yml logs --tail=200 order-service aftersale-service product-service
docker compose -f docker-compose.microservices.yml down
```

回归结论：`通过 / 有条件通过 / 不通过`

备注或缺陷：

```text
____________________________________________________________
____________________________________________________________
```
