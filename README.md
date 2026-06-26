# DWK Shop MVP

DWK Shop 是一个电商 MVP 练习项目，包含 Spring Boot 多模块微服务后端、Vue 3 移动端和 Vue 3 管理后台。当前版本已经打通商品浏览、购物车、确认订单、提交订单、订单查看，以及后台商品和订单管理的基础流程。

## 项目结构

```text
.
├── backend-*                        # 网关、迁移器、公共模块及各业务微服务
├── backend                          # legacy 单体后端（兼容模式）
├── frontend-mobile                  # Vue 3 用户端
├── frontend-admin                   # Vue 3 管理后台
├── docs                             # 设计、需求及微服务拆分文档
├── pom.xml                          # Maven 多模块聚合 POM
├── docker-compose.yml               # MySQL、Redis、RabbitMQ、Elasticsearch
└── docker-compose.microservices.yml # 完整微服务编排
```

## 技术栈

- 后端：Java 21、Spring Boot 3.3、Spring Cloud Gateway、Spring Data JPA、Flyway
- 前端：Vue 3、TypeScript、Vite
- 本地依赖：Docker Compose、MySQL 8.4、Redis 7.4、RabbitMQ 4.0、Elasticsearch 8.15

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 20+
- Docker Desktop

## 快速启动（推荐：微服务模式）

从仓库根目录构建并启动数据库迁移器、API 网关和全部业务服务：

```bash
docker compose -f docker-compose.microservices.yml up --build
```

首次启动需要构建所有后端镜像，耗时会比后续启动长。`db-migrator` 会等待 MySQL 就绪，执行完 Flyway 迁移后退出；其他服务随后启动。

启动完成后可通过网关检查服务：

```bash
curl http://localhost:8080/api/health
```

前端继续访问 `http://localhost:8080`，无需感知后端服务拆分。停止并清理容器：

```bash
docker compose -f docker-compose.microservices.yml down
```

### 服务端口

Docker Compose 默认只把网关和中间件端口暴露到宿主机；业务服务端口用于容器间调用。

| 组件 | 端口 | 说明 |
| --- | ---: | --- |
| API Gateway | `8080` | 前端及外部 API 的统一入口 |
| auth-service | `18081` | 登录、鉴权、健康检查 |
| product-service | `18082` | 商品、分类和搜索 |
| cart-service | `18083` | 购物车 |
| order-service | `18084` | 订单和结算 |
| aftersale-service | `18085` | 售后 |
| member-service | `18086` | 会员、地址和积分，内部服务 |
| marketing-service | `18087` | 优惠券，内部服务 |
| MySQL | `3306` | 业务数据及各服务独立 schema |
| Redis | `6379` | 订单结算等临时数据 |
| RabbitMQ | `5672` | 服务间事件；管理控制台为 `15672` |
| Elasticsearch | `9200` | 商品搜索；传输端口为 `9300` |

### 依赖中间件

| 中间件 | 使用方 | 本地默认信息 |
| --- | --- | --- |
| MySQL | 迁移器及所有持久化服务 | `root/root`；应用账号 `dwkshop/dwkshop` |
| Redis | order-service | `localhost:6379`，无密码 |
| RabbitMQ | product、order、aftersale | `dwkshop/dwkshop`，控制台 `http://localhost:15672` |
| Elasticsearch | product-service | `http://localhost:9200`，本地关闭安全认证 |

## 生产/准生产部署注意事项

仓库中的 Compose 配置和 `application.yml` 默认值仅用于本地开发，不能原样用于生产或准生产。部署前至少完成以下检查：

- 通过部署平台的 Secret、环境变量或外部配置中心注入密码和密钥，不要把真实凭据写入 Compose、镜像、启动脚本或仓库。
- 替换 MySQL、RabbitMQ、Redis、Elasticsearch、测试用户和后台管理员的全部默认密码；生产环境不要保留 `root/root`、`dwkshop/dwkshop`、`user123`、`admin123` 以及示例共享密钥。
- `DWKSHOP_AUTH_SECRET` 必须是足够长的随机值，并在所有签发或校验 Token 的服务间保持一致；`DWKSHOP_INTERNAL_SECRET` 也必须随机生成，并在网关和全部业务服务间保持一致。两个密钥不能相同。
- 只对外暴露网关或上层反向代理。MySQL、Redis、RabbitMQ、Elasticsearch、Actuator 和各业务服务端口应限制在内网或容器网络中，并在入口层配置 TLS、访问控制、超时和请求体大小限制。
- 上线前备份 MySQL 和 Elasticsearch 数据，先运行一次性 `db-migrator`，确认状态码为 `0` 后再滚动启动 runtime 服务；不要让 runtime 服务自行执行 Flyway。

