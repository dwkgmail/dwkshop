ALTER TABLE `user`
  ADD COLUMN password_hash VARCHAR(255) NULL AFTER avatar_url;

UPDATE `user`
SET password_hash = 'pbkdf2$310000$ZHdrc2hvcC11c2VyLXNhbHQ=$jwFBZjcHNxM8LMJzJQTwZB4VsGQPum6cNa8NG+pbj2k='
WHERE id = 1;

ALTER TABLE `user`
  MODIFY password_hash VARCHAR(255) NOT NULL;

CREATE TABLE admin_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_admin_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO admin_user (id, username, password_hash, display_name, status)
VALUES (
  1,
  'admin',
  'pbkdf2$310000$ZHdrc2hvcC1hZG1pbi1zYWx0$2/HJ/Jk6dNFUiGd62ev2TXHHPVyHn769M3en/nOYW/g=',
  '管理员',
  'ACTIVE'
);
