ALTER TABLE admin_operation_log
  ADD COLUMN operator_id BIGINT NULL AFTER id,
  ADD COLUMN operator_name VARCHAR(64) NOT NULL DEFAULT '' AFTER operator_id,
  ADD COLUMN operation_type VARCHAR(64) NOT NULL DEFAULT '' AFTER operator_name,
  ADD COLUMN biz_type VARCHAR(64) NOT NULL DEFAULT '' AFTER operation_type,
  ADD COLUMN biz_id BIGINT NULL AFTER biz_type,
  ADD COLUMN before_value TEXT NULL AFTER biz_id,
  ADD COLUMN after_value TEXT NULL AFTER before_value,
  ADD COLUMN reason VARCHAR(500) NULL AFTER after_value,
  ADD COLUMN ip VARCHAR(64) NULL AFTER reason,
  ADD COLUMN user_agent VARCHAR(255) NULL AFTER ip;

UPDATE admin_operation_log
SET
  operator_id = admin_user_id,
  operator_name = admin_username,
  operation_type = action,
  biz_type = target_type,
  biz_id = target_id,
  before_value = NULL,
  after_value = NULL,
  reason = detail,
  ip = NULL,
  user_agent = NULL
WHERE operator_name = '';
