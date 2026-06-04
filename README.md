# DWK Shop MVP

DWK Shop 练习系统第一阶段工程骨架。

## 目录

- `backend`: Spring Boot 后端
- `frontend-mobile`: Vue 3 用户端
- `frontend-admin`: Vue 3 管理后台
- `docs`: 设计说明
- `docker-compose.yml`: MySQL 本地依赖

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 20+
- Docker Desktop

## 启动 MySQL

```bash
docker compose up -d mysql
```

默认连接信息：

- Host: `localhost`
- Port: `3306`
- Database: `dwkshop`
- Username: `dwkshop`
- Password: `dwkshop`

## 启动后端

```bash
cd backend
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/api/health
```

## 数据库迁移和初始化数据

后端使用 Flyway 管理数据库结构和初始化数据，迁移文件在：

- `backend/src/main/resources/db/migration/V1__create_mvp_tables.sql`
- `backend/src/main/resources/db/migration/V2__seed_mvp_data.sql`
- `backend/src/main/resources/db/migration/V3__add_product_notice.sql`

已创建的 MVP 表：

- `user`: 用户基础表
- `user_address`: 用户收货地址表
- `product`: 商品主表
- `product_sku`: 商品 SKU 表
- `product_category`: 商品分类表
- `product_notice`: 商品购买须知表
- `cart_item`: 购物车表
- `trade_order`: 订单主表
- `trade_order_item`: 订单明细表
- `trade_order_amount`: 订单金额明细表
- `coupon`: 优惠券表
- `coupon_user`: 用户优惠券表
- `user_point_account`: 用户积分账户表
- `user_point_flow`: 用户积分流水表

初始化测试数据：

- 测试用户：`13800000001`，昵称 `测试用户`
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

金额字段统一使用“分”为单位，例如 `169900` 表示 `1699.00` 元。

## 商品接口

用户端：

- `GET /api/products`: 首页/商品列表，可选 `categoryId`
- `GET /api/products/{id}`: 商品详情，下架商品历史入口可访问并返回 `offSale=true`
- `GET /api/categories`: 分类列表
- `GET /api/search/products?keyword=AirPods`: 搜索商品

管理后台：

- `GET /admin/products`: 后台商品列表
- `POST /admin/products`: 新增商品
- `PUT /admin/products/{id}`: 编辑商品
- `POST /admin/products/{id}/on-sale`: 上架商品
- `POST /admin/products/{id}/off-sale`: 下架商品

商品接口返回价格时同时包含原始金额和展示金额，例如 `salePrice=520`、`salePriceText=5.2`。SKU 库存为 0 或 SKU 禁用时，`selectable=false`。

## 购物车接口

当前尚未接入登录体系，购物车接口默认使用初始化测试用户 `userId=1`，也可以通过 query 参数传入 `userId`。

- `GET /api/cart/items`: 查询购物车
- `POST /api/cart/items`: 加入购物车，请求体示例 `{"skuId":1,"quantity":1}`
- `PUT /api/cart/items/{id}`: 修改数量，请求体示例 `{"quantity":2}`
- `DELETE /api/cart/items/{id}`: 删除购物车商品
- `PUT /api/cart/items/{id}/checked`: 修改单项勾选，请求体示例 `{"checked":true}`
- `PUT /api/cart/items/check-all`: 全选/取消全选，请求体示例 `{"checked":true}`

购物车响应包含：

- `badgeCount`: 购物车角标数量，按购物车商品数量汇总
- `estimatedAmount`: 购物车预估金额，单位为分
- `estimatedAmountText`: 去除末尾 0 的展示金额
- `items[].status`: `NORMAL`、`OFF_SALE`、`SKU_INVALID`、`STOCK_NOT_ENOUGH`、`NOT_ALLOW_CART`
- `items[].canCheck`: 是否允许勾选，失效或库存不足商品为 `false`

购物车金额只做预估，最终金额以后续确认订单接口为准。

## 订单接口

当前尚未接入登录体系，订单接口默认使用初始化测试用户 `userId=1`，也可以通过 query 参数传入 `userId`。

- `POST /api/orders/confirm`: 确认订单
- `POST /api/orders/create`: 提交订单
- `GET /api/orders`: 订单列表
- `GET /api/orders/{id}`: 订单详情
- `POST /api/orders/{id}/cancel`: 取消待支付订单

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
  "addressId": 1
}
```

提交订单示例：

```json
{
  "settlementToken": "SETTLE-xxx",
  "expectedPayAmount": 20900
}
```

确认订单会返回地址、商品、运费、优惠券、积分、金额明细、购买须知和 `settlementToken`。同一个 `settlementToken` 只能成功创建一个订单。普通运费当前为 `0`，冷链运费当前为 `1000` 分。所有金额仍以“分”为计算单位。

## 启动用户端

```bash
cd frontend-mobile
npm install
npm run dev
```

访问：`http://localhost:5173`

## 启动管理后台

```bash
cd frontend-admin
npm install
npm run dev
```

访问：`http://localhost:5174`

## 当前状态

当前只创建工程骨架和 MVP 基础数据模型，不包含业务功能。后端提供 `/api/health`，两个前端提供可访问首页。
