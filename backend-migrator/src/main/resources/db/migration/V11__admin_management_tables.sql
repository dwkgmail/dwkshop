CREATE TABLE admin_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  permissions VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_admin_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_admin_user_role_user (admin_user_id),
  KEY idx_admin_user_role_role (role_id),
  CONSTRAINT fk_admin_user_role_user FOREIGN KEY (admin_user_id) REFERENCES admin_user (id),
  CONSTRAINT fk_admin_user_role_role FOREIGN KEY (role_id) REFERENCES admin_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_user_id BIGINT NULL,
  admin_username VARCHAR(64) NOT NULL,
  module VARCHAR(64) NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id BIGINT NULL,
  detail VARCHAR(500) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_admin_operation_log_created_at (created_at),
  KEY idx_admin_operation_log_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO admin_role (id, role_code, role_name, permissions, status)
VALUES
  (1, 'SUPER_ADMIN', '超级管理员', 'dashboard,product,order,aftersale,coupon,user,permission,log', 'ACTIVE'),
  (2, 'OPERATOR', '运营专员', 'dashboard,product,order,aftersale,coupon,user,log', 'ACTIVE'),
  (3, 'AUDITOR', '审核员', 'dashboard,aftersale,order,log', 'ACTIVE');

INSERT INTO admin_user_role (admin_user_id, role_id)
VALUES (1, 1);

INSERT INTO admin_operation_log (admin_user_id, admin_username, module, action, target_type, target_id, detail)
VALUES (1, 'admin', 'SYSTEM', 'INIT', 'ADMIN_ROLE', 1, '初始化后台角色与操作日志');