### 环境变量清单

下表列出部署时常用且需要显式管理的变量。Spring Boot 标准变量可以直接覆盖相应的 `application.yml` 配置。

| 类别 | 环境变量 | 使用方/说明 |
| --- | --- | --- |
| MySQL 迁移 | `SPRING_DATASOURCE_URL`、`DWKSHOP_MIGRATOR_USERNAME`、`DWKSHOP_MIGRATOR_PASSWORD` | 仅 `db-migrator`；账号需要建库、建表和授权能力 |
| MySQL runtime | `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` | auth、product、cart、member、marketing、order、aftersale；每个服务指向自己的 schema |
| 鉴权 | `DWKSHOP_AUTH_SECRET` | 所有签发或校验 Token 的服务使用同一个高强度随机值 |
| 内部调用 | `DWKSHOP_INTERNAL_SECRET` | 网关及所有业务服务使用同一个高强度随机值 |
| RabbitMQ | `SPRING_RABBITMQ_HOST`、`SPRING_RABBITMQ_PORT`、`SPRING_RABBITMQ_USERNAME`、`SPRING_RABBITMQ_PASSWORD` | product、order、aftersale；账号需有目标 vhost、exchange 和 queue 权限 |
| Redis | `SPRING_DATA_REDIS_HOST`、`SPRING_DATA_REDIS_PORT`、`SPRING_DATA_REDIS_PASSWORD` | order-service；生产建议启用认证和 TLS/内网隔离 |
| Elasticsearch | `DWKSHOP_ES_ENABLED`、`DWKSHOP_ES_URIS`、`SPRING_ELASTICSEARCH_USERNAME`、`SPRING_ELASTICSEARCH_PASSWORD` | product-service；当前商品索引名为 `dwkshop_products` |
| 服务发现/地址 | `DWKSHOP_*_SERVICE_URI`、`DWKSHOP_*_SERVICE_BASE_URL` | 网关路由和服务间 HTTP 调用；完整名称见 `docker-compose.microservices.yml` |
| 监听端口 | `SERVER_PORT` | 各业务服务；通常无需对宿主机或公网暴露 |

### 数据库账号权限

MySQL `root` 或等价的管理账号只交给一次性 `db-migrator`，不要注入网关或任何 runtime 服务。`V9__split_service_schemas.sql` 中授予 `dwkshop` 用户 `ALL PRIVILEGES` 的语句仅服务于本地/开发 Compose 共享账号引导，不作为生产权限模型。生产环境建议为每个服务创建独立账号，仅授予其所属 schema 所需的 DML 权限；若暂时共用 runtime 账号，也必须移除建库、授权、用户管理和访问其他业务 schema 的权限。迁移账号和 runtime 账号应分别轮换、审计和保存。

### RabbitMQ 与 Elasticsearch

RabbitMQ 不要使用本地默认账号。建议为应用创建独立 vhost 和最小权限用户，限制 configure/write/read 范围；管理控制台 `15672` 不应暴露公网。服务端和客户端应同时配置用户名、密码，跨主机通信建议启用 TLS。

当前本地 Elasticsearch 设置了 `xpack.security.enabled=false`，该配置不得用于生产或准生产。部署时应启用 Elasticsearch 安全功能和 TLS，创建只允许 product-service 读写商品索引的账号，并限制 `9200`、`9300` 仅在受信网络可达。启用认证后，通过 `SPRING_ELASTICSEARCH_USERNAME` 和 `SPRING_ELASTICSEARCH_PASSWORD` 注入凭据。

### Maven mirror

CI 使用仓库内的 `.github/maven-settings.xml` 明确指向 Maven Central，并通过 `actions/setup-java` 缓存 `~/.m2/repository`，缓存 key 由所有 `pom.xml` 参与计算。这样根目录执行 `mvn test` 与 CI 使用同一套 Maven 解析入口。

