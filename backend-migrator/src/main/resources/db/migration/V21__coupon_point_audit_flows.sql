CREATE TABLE dwkshop_marketing.coupon_lock_flow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  coupon_user_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  order_id BIGINT NULL,
  source VARCHAR(30) NOT NULL,
  biz_no VARCHAR(96) NOT NULL,
  flow_type VARCHAR(30) NOT NULL,
  before_status VARCHAR(30) NOT NULL,
  after_status VARCHAR(30) NOT NULL,
  lock_key VARCHAR(96) NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  operated_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_coupon_lock_flow_idempotency (idempotency_key),
  KEY idx_coupon_lock_flow_coupon_user_id (coupon_user_id),
  KEY idx_coupon_lock_flow_user_id (user_id),
  KEY idx_coupon_lock_flow_biz_no (biz_no),
  CONSTRAINT fk_coupon_lock_flow_coupon_user FOREIGN KEY (coupon_user_id) REFERENCES dwkshop_marketing.coupon_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dwkshop_marketing.coupon_use_flow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  coupon_user_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  source VARCHAR(30) NOT NULL,
  biz_no VARCHAR(96) NOT NULL,
  flow_type VARCHAR(30) NOT NULL,
  before_status VARCHAR(30) NOT NULL,
  after_status VARCHAR(30) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  operated_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_coupon_use_flow_idempotency (idempotency_key),
  KEY idx_coupon_use_flow_coupon_user_id (coupon_user_id),
  KEY idx_coupon_use_flow_user_id (user_id),
  KEY idx_coupon_use_flow_order_id (order_id),
  KEY idx_coupon_use_flow_biz_no (biz_no),
  CONSTRAINT fk_coupon_use_flow_coupon_user FOREIGN KEY (coupon_user_id) REFERENCES dwkshop_marketing.coupon_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE dwkshop_member.user_point_flow
  ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'ORDER' AFTER user_id,
  ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER biz_no,
  ADD UNIQUE KEY uk_user_point_flow_idempotency (idempotency_key);

UPDATE dwkshop_member.user_point_flow
SET idempotency_key = flow_no
WHERE idempotency_key IS NULL;

ALTER TABLE dwkshop_member.user_point_flow
  MODIFY COLUMN idempotency_key VARCHAR(128) NOT NULL;

CREATE TABLE dwkshop_member.point_freeze (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  biz_no VARCHAR(64) NOT NULL,
  source VARCHAR(30) NOT NULL,
  freeze_points INT NOT NULL,
  before_available_points INT NOT NULL,
  after_available_points INT NOT NULL,
  before_locked_points INT NOT NULL,
  after_locked_points INT NOT NULL,
  status VARCHAR(30) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  frozen_at DATETIME NULL,
  released_at DATETIME NULL,
  deducted_at DATETIME NULL,
  refunded_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_point_freeze_biz_no (biz_no),
  UNIQUE KEY uk_point_freeze_idempotency (idempotency_key),
  KEY idx_point_freeze_user_id (user_id),
  KEY idx_point_freeze_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
