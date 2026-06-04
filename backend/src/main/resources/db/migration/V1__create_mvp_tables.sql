CREATE TABLE `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  mobile VARCHAR(20) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  avatar_url VARCHAR(255) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_mobile (mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_address (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  receiver_name VARCHAR(64) NOT NULL,
  receiver_mobile VARCHAR(20) NOT NULL,
  province VARCHAR(64) NOT NULL,
  city VARCHAR(64) NOT NULL,
  district VARCHAR(64) NOT NULL,
  detail_address VARCHAR(255) NOT NULL,
  postal_code VARCHAR(20) NULL,
  default_flag BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_address_user_id (user_id),
  CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NULL,
  name VARCHAR(64) NOT NULL,
  level INT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_product_category_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  subtitle VARCHAR(255) NULL,
  main_image_url VARCHAR(255) NOT NULL,
  product_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
  sale_status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
  delivery_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  allow_cart BOOLEAN NOT NULL DEFAULT TRUE,
  allow_single_buy BOOLEAN NOT NULL DEFAULT TRUE,
  support_point_deduction BOOLEAN NOT NULL DEFAULT FALSE,
  support_point_reward BOOLEAN NOT NULL DEFAULT FALSE,
  point_reward INT NOT NULL DEFAULT 0,
  virtual_sales INT NOT NULL DEFAULT 0,
  actual_sales INT NOT NULL DEFAULT 0,
  deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_code (product_code),
  KEY idx_product_category_id (category_id),
  KEY idx_product_sale_status (sale_status),
  CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES product_category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_sku (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  sku_name VARCHAR(120) NOT NULL,
  spec_json VARCHAR(500) NOT NULL,
  image_url VARCHAR(255) NULL,
  sale_price INT NOT NULL,
  line_price INT NULL,
  stock INT NOT NULL DEFAULT 0,
  locked_stock INT NOT NULL DEFAULT 0,
  sku_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_sku_code (sku_code),
  KEY idx_product_sku_product_id (product_id),
  CONSTRAINT fk_product_sku_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cart_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  checked_flag BOOLEAN NOT NULL DEFAULT TRUE,
  item_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cart_user_sku (user_id, sku_id),
  KEY idx_cart_item_user_id (user_id),
  CONSTRAINT fk_cart_item_user FOREIGN KEY (user_id) REFERENCES `user` (id),
  CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES product (id),
  CONSTRAINT fk_cart_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trade_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  order_status VARCHAR(30) NOT NULL,
  pay_status VARCHAR(30) NOT NULL,
  delivery_status VARCHAR(30) NOT NULL,
  aftersale_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
  source_type VARCHAR(20) NOT NULL DEFAULT 'APP',
  total_amount INT NOT NULL DEFAULT 0,
  discount_amount INT NOT NULL DEFAULT 0,
  coupon_amount INT NOT NULL DEFAULT 0,
  point_amount INT NOT NULL DEFAULT 0,
  freight_amount INT NOT NULL DEFAULT 0,
  pay_amount INT NOT NULL DEFAULT 0,
  receiver_name VARCHAR(64) NOT NULL,
  receiver_mobile VARCHAR(20) NOT NULL,
  receiver_address VARCHAR(500) NOT NULL,
  remark VARCHAR(255) NULL,
  pay_expire_time DATETIME NULL,
  pay_time DATETIME NULL,
  cancel_time DATETIME NULL,
  finish_time DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_trade_order_no (order_no),
  KEY idx_trade_order_user_id (user_id),
  KEY idx_trade_order_status (order_status),
  CONSTRAINT fk_trade_order_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trade_order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  sku_name VARCHAR(120) NOT NULL,
  product_image_url VARCHAR(255) NOT NULL,
  sale_price INT NOT NULL,
  quantity INT NOT NULL,
  total_amount INT NOT NULL,
  discount_amount INT NOT NULL DEFAULT 0,
  pay_amount INT NOT NULL,
  support_refund BOOLEAN NOT NULL DEFAULT TRUE,
  aftersale_quantity INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_trade_order_item_order_id (order_id),
  CONSTRAINT fk_trade_order_item_order FOREIGN KEY (order_id) REFERENCES trade_order (id),
  CONSTRAINT fk_trade_order_item_product FOREIGN KEY (product_id) REFERENCES product (id),
  CONSTRAINT fk_trade_order_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trade_order_amount (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_amount INT NOT NULL DEFAULT 0,
  activity_discount_amount INT NOT NULL DEFAULT 0,
  coupon_discount_amount INT NOT NULL DEFAULT 0,
  point_discount_amount INT NOT NULL DEFAULT 0,
  freight_amount INT NOT NULL DEFAULT 0,
  freight_discount_amount INT NOT NULL DEFAULT 0,
  pay_amount INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_trade_order_amount_order_id (order_id),
  CONSTRAINT fk_trade_order_amount_order FOREIGN KEY (order_id) REFERENCES trade_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  coupon_code VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  coupon_type VARCHAR(30) NOT NULL,
  threshold_amount INT NOT NULL DEFAULT 0,
  discount_amount INT NOT NULL DEFAULT 0,
  discount_rate INT NULL,
  total_quantity INT NOT NULL DEFAULT 0,
  received_quantity INT NOT NULL DEFAULT 0,
  used_quantity INT NOT NULL DEFAULT 0,
  receive_start_time DATETIME NOT NULL,
  receive_end_time DATETIME NOT NULL,
  use_start_time DATETIME NOT NULL,
  use_end_time DATETIME NOT NULL,
  coupon_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_coupon_code (coupon_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  coupon_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  coupon_no VARCHAR(64) NOT NULL,
  user_coupon_status VARCHAR(20) NOT NULL DEFAULT 'UNUSED',
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  used_at DATETIME NULL,
  order_id BIGINT NULL,
  UNIQUE KEY uk_coupon_user_no (coupon_no),
  KEY idx_coupon_user_user_id (user_id),
  KEY idx_coupon_user_coupon_id (coupon_id),
  CONSTRAINT fk_coupon_user_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id),
  CONSTRAINT fk_coupon_user_user FOREIGN KEY (user_id) REFERENCES `user` (id),
  CONSTRAINT fk_coupon_user_order FOREIGN KEY (order_id) REFERENCES trade_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_point_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  available_points INT NOT NULL DEFAULT 0,
  locked_points INT NOT NULL DEFAULT 0,
  total_earned_points INT NOT NULL DEFAULT 0,
  total_used_points INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_point_account_user_id (user_id),
  CONSTRAINT fk_user_point_account_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_point_flow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  flow_no VARCHAR(64) NOT NULL,
  change_type VARCHAR(30) NOT NULL,
  change_points INT NOT NULL,
  balance_after INT NOT NULL,
  biz_type VARCHAR(30) NOT NULL,
  biz_id BIGINT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_point_flow_no (flow_no),
  KEY idx_user_point_flow_user_id (user_id),
  CONSTRAINT fk_user_point_flow_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
