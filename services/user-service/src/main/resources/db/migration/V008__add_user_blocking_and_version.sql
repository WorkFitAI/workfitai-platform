-- V008__add_user_blocking_and_version.sql
-- Add user blocking functionality and optimistic locking

-- Add is_blocked column to users table
ALTER TABLE users
ADD COLUMN IF NOT EXISTS is_blocked BOOLEAN NOT NULL DEFAULT FALSE;

-- Add version column for optimistic locking
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Update existing records to have version = 0 (for records created before this migration)
UPDATE users SET version = 0 WHERE version IS NULL;

UPDATE users SET is_blocked = FALSE WHERE is_blocked IS NULL;

-- Create index on is_blocked for faster filtering
CREATE INDEX idx_users_is_blocked ON users (is_blocked);

-- Create index on is_deleted for faster filtering (if not exists)
CREATE INDEX IF NOT EXISTS idx_users_is_deleted ON users (is_deleted);