如果本地或公司网络访问 Maven Central 不稳定，可复制 `docs/maven-settings.example.xml` 到个人 Maven 配置目录后替换为内网 Nexus/Artifactory 地址。不要提交真实地址、账号、Token 或个人 `settings.xml`。

Linux/macOS:

```bash
mkdir -p ~/.m2
cp docs/maven-settings.example.xml ~/.m2/settings.xml
export MAVEN_MIRROR_URL=https://nexus.example.com/repository/maven-public/
export MAVEN_MIRROR_USERNAME=your-username
export MAVEN_MIRROR_PASSWORD=your-token
mvn --settings ~/.m2/settings.xml test
```

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force $env:USERPROFILE\.m2
Copy-Item docs\maven-settings.example.xml $env:USERPROFILE\.m2\settings.xml
$env:MAVEN_MIRROR_URL = 'https://nexus.example.com/repository/maven-public/'
$env:MAVEN_MIRROR_USERNAME = 'your-username'
$env:MAVEN_MIRROR_PASSWORD = 'your-token'
mvn --settings $env:USERPROFILE\.m2\settings.xml test
```

离线/内网环境先在可联网机器预热依赖缓存，再把 `~/.m2/repository` 同步到内网构建机；内网构建机继续使用指向内网镜像的 `settings.xml`。确认依赖完整后，可用 `mvn --offline --settings ~/.m2/settings.xml test` 验证离线构建。私服地址、账号和 Token 应由开发机环境变量或 CI Secret 注入，不要写入项目 `pom.xml`。

### 重建 Elasticsearch 商品索引

product-service 在启动完成后会从 MySQL 全量写入现有商品。确认 MySQL 是权威数据源并做好备份后，可删除商品索引并重启服务：

```bash
curl -X DELETE "http://localhost:9200/dwkshop_products"
docker compose -f docker-compose.microservices.yml restart product-service
```

启用 Elasticsearch 认证或 TLS 时，请相应改用 HTTPS 并提供认证信息。重启后检查 product-service 日志，再通过 `/api/search/products?keyword=<关键词>` 验证搜索结果。删除索引到重建完成之间搜索可能暂时降级到数据库，不要在高峰期直接操作生产索引。

### 清理本地环境

仅停止并删除容器、保留中间件数据卷：

```bash
docker compose -f docker-compose.microservices.yml down --remove-orphans
```

彻底清空本地 MySQL、Redis、RabbitMQ 和 Elasticsearch 数据并重新初始化：

```bash
docker compose -f docker-compose.microservices.yml down -v --remove-orphans
docker compose -f docker-compose.microservices.yml up --build
```

`down -v` 会永久删除该 Compose 项目的本地数据卷，只能用于确认无需保留数据的开发环境，禁止作为生产清理命令。

## 单体兼容模式（legacy）

`backend` 目录仍保留可运行的单体应用，仅用于兼容、回归或拆分期间排查问题；日常开发和联调优先使用微服务模式。先启动全部中间件，再启动单体：

```bash
docker compose up -d mysql redis rabbitmq elasticsearch
cd backend
mvn spring-boot:run
```

单体同样监听 `8080`，不要与微服务网关同时启动。其默认数据库连接信息为：

| 配置 | 值 |
| --- | --- |
| Host | `localhost` |
| Port | `3306` |
| Database | `dwkshop` |
| Username | `dwkshop` |
| Password | `dwkshop` |

## 启动前端

### 用户端

```bash
cd frontend-mobile
npm install
npm run dev
```

访问地址：`http://localhost:5173`

用户端 Vite 会把 `/api` 代理到 `http://localhost:8080`。

### 管理后台

```bash
cd frontend-admin
npm install
npm run dev
```

访问地址：`http://localhost:5174`

管理后台 Vite 会把 `/api` 和 `/admin` 代理到 `http://localhost:8080`。后台已接入登录接口，默认表单值为：

- 账号：`admin`
- 密码：`admin123`

## 常用命令

后端测试应优先从仓库根目录执行，以便 Maven 一次解析并测试所有微服务模块：

```bash
mvn test
```

仅回归 legacy 单体时，可执行 `mvn -f backend/pom.xml test`。

用户端构建：

```bash
cd frontend-mobile
npm run build
```

管理后台构建：

```bash
cd frontend-admin
npm run build
```

## 常见问题

### `db-migrator` 正常退出是否表示启动失败？

不是。迁移器是一次性任务，成功执行迁移并以状态码 `0` 退出是预期行为。

