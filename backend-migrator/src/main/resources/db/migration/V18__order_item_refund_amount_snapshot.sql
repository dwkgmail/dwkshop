ALTER TABLE trade_order_item
  ADD COLUMN coupon_share_amount INT NOT NULL DEFAULT 0 AFTER pay_amount,
  ADD COLUMN point_share_amount INT NOT NULL DEFAULT 0 AFTER coupon_share_amount,
  ADD COLUMN freight_share_amount INT NOT NULL DEFAULT 0 AFTER point_share_amount,
  ADD COLUMN refundable_amount INT NOT NULL DEFAULT 0 AFTER refund_status;

UPDATE trade_order_item
SET refundable_amount = GREATEST(pay_amount - refund_amount, 0);
