ALTER TABLE trade_order_item
  ADD COLUMN coupon_share_amount INT NOT NULL DEFAULT 0 AFTER pay_amount,
  ADD COLUMN point_share_amount INT NOT NULL DEFAULT 0 AFTER coupon_share_amount,
  ADD COLUMN freight_share_amount INT NOT NULL DEFAULT 0 AFTER point_share_amount,
  ADD COLUMN refundable_quantity INT NOT NULL DEFAULT 0 AFTER aftersale_quantity,
  ADD COLUMN refunded_quantity INT NOT NULL DEFAULT 0 AFTER refundable_quantity,
  ADD COLUMN refund_amount INT NOT NULL DEFAULT 0 AFTER refunded_quantity,
  ADD COLUMN refund_status VARCHAR(30) NOT NULL DEFAULT 'NONE' AFTER refund_amount,
  ADD COLUMN refundable_amount INT NOT NULL DEFAULT 0 AFTER refund_status;

UPDATE trade_order_item
SET refundable_quantity = GREATEST(quantity - aftersale_quantity, 0),
    refunded_quantity = aftersale_quantity,
    refund_amount = 0,
    refundable_amount = GREATEST(pay_amount, 0),
    refund_status = CASE
      WHEN aftersale_quantity >= quantity THEN 'REFUNDED'
      WHEN aftersale_quantity > 0 THEN 'PARTIAL_REFUNDED'
      ELSE 'NONE'
    END;
