CREATE TABLE aftersale_outbox_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL,
  aggregate_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  publish_status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME NULL,
  last_error VARCHAR(255) NULL,
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_aftersale_outbox_event_id (event_id),
  UNIQUE KEY uk_aftersale_outbox_aggregate_type (aggregate_id, event_type),
  KEY idx_aftersale_outbox_pending (publish_status, next_retry_at, id),
  CONSTRAINT fk_aftersale_outbox_aggregate FOREIGN KEY (aggregate_id) REFERENCES aftersale_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
