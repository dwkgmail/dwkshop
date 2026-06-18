-- Each runtime gets a dedicated MySQL schema. The migration deliberately copies
-- data before applications switch their JDBC URLs. Legacy dwkshop tables remain
-- as a rollback source and can be removed after the split has been observed.
CREATE DATABASE IF NOT EXISTS dwkshop_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS dwkshop_product CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS dwkshop_cart CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS dwkshop_member CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS dwkshop_marketing CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS dwkshop_order CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS dwkshop_aftersale CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON dwkshop_auth.* TO 'dwkshop'@'%';
GRANT ALL PRIVILEGES ON dwkshop_product.* TO 'dwkshop'@'%';
GRANT ALL PRIVILEGES ON dwkshop_cart.* TO 'dwkshop'@'%';
GRANT ALL PRIVILEGES ON dwkshop_member.* TO 'dwkshop'@'%';
GRANT ALL PRIVILEGES ON dwkshop_marketing.* TO 'dwkshop'@'%';
GRANT ALL PRIVILEGES ON dwkshop_order.* TO 'dwkshop'@'%';
GRANT ALL PRIVILEGES ON dwkshop_aftersale.* TO 'dwkshop'@'%';

CREATE TABLE dwkshop_auth.`user` LIKE dwkshop.`user`;
INSERT INTO dwkshop_auth.`user` SELECT * FROM dwkshop.`user`;
CREATE TABLE dwkshop_auth.admin_user LIKE dwkshop.admin_user;
INSERT INTO dwkshop_auth.admin_user SELECT * FROM dwkshop.admin_user;

CREATE TABLE dwkshop_product.product_category LIKE dwkshop.product_category;
INSERT INTO dwkshop_product.product_category SELECT * FROM dwkshop.product_category;
CREATE TABLE dwkshop_product.product LIKE dwkshop.product;
INSERT INTO dwkshop_product.product SELECT * FROM dwkshop.product;
CREATE TABLE dwkshop_product.product_sku LIKE dwkshop.product_sku;
INSERT INTO dwkshop_product.product_sku SELECT * FROM dwkshop.product_sku;
CREATE TABLE dwkshop_product.product_notice LIKE dwkshop.product_notice;
INSERT INTO dwkshop_product.product_notice SELECT * FROM dwkshop.product_notice;
CREATE TABLE dwkshop_product.product_refund_command LIKE dwkshop.product_refund_command;
INSERT INTO dwkshop_product.product_refund_command SELECT * FROM dwkshop.product_refund_command;
ALTER TABLE dwkshop_product.product
  ADD CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES dwkshop_product.product_category (id);
ALTER TABLE dwkshop_product.product_sku
  ADD CONSTRAINT fk_product_sku_product FOREIGN KEY (product_id) REFERENCES dwkshop_product.product (id);
ALTER TABLE dwkshop_product.product_notice
  ADD CONSTRAINT fk_product_notice_product FOREIGN KEY (product_id) REFERENCES dwkshop_product.product (id);

CREATE TABLE dwkshop_cart.cart_item LIKE dwkshop.cart_item;
INSERT INTO dwkshop_cart.cart_item SELECT * FROM dwkshop.cart_item;

CREATE TABLE dwkshop_member.user_address LIKE dwkshop.user_address;
INSERT INTO dwkshop_member.user_address SELECT * FROM dwkshop.user_address;
CREATE TABLE dwkshop_member.user_point_account LIKE dwkshop.user_point_account;
INSERT INTO dwkshop_member.user_point_account SELECT * FROM dwkshop.user_point_account;
CREATE TABLE dwkshop_member.user_point_flow LIKE dwkshop.user_point_flow;
INSERT INTO dwkshop_member.user_point_flow SELECT * FROM dwkshop.user_point_flow;

CREATE TABLE dwkshop_marketing.coupon LIKE dwkshop.coupon;
INSERT INTO dwkshop_marketing.coupon SELECT * FROM dwkshop.coupon;
CREATE TABLE dwkshop_marketing.coupon_user LIKE dwkshop.coupon_user;
INSERT INTO dwkshop_marketing.coupon_user SELECT * FROM dwkshop.coupon_user;
ALTER TABLE dwkshop_marketing.coupon_user
  ADD CONSTRAINT fk_coupon_user_coupon FOREIGN KEY (coupon_id) REFERENCES dwkshop_marketing.coupon (id);

CREATE TABLE dwkshop_order.trade_order LIKE dwkshop.trade_order;
INSERT INTO dwkshop_order.trade_order SELECT * FROM dwkshop.trade_order;
CREATE TABLE dwkshop_order.trade_order_item LIKE dwkshop.trade_order_item;
INSERT INTO dwkshop_order.trade_order_item SELECT * FROM dwkshop.trade_order_item;
CREATE TABLE dwkshop_order.trade_order_amount LIKE dwkshop.trade_order_amount;
INSERT INTO dwkshop_order.trade_order_amount SELECT * FROM dwkshop.trade_order_amount;
ALTER TABLE dwkshop_order.trade_order_item
  ADD CONSTRAINT fk_trade_order_item_order FOREIGN KEY (order_id) REFERENCES dwkshop_order.trade_order (id);
ALTER TABLE dwkshop_order.trade_order_amount
  ADD CONSTRAINT fk_trade_order_amount_order FOREIGN KEY (order_id) REFERENCES dwkshop_order.trade_order (id);

CREATE TABLE dwkshop_aftersale.aftersale_order LIKE dwkshop.aftersale_order;
INSERT INTO dwkshop_aftersale.aftersale_order SELECT * FROM dwkshop.aftersale_order;
CREATE TABLE dwkshop_aftersale.aftersale_refund_flow LIKE dwkshop.aftersale_refund_flow;
INSERT INTO dwkshop_aftersale.aftersale_refund_flow SELECT * FROM dwkshop.aftersale_refund_flow;
CREATE TABLE dwkshop_aftersale.aftersale_outbox_event LIKE dwkshop.aftersale_outbox_event;
INSERT INTO dwkshop_aftersale.aftersale_outbox_event SELECT * FROM dwkshop.aftersale_outbox_event;
ALTER TABLE dwkshop_aftersale.aftersale_refund_flow
  ADD CONSTRAINT fk_aftersale_refund_flow_aftersale FOREIGN KEY (aftersale_id) REFERENCES dwkshop_aftersale.aftersale_order (id);
ALTER TABLE dwkshop_aftersale.aftersale_outbox_event
  ADD CONSTRAINT fk_aftersale_outbox_aggregate FOREIGN KEY (aggregate_id) REFERENCES dwkshop_aftersale.aftersale_order (id);
