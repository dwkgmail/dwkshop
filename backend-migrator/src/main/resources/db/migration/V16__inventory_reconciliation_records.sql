CREATE TABLE dwkshop_product.inventory_reconciliation_repair_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sku_id BIGINT NOT NULL,
  before_locked_stock INT NOT NULL,
  projected_locked_stock INT NOT NULL,
  difference INT NOT NULL,
  repair_type VARCHAR(30) NOT NULL,
  repair_status VARCHAR(20) NOT NULL,
  operator VARCHAR(64) NOT NULL,
  reason VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  KEY idx_inventory_repair_sku_id (sku_id),
  KEY idx_inventory_repair_created_at (created_at)
);
