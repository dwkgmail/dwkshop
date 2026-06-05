ALTER TABLE trade_order ADD COLUMN logistics_company VARCHAR(64) NULL;
ALTER TABLE trade_order ADD COLUMN logistics_no VARCHAR(64) NULL;
ALTER TABLE trade_order ADD COLUMN delivery_remark VARCHAR(255) NULL;
ALTER TABLE trade_order ADD COLUMN delivery_time DATETIME NULL;
