ALTER TABLE admin_role MODIFY permissions VARCHAR(500) NOT NULL;

INSERT INTO admin_role (role_code, role_name, permissions, status)
VALUES
  ('SUPER_ADMIN', '超级管理员', '*', 'ACTIVE'),
  ('PRODUCT_OPERATOR', '商品运营', 'product:read,product:write,product:publish,inventory:read', 'ACTIVE'),
  ('ORDER_SERVICE', '订单客服', 'order:read,order:ship', 'ACTIVE'),
  ('AFTERSALE_SERVICE', '售后客服', 'aftersale:read,aftersale:audit', 'ACTIVE'),
  ('FINANCE', '财务', 'finance:read,finance:refund,order:read,aftersale:read,log:read', 'ACTIVE'),
  ('READONLY_AUDITOR', '只读审计', 'product:read,order:read,aftersale:read,finance:read,inventory:read,log:read,user:read,coupon:read', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  permissions = VALUES(permissions),
  status = VALUES(status);

UPDATE admin_user_role aur
JOIN admin_role ar ON ar.role_code = 'SUPER_ADMIN'
SET aur.role_id = ar.id
WHERE aur.admin_user_id = 1;
