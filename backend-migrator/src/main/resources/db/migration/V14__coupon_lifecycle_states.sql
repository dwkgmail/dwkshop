ALTER TABLE dwkshop_marketing.coupon_user
  ADD COLUMN locked_at DATETIME NULL,
  ADD COLUMN lock_key VARCHAR(96) NULL,
  ADD COLUMN released_at DATETIME NULL,
  ADD COLUMN refunded_at DATETIME NULL;

UPDATE dwkshop_marketing.coupon_user
SET user_coupon_status = 'AVAILABLE'
WHERE user_coupon_status = 'UNUSED';

ALTER TABLE dwkshop_order.trade_order
  ADD COLUMN coupon_user_id BIGINT NULL;
