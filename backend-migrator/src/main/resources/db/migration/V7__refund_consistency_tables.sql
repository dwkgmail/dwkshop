CREATE TABLE aftersale_refund_flow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  aftersale_id BIGINT NOT NULL,
  aftersale_no VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  flow_status VARCHAR(30) NOT NULL,
  current_step VARCHAR(30) NULL,
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(255) NULL,
  command_no VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_aftersale_refund_flow_aftersale_id (aftersale_id),
  UNIQUE KEY uk_aftersale_refund_flow_aftersale_no (aftersale_no),
  UNIQUE KEY uk_aftersale_refund_flow_command_no (command_no),
  KEY idx_aftersale_refund_flow_order_id (order_id),
  KEY idx_aftersale_refund_flow_status (flow_status),
  CONSTRAINT fk_aftersale_refund_flow_aftersale FOREIGN KEY (aftersale_id) REFERENCES aftersale_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_refund_command (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  command_no VARCHAR(64) NOT NULL,
  command_type VARCHAR(30) NOT NULL,
  command_status VARCHAR(30) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  result_json LONGTEXT NULL,
  last_error VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_refund_command_no (command_no),
  KEY idx_product_refund_command_type (command_type),
  KEY idx_product_refund_command_status (command_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
