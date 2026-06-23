# DWK Shop Native Android

原生 Android 用户端，基于现有 `/api/**` 接口实现移动端 MVP 流程。

## 技术选择

- Java + Android 原生 View
- `HttpURLConnection` 调接口
- `SharedPreferences` 保存用户 token 和 refresh token
- `org.json` 解析接口响应
- 默认 API 地址：`http://10.0.2.2:8080`

## 已实现功能

- 登录、注册、退出登录
- 商品首页、分类、搜索
- 商品详情、SKU 选择、数量选择
- 加入购物车、修改数量、勾选、删除
- 购物车结算、立即购买
- 确认订单、提交订单、模拟支付
- 订单列表、订单详情、取消订单、申请退款
- 401 自动刷新 token，刷新失败后回到登录

## 本地运行

1. 先启动后端网关，确保 `http://localhost:8080/api/health` 可访问。
2. 用 Android Studio 打开 `android-native` 目录。
3. 运行 `app` 到 Android 模拟器。

模拟器访问宿主机使用 `10.0.2.2`，所以默认配置可以直接连本机后端。

如果要在真机调试，把 `app/build.gradle` 里的 `API_BASE_URL` 改成电脑局域网 IP，例如：

```gradle
buildConfigField "String", "API_BASE_URL", "\"http://192.168.1.20:8080\""
```

手机和电脑需要在同一个网络中，并允许电脑防火墙放行 8080 端口。