### 为什么业务服务无法从宿主机通过 `18081` 至 `18087` 访问？

微服务编排只暴露统一网关 `8080`。业务端口用于 Compose 网络内部调用；本地调试单个服务时，可直接用 Maven 启动该模块，或临时在 Compose 中增加端口映射。

### 网关返回 `502 Bad Gateway` 怎么排查？

先运行 `docker compose -f docker-compose.microservices.yml ps`，确认对应业务服务正在运行；再用 `docker compose -f docker-compose.microservices.yml logs <service-name>` 查看服务是否因数据库迁移、端口占用或中间件连接失败退出。

### 修改了数据库迁移后为什么没有重新执行？

Flyway 只执行尚未登记的版本迁移。不要修改已执行的迁移文件；应在 `backend-migrator/src/main/resources/db/migration` 中新增更高版本脚本。仅在确认不需要保留本地数据时，才使用 `docker compose -f docker-compose.microservices.yml down -v` 删除数据卷后重建。

### `8080` 端口被占用怎么办？

确认没有同时运行 legacy 单体和微服务网关。两者默认都监听 `8080`，本地只能启动其中一种。

## 已实现功能

用户端：

- 首页商品推荐
- 商品分类筛选
- 商品搜索
- 商品详情和 SKU 选择
- 加入购物车、修改数量、删除、勾选和全选
- 立即购买和购物车结算
- 确认订单、优惠券、运费计算展示
- 提交订单、模拟支付、订单列表和订单详情
- 待支付订单取消

管理后台：

- 管理员登录和后台接口 Token 鉴权
- 工作台指标
- 商品列表、筛选、新增、编辑、上下架
- SKU 编辑
- 商品购买须知维护
- 订单列表、筛选和订单详情
- 用户、营销、售后、财务、权限等菜单占位

## 数据库

数据库结构和初始化数据由 `backend-migrator` 统一通过 Flyway 管理，迁移目录为：

- `backend-migrator/src/main/resources/db/migration`

微服务模式下，各业务服务不自行执行迁移。新增或调整数据库结构时，应在该目录中增加新的版本化迁移脚本；不要继续向 legacy 的 `backend/src/main/resources/db/migration` 添加迁移。

主要数据表：

- `user`：用户基础信息
- `user_address`：收货地址
- `product`：商品主表
- `product_sku`：商品 SKU
- `product_category`：商品分类
- `product_notice`：商品购买须知
- `cart_item`：购物车
- `trade_order`：订单主表
- `trade_order_item`：订单明细
- `trade_order_amount`：订单金额明细
- `coupon`：优惠券
- `coupon_user`：用户优惠券
- `user_point_account`：用户积分账户
- `user_point_flow`：用户积分流水
- `admin_user`：后台管理员账号

金额字段统一使用“分”为计算单位，例如 `169900` 表示 `1699.00` 元。接口响应通常同时返回原始金额和展示金额，例如 `salePrice=520`、`salePriceText=5.2`。

## 初始化测试数据

- 测试用户：`13800000001`，密码 `user123`，昵称 `测试用户`
- 后台管理员：`admin`，密码 `admin123`
- 默认地址：北京市朝阳区望京测试路 100 号
- 普通商品：`Apple AirPods Pro 第二代`
- 冷链商品：`澳洲冷链牛排套餐`
- 不允许加入购物车商品：`品牌定制服务卡`
- 不可单独购买商品：`配件加购保护壳`
- 下架商品：`历史下架蓝牙耳机`
- 支持积商品：`智能运动手表`
- 满减优惠券：`满1000减100优惠券`
- 用户积分账户：可用积分 `5000`

购物车和订单接口会优先使用 `Authorization: Bearer <token>` 中的登录用户；未携带 Token 时仍兼容初始化用户 `userId=1`，也可以通过 query 参数传入 `userId`。

## API 概览

### 健康检查

- `GET /api/health`

### 登录接口

- `POST /api/auth/login`：用户登录，请求体示例 `{"mobile":"13800000001","password":"user123"}`
- `POST /admin/auth/login`：后台管理员登录，请求体示例 `{"username":"admin","password":"admin123"}`

登录成功后返回 `token`、`tokenType`、`expiresIn`、`id`、`name` 和 `role`。需要鉴权的接口请在请求头中携带：

