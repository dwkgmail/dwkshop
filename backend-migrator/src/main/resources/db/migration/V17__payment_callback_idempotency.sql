ALTER TABLE dwkshop_order.payment_transaction
  DROP INDEX idx_payment_transaction_channel_trade_no,
  ADD UNIQUE KEY uk_payment_transaction_channel_trade_no (channel_trade_no);
