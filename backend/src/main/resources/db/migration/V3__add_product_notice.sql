CREATE TABLE product_notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  notice_title VARCHAR(120) NOT NULL DEFAULT '用户购买须知',
  notice_content VARCHAR(2000) NOT NULL,
  enabled_flag BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_notice_product_id (product_id),
  CONSTRAINT fk_product_notice_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO product_notice (id, product_id, notice_title, notice_content, enabled_flag)
VALUES
  (1, 1, '用户购买须知', '商品以实物为准；请确认规格、地址后再提交订单。', TRUE),
  (2, 2, '用户购买须知', '冷链商品发出后不支持无理由退换；请确认收货地址可配送。', TRUE),
  (3, 6, '用户购买须知', '该商品支持积分抵扣，具体抵扣金额以确认订单页为准。', TRUE);
