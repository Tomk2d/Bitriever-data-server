-- Add Bithumb last trading history sync timestamp to users table.
-- Optional: run this if not using JPA ddl-auto=update.
ALTER TABLE users ADD COLUMN IF NOT EXISTS bithumb_last_trading_history_update_at TIMESTAMP;
