CREATE DATABASE IF NOT EXISTS dwkshop_audit CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON dwkshop_audit.* TO 'dwkshop'@'%';

CREATE TABLE dwkshop_audit.admin_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NOT NULL,
  operation_type VARCHAR(64) NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_id BIGINT NULL,
  before_value TEXT NULL,
  after_value TEXT NULL,
  reason VARCHAR(500) NOT NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_admin_operation_log_created_at (created_at),
  KEY idx_admin_operation_log_biz_type (biz_type),
  KEY idx_admin_operation_log_biz_id (biz_id),
  KEY idx_admin_operation_log_operator_id (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