```text
Authorization: Bearer <token>
```

`/admin/**` 除 `/admin/auth/login` 外均要求管理员 Token。

### 商品接口

- `GET /api/products`：商品列表，可选 `categoryId`
- `GET /api/products/{id}`：商品详情，下架商品可通过历史入口访问并返回 `offSale=true`
- `GET /api/categories`：分类列表
- `GET /api/search/products?keyword=AirPods`：搜索商品

SKU 库存为 `0` 或 SKU 禁用时，接口返回 `selectable=false`。

### 购物车接口

- `GET /api/cart/items`：查询购物车
- `POST /api/cart/items`：加入购物车，请求体示例 `{"skuId":1,"quantity":1}`
- `PUT /api/cart/items/{id}`：修改数量，请求体示例 `{"quantity":2}`
- `DELETE /api/cart/items/{id}`：删除购物车商品
- `PUT /api/cart/items/{id}/checked`：修改单项勾选，请求体示例 `{"checked":true}`
- `PUT /api/cart/items/check-all`：全选或取消全选，请求体示例 `{"checked":true}`

购物车响应字段说明：

- `badgeCount`：购物车角标数量，按商品数量汇总
- `estimatedAmount`：购物车预估金额，单位为分
- `estimatedAmountText`：展示金额
- `items[].status`：`NORMAL`、`OFF_SALE`、`SKU_INVALID`、`STOCK_NOT_ENOUGH`、`NOT_ALLOW_CART`
- `items[].canCheck`：是否允许勾选

查询购物车会根据商品实时快照同步购物车项状态。商品下架、软删除、SKU 禁用或库存不足时，购物车项会标记为失效并自动取消勾选。

购物车金额只做预估，最终金额以确认订单接口为准。

### 订单接口

- `POST /api/orders/confirm`：确认订单
- `POST /api/orders/create`：提交订单
- `GET /api/orders`：订单列表
- `GET /api/orders/{id}`：订单详情
- `POST /api/orders/{id}/cancel`：取消待支付订单
- `POST /api/orders/{id}/pay`：模拟支付并将待支付订单流转为待发货
- `POST /api/aftersales`：提交退款售后申请
- `GET /api/aftersales`：用户售后列表
- `GET /admin/aftersales`：后台售后列表
- `POST /admin/aftersales/{id}/approve`：审核通过并进入退款中
- `POST /admin/aftersales/{id}/refund/complete`：支付渠道确认退款成功
- `POST /admin/aftersales/{id}/refund/fail`：记录支付渠道退款失败
- `POST /admin/aftersales/{id}/refund/retry`：重试失败退款
- `POST /admin/aftersales/{id}/reject`：拒绝退款申请

购物车结算确认示例：

```json
{
  "sourceType": "CART",
  "cartItemIds": [1, 2],
  "addressId": 1,
  "usePoints": true
}
```

立即购买确认示例：

```json
{
  "sourceType": "BUY_NOW",
  "skuId": 2,
  "quantity": 1,
  "addressId": 1,
  "usePoints": true
}
```

提交订单示例：

```json
{
  "settlementToken": "SETTLE-xxx",
  "expectedPayAmount": 20900
}
```

确认订单会返回地址、商品、运费、优惠券、金额明细、购买须知和 `settlementToken`。同一个 `settlementToken` 只能成功创建一个订单。普通运费当前为 `0`，冷链运费当前为 `1000` 分。

上下架与订单规则：

- 确认订单前校验商品必须 `ON_SALE`，SKU 必须 `ENABLED`，库存必须充足。
- 订单创建后锁定商品快照和金额。待支付订单允许继续支付，不再受商品后续下架影响。
- 已支付订单不受商品下架或后台软删除影响。
- 售后读取订单商品快照，不依赖商品当前上下架状态。

### 管理后台商品接口

- `GET /admin/products`：后台商品列表
- `POST /admin/products`：新增商品
- `PUT /admin/products/{id}`：编辑商品
- `POST /admin/products/{id}/on-sale`：上架商品
- `POST /admin/products/{id}/off-sale`：下架商品
- `DELETE /admin/products/{id}`：软删除商品，置为下架并隐藏前台展示，不物理删除历史数据

## 相关文档

- `docs/design-mvp.md`
- `docs/microservices-split.md`
- `docs/database-ownership.md`
- `电商系统设计稿_完整版.docx`
