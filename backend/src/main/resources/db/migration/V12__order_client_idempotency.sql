ALTER TABLE trade_order ADD COLUMN client_request_id VARCHAR(64) NULL;
CREATE UNIQUE INDEX uk_trade_order_user_client_request ON trade_order (user_id, client_request_id);
