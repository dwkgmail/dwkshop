ALTER TABLE dwkshop_member.user_point_flow
  ADD COLUMN order_id BIGINT NULL AFTER biz_id,
  ADD COLUMN biz_no VARCHAR(64) NULL AFTER order_id,
  ADD COLUMN change_amount INT NOT NULL DEFAULT 0 AFTER biz_no,
  ADD COLUMN before_balance INT NOT NULL DEFAULT 0 AFTER change_amount,
  ADD COLUMN after_balance INT NOT NULL DEFAULT 0 AFTER before_balance,
  ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS' AFTER after_balance,
  ADD KEY idx_user_point_flow_order_id (order_id),
  ADD KEY idx_user_point_flow_biz_no (biz_no);

UPDATE dwkshop_member.user_point_flow
SET change_amount = change_points,
    before_balance = balance_after - change_points,
    after_balance = balance_after,
    order_id = biz_id,
    biz_no = flow_no
WHERE change_amount = 0
  AND before_balance = 0
  AND after_balance = 0;
