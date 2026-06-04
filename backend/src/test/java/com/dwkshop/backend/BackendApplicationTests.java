package com.dwkshop.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BackendApplicationTests {

    private final JdbcTemplate jdbcTemplate;
    private final MockMvc mockMvc;

    @Autowired
    BackendApplicationTests(JdbcTemplate jdbcTemplate, MockMvc mockMvc) {
        this.jdbcTemplate = jdbcTemplate;
        this.mockMvc = mockMvc;
    }

    @Test
    void contextLoads() {
    }

    @Test
    void flywaySeedsMvpData() {
        Integer userCount = jdbcTemplate.queryForObject("select count(*) from `user`", Integer.class);
        Integer productCount = jdbcTemplate.queryForObject("select count(*) from product", Integer.class);
        Integer couponCount = jdbcTemplate.queryForObject("select count(*) from coupon", Integer.class);
        Integer pointAccountCount = jdbcTemplate.queryForObject("select count(*) from user_point_account", Integer.class);

        assertThat(userCount).isEqualTo(1);
        assertThat(productCount).isGreaterThanOrEqualTo(7);
        assertThat(couponCount).isEqualTo(1);
        assertThat(pointAccountCount).isEqualTo(1);
    }

    @Test
    void productListHidesOffSaleProducts() throws Exception {
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].saleStatus", everyItem(org.hamcrest.Matchers.is("ON_SALE"))))
            .andExpect(jsonPath("$[*].id", not(hasItem(5))));
    }

    @Test
    void productDetailShowsOffSaleMessageForHistoricalAccess() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 5))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saleStatus").value("OFF_SALE"))
            .andExpect(jsonPath("$.offSale").value(true))
            .andExpect(jsonPath("$.offSaleMessage").value("商品已下架"));
    }

    @Test
    void productDetailReturnsCoreProductRules() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowCart").value(true))
            .andExpect(jsonPath("$.allowSingleBuy").value(true))
            .andExpect(jsonPath("$.pointDeductEnabled").value(false))
            .andExpect(jsonPath("$.noticeTitle").value("用户购买须知"))
            .andExpect(jsonPath("$.skus[0].salePrice").value(169900))
            .andExpect(jsonPath("$.skus[0].salePriceText").value("1699"))
            .andExpect(jsonPath("$.skus[0].selectable").value(true));

        mockMvc.perform(get("/api/products/{id}", 3))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowCart").value(false));

        mockMvc.perform(get("/api/products/{id}", 4))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowSingleBuy").value(false));

        mockMvc.perform(get("/api/products/{id}", 6))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pointDeductEnabled").value(true));
    }

    @Test
    void searchProductsOnlyReturnsOnSaleMatches() throws Exception {
        mockMvc.perform(get("/api/search/products").param("keyword", "下架"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/search/products").param("keyword", "AirPods"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Apple AirPods Pro 第二代"));
    }

    @Test
    void adminCanCreateProductWithZeroSalesAndUnselectableZeroStockSku() throws Exception {
        String payload = """
            {
              "categoryId": 1,
              "name": "零库存测试商品",
              "subtitle": "用于验证库存为0时不可选",
              "mainImageUrl": "/images/products/zero-stock.png",
              "allowCart": true,
              "allowSingleBuy": true,
              "pointDeductEnabled": true,
              "noticeTitle": "用户购买须知",
              "noticeContent": "零库存测试须知",
              "skus": [
                {
                  "skuName": "默认规格",
                  "specJson": "{\\"规格\\":\\"默认\\"}",
                  "salePrice": 520,
                  "linePrice": 600,
                  "stock": 0
                }
              ]
            }
            """;

        mockMvc.perform(post("/admin/products").contentType("application/json").content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayedSales").value(0))
            .andExpect(jsonPath("$.pointDeductEnabled").value(true))
            .andExpect(jsonPath("$.noticeContent").value("零库存测试须知"))
            .andExpect(jsonPath("$.skus[0].salePrice").value(520))
            .andExpect(jsonPath("$.skus[0].salePriceText").value("5.2"))
            .andExpect(jsonPath("$.skus[0].selectable").value(false));
    }

    @Test
    void cartAddsSameSkuByMergingQuantityAndReturnsBadgeCount() throws Exception {
        clearCart();

        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("""
            {"skuId":1,"quantity":1}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.badgeCount").value(1))
            .andExpect(jsonPath("$.estimatedAmount").value(169900))
            .andExpect(jsonPath("$.estimatedAmountText").value("1699"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].quantity").value(1))
            .andExpect(jsonPath("$.items[0].status").value("NORMAL"));

        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("""
            {"skuId":1,"quantity":2}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.badgeCount").value(3))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void cartRejectsInvalidAddRequests() throws Exception {
        clearCart();

        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("""
            {"skuId":5,"quantity":1}
            """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("""
            {"skuId":3,"quantity":1}
            """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("""
            {"skuId":1,"quantity":9999}
            """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void cartMarksOffSaleAndStockNotEnoughItemsAsInvalid() throws Exception {
        clearCart();
        jdbcTemplate.update("""
            insert into cart_item (user_id, product_id, sku_id, quantity, checked_flag, item_status)
            values (1, 5, 5, 1, true, 'NORMAL')
            """);
        jdbcTemplate.update("""
            insert into cart_item (user_id, product_id, sku_id, quantity, checked_flag, item_status)
            values (1, 1, 1, 999, true, 'NORMAL')
            """);

        mockMvc.perform(get("/api/cart/items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.badgeCount").value(1000))
            .andExpect(jsonPath("$.estimatedAmount").value(0))
            .andExpect(jsonPath("$.items[*].status", hasItem("OFF_SALE")))
            .andExpect(jsonPath("$.items[*].status", hasItem("STOCK_NOT_ENOUGH")))
            .andExpect(jsonPath("$.items[*].canCheck", everyItem(org.hamcrest.Matchers.is(false))));
    }

    @Test
    void cartCanUpdateCheckAllAndDeleteItems() throws Exception {
        clearCart();
        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("""
            {"skuId":1,"quantity":1}
            """))
            .andExpect(status().isOk());
        Long itemId = jdbcTemplate.queryForObject("select id from cart_item where user_id = 1 and sku_id = 1", Long.class);

        mockMvc.perform(put("/api/cart/items/{id}", itemId).contentType("application/json").content("""
            {"quantity":2}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].quantity").value(2));

        mockMvc.perform(put("/api/cart/items/{id}/checked", itemId).contentType("application/json").content("""
            {"checked":false}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estimatedAmount").value(0));

        mockMvc.perform(put("/api/cart/items/check-all").contentType("application/json").content("""
            {"checked":true}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estimatedAmount").value(339800));

        mockMvc.perform(delete("/api/cart/items/{id}", itemId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.badgeCount").value(0))
            .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void cartUpdateQuantityRejectsItemsThatAreNoLongerAddable() throws Exception {
        clearCart();
        jdbcTemplate.update("""
            insert into cart_item (user_id, product_id, sku_id, quantity, checked_flag, item_status)
            values (1, 5, 5, 1, true, 'NORMAL')
            """);
        Long itemId = jdbcTemplate.queryForObject("select id from cart_item where user_id = 1 and sku_id = 5", Long.class);

        mockMvc.perform(put("/api/cart/items/{id}", itemId).contentType("application/json").content("""
            {"quantity":2}
            """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void confirmOrderReturnsFullDataWithCouponPointsFreightAndToken() throws Exception {
        clearCart();
        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("""
            {"skuId":1,"quantity":1}
            """))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("""
            {"skuId":6,"quantity":1}
            """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/confirm").contentType("application/json").content("""
            {"sourceType":"CART","usePoints":true}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.settlementToken").isNotEmpty())
            .andExpect(jsonPath("$.address.id").value(1))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.selectedCoupon.discountAmount").value(10000))
            .andExpect(jsonPath("$.pointDeduction.visible").value(true))
            .andExpect(jsonPath("$.pointDeduction.deductionAmount").value(50))
            .andExpect(jsonPath("$.amount.productAmount").value(229800))
            .andExpect(jsonPath("$.amount.couponDiscountAmount").value(10000))
            .andExpect(jsonPath("$.amount.pointDiscountAmount").value(50))
            .andExpect(jsonPath("$.amount.payAmount").value(219750));

        mockMvc.perform(post("/api/orders/confirm").contentType("application/json").content("""
            {"sourceType":"BUY_NOW","skuId":2,"quantity":1}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.freightAmount").value(1000))
            .andExpect(jsonPath("$.amount.payAmount").value(20900));

        mockMvc.perform(post("/api/orders/confirm").contentType("application/json").content("""
            {"sourceType":"BUY_NOW","skuId":1,"quantity":1}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pointDeduction.visible").value(false));
    }

    @Test
    void createOrderRequiresValidSingleUseTokenAndClearsCartItems() throws Exception {
        clearCart();
        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("""
            {"skuId":1,"quantity":1}
            """))
            .andExpect(status().isOk());
        Long cartItemId = jdbcTemplate.queryForObject("select id from cart_item where user_id = 1 and sku_id = 1", Long.class);
        MvcResult confirm = mockMvc.perform(post("/api/orders/confirm").contentType("application/json").content("""
            {"sourceType":"CART","cartItemIds":[%d],"usePoints":false}
            """.formatted(cartItemId)))
            .andExpect(status().isOk())
            .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(confirm.getResponse().getContentAsString(), "$.settlementToken");
        Integer payAmount = com.jayway.jsonpath.JsonPath.read(confirm.getResponse().getContentAsString(), "$.amount.payAmount");

        MvcResult created = mockMvc.perform(post("/api/orders/create").contentType("application/json").content("""
            {"settlementToken":"%s","expectedPayAmount":%d}
            """.formatted(token, payAmount)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("WAIT_PAY"))
            .andExpect(jsonPath("$.payStatus").value("UNPAID"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andReturn();
        Integer cartCount = jdbcTemplate.queryForObject("select count(*) from cart_item where id = ?", Integer.class, cartItemId);
        assertThat(cartCount).isZero();

        mockMvc.perform(post("/api/orders/create").contentType("application/json").content("""
            {"settlementToken":"%s","expectedPayAmount":%d}
            """.formatted(token, payAmount)))
            .andExpect(status().isBadRequest());

        Integer orderId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/orders/{id}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(orderId));
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("CANCELED"));
    }

    @Test
    void createOrderRejectsAmountChangedOffSaleAndStockNotEnough() throws Exception {
        clearCart();
        MvcResult confirm = mockMvc.perform(post("/api/orders/confirm").contentType("application/json").content("""
            {"sourceType":"BUY_NOW","skuId":1,"quantity":1}
            """))
            .andExpect(status().isOk())
            .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(confirm.getResponse().getContentAsString(), "$.settlementToken");
        mockMvc.perform(post("/api/orders/create").contentType("application/json").content("""
            {"settlementToken":"%s","expectedPayAmount":1}
            """.formatted(token)))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders/confirm").contentType("application/json").content("""
            {"sourceType":"BUY_NOW","skuId":5,"quantity":1}
            """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders/confirm").contentType("application/json").content("""
            {"sourceType":"BUY_NOW","skuId":1,"quantity":9999}
            """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void buyNowCreateDoesNotClearCart() throws Exception {
        clearCart();
        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("""
            {"skuId":1,"quantity":1}
            """))
            .andExpect(status().isOk());
        MvcResult confirm = mockMvc.perform(post("/api/orders/confirm").contentType("application/json").content("""
            {"sourceType":"BUY_NOW","skuId":2,"quantity":1}
            """))
            .andExpect(status().isOk())
            .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(confirm.getResponse().getContentAsString(), "$.settlementToken");
        Integer payAmount = com.jayway.jsonpath.JsonPath.read(confirm.getResponse().getContentAsString(), "$.amount.payAmount");

        mockMvc.perform(post("/api/orders/create").contentType("application/json").content("""
            {"settlementToken":"%s","expectedPayAmount":%d}
            """.formatted(token, payAmount)))
            .andExpect(status().isOk());

        Integer cartCount = jdbcTemplate.queryForObject("select count(*) from cart_item where user_id = 1", Integer.class);
        assertThat(cartCount).isEqualTo(1);
    }

    private void clearCart() {
        jdbcTemplate.update("update coupon_user set user_coupon_status = 'UNUSED', used_at = null, order_id = null");
        jdbcTemplate.update("delete from trade_order_amount");
        jdbcTemplate.update("delete from trade_order_item");
        jdbcTemplate.update("delete from trade_order");
        jdbcTemplate.update("update product_sku set stock = 120, locked_stock = 0 where id = 1");
        jdbcTemplate.update("update product_sku set stock = 80, locked_stock = 0 where id = 2");
        jdbcTemplate.update("update product_sku set stock = 50, locked_stock = 0 where id = 3");
        jdbcTemplate.update("update product_sku set stock = 200, locked_stock = 0 where id = 4");
        jdbcTemplate.update("update product_sku set stock = 20, locked_stock = 0 where id = 5");
        jdbcTemplate.update("update product_sku set stock = 60, locked_stock = 0 where id = 6");
        jdbcTemplate.update("update product_sku set stock = 100, locked_stock = 0 where id = 7");
        jdbcTemplate.update("delete from cart_item");
    }
}
