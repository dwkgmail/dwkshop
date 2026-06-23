package com.dwkshop.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ApiClient api;
    private LinearLayout root;
    private LinearLayout content;
    private ProgressBar progress;
    private String view = "home";
    private JSONArray products = new JSONArray();
    private JSONArray categories = new JSONArray();
    private JSONArray orders = new JSONArray();
    private JSONObject productDetail;
    private JSONObject cart;
    private JSONObject confirmData;
    private JSONObject currentOrder;
    private int selectedSkuId = 0;
    private int selectedQuantity = 1;
    private String settlementToken;
    private long expectedPayAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new ApiClient(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        FrameLayout frame = new FrameLayout(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
        frame.addView(scrollView);
        frame.addView(progress, progressParams);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(tabbar(), new LinearLayout.LayoutParams(-1, dp(62)));
        setContentView(root);
        loadHome();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadHome() {
        view = "home";
        run(() -> {
            products = api.getArray("/api/products");
            if (api.isLoggedIn()) tryLoadCart();
        }, this::renderHome);
    }

    private void loadCategories(Integer categoryId) {
        view = "category";
        run(() -> {
            categories = api.getArray("/api/categories");
            products = api.getArray(categoryId == null ? "/api/products" : "/api/products?categoryId=" + categoryId);
        }, () -> renderCategory(categoryId));
    }

    private void search(String keyword) {
        view = "search";
        run(() -> products = api.getArray("/api/search/products?keyword=" + enc(keyword)), () -> renderSearch(keyword));
    }

    private void loadProduct(int id) {
        view = "detail";
        run(() -> {
            productDetail = api.getObject("/api/products/" + id);
            JSONArray skus = productDetail.optJSONArray("skus");
            selectedSkuId = skus != null && skus.length() > 0 ? skus.getJSONObject(0).optInt("id") : 0;
            selectedQuantity = 1;
        }, this::renderDetail);
    }

    private void loadCart() {
        if (!requireLogin("cart")) return;
        view = "cart";
        run(() -> cart = api.getObject("/api/cart/items"), this::renderCart);
    }

    private void loadOrders() {
        if (!requireLogin("orders")) return;
        view = "orders";
        run(() -> orders = api.getArray("/api/orders"), this::renderOrders);
    }

    private void loadOrder(int id) {
        if (!requireLogin("orders")) return;
        view = "order";
        run(() -> currentOrder = api.getObject("/api/orders/" + id), this::renderOrderDetail);
    }

    private void renderHome() {
        clear("DWK Shop");
        TextView hero = title("精选好物");
        hero.setTextSize(26);
        content.addView(hero);
        EditText search = input("搜索商品", false);
        Button searchButton = primary("搜索");
        searchButton.setOnClickListener(v -> {
            String keyword = search.getText().toString().trim();
            if (!keyword.isEmpty()) search(keyword);
        });
        content.addView(search);
        content.addView(searchButton);

        LinearLayout shortcuts = row();
        shortcuts.addView(smallButton("分类", v -> loadCategories(null)), weight());
        shortcuts.addView(smallButton("购物车", v -> loadCart()), weight());
        shortcuts.addView(smallButton("订单", v -> loadOrders()), weight());
        content.addView(shortcuts);

        content.addView(section("推荐商品"));
        renderProductList(products);
    }

    private void renderCategory(Integer activeCategoryId) {
        clear("商品分类");
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout row = row();
        row.addView(smallButton("全部", v -> loadCategories(null)));
        forEach(categories, item -> row.addView(smallButton(item.optString("name"), v -> loadCategories(item.optInt("id")))));
        scroll.addView(row);
        content.addView(scroll);
        renderProductList(products);
    }

    private void renderSearch(String keyword) {
        clear("搜索");
        EditText search = input("输入商品关键词", false);
        search.setText(keyword);
        Button searchButton = primary("搜索");
        searchButton.setOnClickListener(v -> {
            String next = search.getText().toString().trim();
            if (!next.isEmpty()) search(next);
        });
        content.addView(search);
        content.addView(searchButton);
        renderProductList(products);
    }

    private void renderDetail() {
        clear("商品详情");
        content.addView(title(productDetail.optString("name")));
        content.addView(body(productDetail.optString("subtitle")));
        content.addView(price("¥" + productDetail.optString("minSalePriceText")));
        JSONArray skus = productDetail.optJSONArray("skus");
        content.addView(section("选择规格"));
        if (skus != null) {
            forEach(skus, sku -> {
                Button button = smallButton(sku.optString("skuName") + "  库存 " + sku.optInt("stock"), v -> {
                    selectedSkuId = sku.optInt("id");
                    Toast.makeText(this, "已选择 " + sku.optString("skuName"), Toast.LENGTH_SHORT).show();
                });
                button.setEnabled(sku.optBoolean("selectable", true));
                content.addView(button);
            });
        }
        LinearLayout qty = row();
        qty.addView(body("数量"), weight());
        qty.addView(smallButton("-", v -> {
            selectedQuantity = Math.max(1, selectedQuantity - 1);
            renderDetail();
        }));
        qty.addView(body(String.valueOf(selectedQuantity)));
        qty.addView(smallButton("+", v -> {
            selectedQuantity++;
            renderDetail();
        }));
        content.addView(qty);

        Button add = primary("加入购物车");
        add.setOnClickListener(v -> {
            if (!requireLogin("detail")) return;
            run(() -> {
                cart = api.post("/api/cart/items", new JSONObject()
                        .put("skuId", selectedSkuId)
                        .put("quantity", selectedQuantity));
            }, () -> Toast.makeText(this, "已加入购物车", Toast.LENGTH_SHORT).show());
        });
        Button buy = primary("立即购买");
        buy.setOnClickListener(v -> confirmBuyNow());
        content.addView(add);
        content.addView(buy);
    }

    private void renderCart() {
        clear("购物车");
        JSONArray items = cart.optJSONArray("items");
        if (items == null || items.length() == 0) {
            content.addView(body("购物车还是空的"));
            return;
        }
        forEach(items, item -> {
            LinearLayout card = card();
            CheckBox checked = new CheckBox(this);
            checked.setText(item.optString("productName") + "\n" + item.optString("skuName") + " x " + item.optInt("quantity"));
            checked.setChecked(item.optBoolean("checked"));
            checked.setEnabled(item.optBoolean("canCheck", true));
            checked.setOnClickListener(v -> updateChecked(item.optInt("id"), checked.isChecked()));
            card.addView(checked);
            card.addView(price("¥" + item.optString("estimatedAmountText")));
            LinearLayout actions = row();
            actions.addView(smallButton("-", v -> updateQuantity(item.optInt("id"), Math.max(1, item.optInt("quantity") - 1))), weight());
            actions.addView(smallButton("+", v -> updateQuantity(item.optInt("id"), item.optInt("quantity") + 1)), weight());
            actions.addView(smallButton("删除", v -> deleteCartItem(item.optInt("id"))), weight());
            card.addView(actions);
            content.addView(card);
        });
        content.addView(price("合计 ¥" + cart.optString("estimatedAmountText")));
        Button checkout = primary("去结算");
        checkout.setOnClickListener(v -> confirmCart());
        content.addView(checkout);
    }

    private void renderConfirm() {
        clear("确认订单");
        JSONObject address = confirmData.optJSONObject("address");
        if (address != null) content.addView(body(address.optString("receiverName") + " " + address.optString("receiverMobile") + "\n"
                + address.optString("province") + address.optString("city") + address.optString("district") + address.optString("detailAddress")));
        JSONArray items = confirmData.optJSONArray("items");
        if (items != null) forEach(items, item -> content.addView(body(item.optString("productName") + "\n" + item.optString("skuName") + " x " + item.optInt("quantity") + "  ¥" + item.optString("totalAmountText"))));
        JSONObject amount = confirmData.optJSONObject("amount");
        if (amount != null) {
            expectedPayAmount = amount.optLong("payAmount");
            content.addView(price("应付 ¥" + amount.optString("payAmountText")));
        }
        settlementToken = confirmData.optString("settlementToken");
        Button submit = primary("提交订单");
        submit.setOnClickListener(v -> run(() -> currentOrder = api.post("/api/orders/create", new JSONObject()
                        .put("settlementToken", settlementToken)
                        .put("expectedPayAmount", expectedPayAmount)),
                this::renderPayment));
        content.addView(submit);
    }

    private void renderPayment() {
        view = "payment";
        clear("支付订单");
        content.addView(title(currentOrder.optString("orderNo")));
        content.addView(price("¥" + currentOrder.optString("payAmountText")));
        Button pay = primary(currentOrder.optString("orderStatus").equals("WAIT_PAY") ? "立即支付" : "已支付");
        pay.setEnabled(currentOrder.optString("orderStatus").equals("WAIT_PAY"));
        pay.setOnClickListener(v -> run(() -> currentOrder = api.post("/api/orders/" + currentOrder.optInt("id") + "/pay", new JSONObject()),
                () -> {
                    Toast.makeText(this, "支付成功", Toast.LENGTH_SHORT).show();
                    renderPayment();
                }));
        content.addView(pay);
        content.addView(smallButton("查看订单详情", v -> loadOrder(currentOrder.optInt("id"))));
    }

    private void renderOrders() {
        clear("我的订单");
        if (orders.length() == 0) content.addView(body("暂无订单"));
        forEach(orders, order -> {
            LinearLayout card = card();
            card.addView(title(order.optString("orderNo")));
            card.addView(body(statusText(order.optString("orderStatus"))));
            card.addView(price("¥" + order.optString("payAmountText")));
            card.setOnClickListener(v -> loadOrder(order.optInt("id")));
            content.addView(card);
        });
    }

    private void renderOrderDetail() {
        clear("订单详情");
        content.addView(title(currentOrder.optString("orderNo")));
        content.addView(body("订单状态：" + statusText(currentOrder.optString("orderStatus")) + "\n售后状态：" + statusText(currentOrder.optString("aftersaleStatus"))));
        content.addView(body(currentOrder.optString("receiverName") + " " + currentOrder.optString("receiverMobile") + "\n" + currentOrder.optString("receiverAddress")));
        JSONArray items = currentOrder.optJSONArray("items");
        if (items != null) forEach(items, item -> content.addView(body(item.optString("productName") + "\n" + item.optString("skuName") + " x " + item.optInt("quantity") + "  ¥" + item.optString("payAmountText"))));
        content.addView(price("实付 ¥" + currentOrder.optString("payAmountText")));
        if ("WAIT_PAY".equals(currentOrder.optString("orderStatus"))) {
            content.addView(primary("去支付", v -> renderPayment()));
            content.addView(smallButton("取消订单", v -> run(() -> currentOrder = api.post("/api/orders/" + currentOrder.optInt("id") + "/cancel", new JSONObject()), this::renderOrderDetail)));
        }
        if ("PAID".equals(currentOrder.optString("payStatus")) && "NONE".equals(currentOrder.optString("aftersaleStatus"))) {
            content.addView(smallButton("申请退款", v -> askRefundReason()));
        }
    }

    private void renderMine() {
        clear("我的");
        content.addView(title(api.isLoggedIn() ? api.userName() : "未登录"));
        if (!api.isLoggedIn()) {
            content.addView(primary("登录 / 注册", v -> renderLogin("mine")));
            return;
        }
        content.addView(smallButton("我的订单", v -> loadOrders()));
        content.addView(smallButton("购物车", v -> loadCart()));
        content.addView(smallButton("退出登录", v -> {
            run(() -> {
                try {
                    api.post("/api/auth/logout", new JSONObject());
                } finally {
                    api.clearSession();
                }
            }, this::renderMine);
        }));
    }

    private void renderLogin(String target) {
        view = "login";
        clear("用户登录");
        EditText mobile = input("手机号", false);
        EditText password = input("密码", true);
        mobile.setText("13800000001");
        password.setText("user123");
        content.addView(mobile);
        content.addView(password);
        content.addView(primary("登录", v -> run(() -> {
            JSONObject result = api.post("/api/auth/login", new JSONObject()
                    .put("mobile", mobile.getText().toString().trim())
                    .put("password", password.getText().toString().trim()));
            api.saveLogin(result);
        }, () -> afterLogin(target))));

        EditText nickname = input("注册昵称", false);
        content.addView(section("没有账号时可直接注册"));
        content.addView(nickname);
        content.addView(smallButton("注册并登录", v -> run(() -> {
            JSONObject result = api.post("/api/auth/register", new JSONObject()
                    .put("mobile", mobile.getText().toString().trim())
                    .put("password", password.getText().toString().trim())
                    .put("nickname", nickname.getText().toString().trim()));
            api.saveLogin(result);
        }, () -> afterLogin(target))));
    }

    private void afterLogin(String target) {
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
        if ("cart".equals(target)) loadCart();
        else if ("orders".equals(target)) loadOrders();
        else if ("detail".equals(target)) renderDetail();
        else renderMine();
    }

    private void confirmBuyNow() {
        if (!requireLogin("detail")) return;
        view = "confirm";
        run(() -> confirmData = api.post("/api/orders/confirm", new JSONObject()
                        .put("sourceType", "BUY_NOW")
                        .put("skuId", selectedSkuId)
                        .put("quantity", selectedQuantity)
                        .put("usePoints", true)),
                this::renderConfirm);
    }

    private void confirmCart() {
        JSONArray ids = new JSONArray();
        JSONArray items = cart.optJSONArray("items");
        if (items != null) forEach(items, item -> {
            if (item.optBoolean("checked") && item.optBoolean("canCheck", true)) ids.put(item.optInt("id"));
        });
        if (ids.length() == 0) {
            Toast.makeText(this, "请选择要结算的商品", Toast.LENGTH_SHORT).show();
            return;
        }
        view = "confirm";
        run(() -> confirmData = api.post("/api/orders/confirm", new JSONObject()
                        .put("sourceType", "CART")
                        .put("cartItemIds", ids)
                        .put("usePoints", true)),
                this::renderConfirm);
    }

    private void updateQuantity(int id, int quantity) {
        run(() -> cart = api.put("/api/cart/items/" + id, new JSONObject().put("quantity", quantity)), this::renderCart);
    }

    private void updateChecked(int id, boolean checked) {
        run(() -> cart = api.put("/api/cart/items/" + id + "/checked", new JSONObject().put("checked", checked)), this::renderCart);
    }

    private void deleteCartItem(int id) {
        run(() -> cart = api.delete("/api/cart/items/" + id), this::renderCart);
    }

    private void askRefundReason() {
        EditText input = input("退款原因", false);
        input.setText("我想申请退款");
        new AlertDialog.Builder(this)
                .setTitle("申请退款")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("提交", (dialog, which) -> run(() -> {
                    api.post("/api/aftersales", new JSONObject()
                            .put("orderId", currentOrder.optInt("id"))
                            .put("reason", input.getText().toString().trim()));
                    currentOrder = api.getObject("/api/orders/" + currentOrder.optInt("id"));
                }, this::renderOrderDetail))
                .show();
    }

    private boolean requireLogin(String target) {
        if (api.isLoggedIn()) return true;
        renderLogin(target);
        return false;
    }

    private void tryLoadCart() {
        try {
            cart = api.getObject("/api/cart/items");
        } catch (Exception ignored) {
            cart = null;
        }
    }

    private void run(Task task, Runnable success) {
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                task.run();
                main.post(() -> {
                    progress.setVisibility(View.GONE);
                    success.run();
                });
            } catch (Exception e) {
                main.post(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    if (!api.isLoggedIn() && !"login".equals(view)) renderLogin(view);
                });
            }
        });
    }

    private void clear(String header) {
        content.removeAllViews();
        LinearLayout top = row();
        if (!"home".equals(view)) top.addView(smallButton("<", v -> loadHome()));
        TextView title = title(header);
        top.addView(title, weight());
        content.addView(top);
    }

    private void renderProductList(JSONArray list) {
        if (list.length() == 0) content.addView(body("暂无商品"));
        forEach(list, product -> {
            LinearLayout card = card();
            card.addView(title(product.optString("name")));
            card.addView(body(product.optString("subtitle")));
            card.addView(body(product.optString("deliveryType") + "  已售 " + product.optInt("displayedSales")));
            card.addView(price("¥" + product.optString("minSalePriceText")));
            card.setOnClickListener(v -> loadProduct(product.optInt("id")));
            content.addView(card);
        });
    }

    private LinearLayout tabbar() {
        LinearLayout bar = row();
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bar.addView(tab("首页", v -> loadHome()), weight());
        bar.addView(tab("分类", v -> loadCategories(null)), weight());
        bar.addView(tab("购物车", v -> loadCart()), weight());
        bar.addView(tab("订单", v -> loadOrders()), weight());
        bar.addView(tab("我的", v -> renderMine()), weight());
        return bar;
    }

    private Button tab(String text, View.OnClickListener listener) {
        Button button = smallButton(text, listener);
        button.setAllCaps(false);
        return button;
    }

    private Button primary(String text) {
        return primary(text, null);
    }

    private Button primary(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.rgb(242, 107, 44));
        button.setOnClickListener(listener);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        return button;
    }

    private Button smallButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(32, 33, 36));
        view.setTextSize(20);
        view.setTypeface(null, 1);
        view.setPadding(dp(12), dp(10), dp(12), dp(6));
        return view;
    }

    private TextView section(String text) {
        TextView view = body(text);
        view.setTextSize(16);
        view.setTypeface(null, 1);
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text == null ? "" : text);
        view.setTextColor(Color.rgb(72, 76, 82));
        view.setTextSize(14);
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        return view;
    }

    private TextView price(String text) {
        TextView view = body(text);
        view.setTextColor(Color.rgb(242, 107, 44));
        view.setTextSize(18);
        view.setTypeface(null, 1);
        return view;
    }

    private EditText input(String hint, boolean password) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        edit.setPadding(dp(12), dp(8), dp(12), dp(8));
        return edit;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(10), dp(6), dp(10), dp(6));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String enc(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private String statusText(String status) {
        if ("WAIT_PAY".equals(status)) return "待支付";
        if ("CANCELED".equals(status)) return "已取消";
        if ("WAIT_SHIP".equals(status)) return "待发货";
        if ("WAIT_RECEIVE".equals(status)) return "待收货";
        if ("FINISHED".equals(status)) return "已完成";
        if ("REFUNDED".equals(status)) return "已退款";
        if ("APPLYING".equals(status)) return "退款处理中";
        if ("REJECTED".equals(status)) return "退款已拒绝";
        if ("NONE".equals(status)) return "无";
        return status;
    }

    private void forEach(JSONArray array, JsonConsumer consumer) {
        for (int i = 0; i < array.length(); i++) {
            consumer.accept(array.optJSONObject(i));
        }
    }

    private interface Task {
        void run() throws Exception;
    }

    private interface JsonConsumer {
        void accept(JSONObject object);
    }
}
