ALTER TABLE trade_order_item
  ADD COLUMN refundable_quantity INT NOT NULL DEFAULT 0 AFTER aftersale_quantity,
  ADD COLUMN refunded_quantity INT NOT NULL DEFAULT 0 AFTER refundable_quantity,
  ADD COLUMN refund_amount INT NOT NULL DEFAULT 0 AFTER refunded_quantity,
  ADD COLUMN refund_status VARCHAR(30) NOT NULL DEFAULT 'NONE' AFTER refund_amount;

UPDATE trade_order_item
SET refundable_quantity = GREATEST(quantity - aftersale_quantity, 0),
    refunded_quantity = aftersale_quantity,
    refund_status = CASE
      WHEN aftersale_quantity >= quantity THEN 'REFUNDED'
      WHEN aftersale_quantity > 0 THEN 'PARTIAL_REFUNDED'
      ELSE 'NONE'
    END;

ALTER TABLE aftersale_order
  ADD COLUMN refund_scope VARCHAR(30) NOT NULL DEFAULT 'FULL' AFTER aftersale_type,
  ADD COLUMN include_freight BOOLEAN NOT NULL DEFAULT FALSE AFTER refund_amount,
  ADD COLUMN refund_reason_type VARCHAR(50) NULL AFTER reason,
  ADD COLUMN evidence_images VARCHAR(1000) NULL AFTER refund_reason_type,
  ADD COLUMN return_logistics_company VARCHAR(64) NULL AFTER evidence_images,
  ADD COLUMN return_logistics_no VARCHAR(64) NULL AFTER return_logistics_company;

UPDATE aftersale_order
SET aftersale_type = 'REFUND_ONLY'
WHERE aftersale_type = 'REFUND';

CREATE TABLE aftersale_order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  aftersale_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  refund_amount INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_aftersale_order_item_aftersale_id (aftersale_id),
  KEY idx_aftersale_order_item_order_id (order_id),
  KEY idx_aftersale_order_item_sku_id (sku_id),
  CONSTRAINT fk_aftersale_order_item_aftersale FOREIGN KEY (aftersale_id) REFERENCES aftersale_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
