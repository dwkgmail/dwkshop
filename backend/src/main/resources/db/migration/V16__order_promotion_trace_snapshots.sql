ALTER TABLE trade_order_amount
  ADD COLUMN promotion_trace_json TEXT NULL AFTER pay_amount;

ALTER TABLE trade_order_item
  ADD COLUMN promotion_share_json TEXT NULL AFTER freight_share_amount;
