CREATE TABLE dwkshop_order.order_outbox_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL,
  aggregate_id BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  routing_key VARCHAR(80) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  publish_status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME NULL,
  last_error VARCHAR(255) NULL,
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_order_outbox_event_id (event_id),
  UNIQUE KEY uk_order_outbox_aggregate_type (aggregate_id, event_type),
  KEY idx_order_outbox_pending (publish_status, next_retry_at, id)
);

CREATE TABLE dwkshop_product.inventory_order_item_state (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  state VARCHAR(20) NOT NULL,
  last_event_version INT NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_inventory_order_sku (order_id, sku_id),
  KEY idx_inventory_state_sku (sku_id)
);

CREATE TABLE dwkshop_product.inventory_consumed_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  consumed_at DATETIME NOT NULL,
  UNIQUE KEY uk_inventory_event_sku (event_id, sku_id),
  UNIQUE KEY uk_inventory_business_event (order_id, sku_id, event_type),
  KEY idx_inventory_consumed_at (consumed_at)
);
