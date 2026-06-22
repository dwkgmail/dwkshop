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
- 确认订单、优惠券、积分抵扣和运费计算展示
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
- 支持积分抵扣商品：`智能运动手表`
- 不支持积分抵扣商品：`限价手机充电器`
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
- `POST /admin/aftersales/{id}/approve`：审核通过并完成退款
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

确认订单会返回地址、商品、运费、优惠券、积分抵扣、金额明细、购买须知和 `settlementToken`。同一个 `settlementToken` 只能成功创建一个订单。普通运费当前为 `0`，冷链运费当前为 `1000` 分。

### 管理后台商品接口

- `GET /admin/products`：后台商品列表
- `POST /admin/products`：新增商品
- `PUT /admin/products/{id}`：编辑商品
- `POST /admin/products/{id}/on-sale`：上架商品
- `POST /admin/products/{id}/off-sale`：下架商品

## 相关文档

- `docs/design-mvp.md`
- `docs/microservices-split.md`
- `docs/database-ownership.md`
- `电商系统设计稿_完整版.docx`
