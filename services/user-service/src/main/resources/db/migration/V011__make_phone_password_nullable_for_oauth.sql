-- V011__make_phone_password_nullable_for_oauth.sql
-- Make phone_number and password_hash nullable to support OAuth users

-- Remove NOT NULL constraint from phone_number
ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;

-- Remove NOT NULL constraint from password_hash
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Add comment explaining the nullable columns
COMMENT ON COLUMN users.phone_number IS 'Phone number - nullable for OAuth users who register without phone';

COMMENT ON COLUMN users.password_hash IS 'Password hash - nullable for OAuth-only users who have no password';