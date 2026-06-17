INSERT INTO `user` (id, mobile, nickname, avatar_url, status)
VALUES (1, '13800000001', '测试用户', NULL, 'ACTIVE');

INSERT INTO user_address (
  id, user_id, receiver_name, receiver_mobile, province, city, district, detail_address, postal_code, default_flag
) VALUES (
  1, 1, '张三', '13800000001', '北京市', '北京市', '朝阳区', '望京测试路 100 号', '100000', TRUE
);

INSERT INTO product_category (id, parent_id, name, level, sort_order, status)
VALUES
  (1, NULL, '手机数码', 1, 10, 'ENABLED'),
  (2, NULL, '生鲜冷链', 1, 20, 'ENABLED'),
  (3, NULL, '组合专区', 1, 30, 'ENABLED');

INSERT INTO product (
  id, category_id, product_code, name, subtitle, main_image_url, product_type, sale_status, delivery_type,
  allow_cart, allow_single_buy, support_point_deduction, support_point_reward, point_reward, virtual_sales, actual_sales
) VALUES
  (1, 1, 'P-MVP-普通-001', 'Apple AirPods Pro 第二代', '普通商品，可加入购物车并单独购买', '/images/products/airpods.png', 'NORMAL', 'ON_SALE', 'NORMAL', TRUE, TRUE, FALSE, TRUE, 100, 120, 0),
  (2, 2, 'P-MVP-冷链-001', '澳洲冷链牛排套餐', '冷链商品，用于验证冷链运费', '/images/products/steak.png', 'NORMAL', 'ON_SALE', 'COLD_CHAIN', TRUE, TRUE, FALSE, TRUE, 30, 60, 0),
  (3, 1, 'P-MVP-不可加购-001', '品牌定制服务卡', '不允许加入购物车，仅用于规则测试', '/images/products/service-card.png', 'NORMAL', 'ON_SALE', 'NORMAL', FALSE, TRUE, FALSE, FALSE, 0, 12, 0),
  (4, 3, 'P-MVP-不可单独购买-001', '配件加购保护壳', '不可单独购买，需搭配普通商品', '/images/products/case.png', 'NORMAL', 'ON_SALE', 'NORMAL', TRUE, FALSE, FALSE, FALSE, 0, 88, 0),
  (5, 1, 'P-MVP-下架-001', '历史下架蓝牙耳机', '下架商品，不应在列表展示或购买', '/images/products/off-sale-headset.png', 'NORMAL', 'OFF_SALE', 'NORMAL', TRUE, TRUE, FALSE, FALSE, 0, 20, 0),
  (6, 1, 'P-MVP-积分抵扣-001', '智能运动手表', '支持积分抵扣商品', '/images/products/watch.png', 'NORMAL', 'ON_SALE', 'NORMAL', TRUE, TRUE, TRUE, TRUE, 80, 45, 0),
  (7, 1, 'P-MVP-不支持积分-001', '限价手机充电器', '不支持积分抵扣商品', '/images/products/charger.png', 'NORMAL', 'ON_SALE', 'NORMAL', TRUE, TRUE, FALSE, TRUE, 10, 34, 0);

INSERT INTO product_sku (
  id, product_id, sku_code, sku_name, spec_json, image_url, sale_price, line_price, stock, locked_stock, sku_status
) VALUES
  (1, 1, 'SKU-MVP-普通-001', '白色 / USB-C', '{"颜色":"白色","接口":"USB-C"}', '/images/products/airpods-white.png', 169900, 189900, 120, 0, 'ENABLED'),
  (2, 2, 'SKU-MVP-冷链-001', '牛排套餐 / 1kg', '{"规格":"1kg","配送":"冷链"}', '/images/products/steak-1kg.png', 19900, 25900, 80, 0, 'ENABLED'),
  (3, 3, 'SKU-MVP-不可加购-001', '标准版', '{"版本":"标准版"}', '/images/products/service-card-standard.png', 29900, NULL, 50, 0, 'ENABLED'),
  (4, 4, 'SKU-MVP-不可单独购买-001', '透明 / iPhone', '{"颜色":"透明","机型":"iPhone"}', '/images/products/case-clear.png', 2900, 4900, 200, 0, 'ENABLED'),
  (5, 5, 'SKU-MVP-下架-001', '黑色', '{"颜色":"黑色"}', '/images/products/off-sale-headset-black.png', 9900, 12900, 20, 0, 'ENABLED'),
  (6, 6, 'SKU-MVP-积分抵扣-001', '曜石黑', '{"颜色":"曜石黑"}', '/images/products/watch-black.png', 59900, 69900, 60, 0, 'ENABLED'),
  (7, 7, 'SKU-MVP-不支持积分-001', '20W 快充', '{"功率":"20W"}', '/images/products/charger-20w.png', 5900, 7900, 100, 0, 'ENABLED');

INSERT INTO coupon (
  id, coupon_code, name, coupon_type, threshold_amount, discount_amount, discount_rate,
  total_quantity, received_quantity, used_quantity, receive_start_time, receive_end_time,
  use_start_time, use_end_time, coupon_status
) VALUES (
  1, 'C-MVP-MJ-1000-100', '满1000减100优惠券', 'FULL_REDUCTION', 100000, 10000, NULL,
  1000, 1, 0, '2026-01-01 00:00:00', '2026-12-31 23:59:59',
  '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ENABLED'
);

INSERT INTO coupon_user (id, coupon_id, user_id, coupon_no, user_coupon_status)
VALUES (1, 1, 1, 'CU-MVP-000001', 'UNUSED');

INSERT INTO user_point_account (
  id, user_id, available_points, locked_points, total_earned_points, total_used_points
) VALUES (
  1, 1, 5000, 0, 5000, 0
);

INSERT INTO user_point_flow (
  id, user_id, flow_no, change_type, change_points, balance_after, biz_type, biz_id, remark
) VALUES (
  1, 1, 'PF-MVP-INIT-000001', 'EARN', 5000, 5000, 'INIT', NULL, 'MVP 初始化测试积分'
);
