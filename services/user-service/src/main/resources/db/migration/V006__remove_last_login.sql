-- V006__remove_last_login.sql
-- Remove last_login column from users table
-- Reason: Login tracking is handled by auth-service sessions in MongoDB
-- user-service doesn't need to track last login timestamp

-- Drop last_login column
ALTER TABLE users DROP COLUMN IF EXISTS last_login;

-- Add table comment for documentation
COMMENT ON
TABLE users IS 'Base user table - login tracking handled by auth-service (MongoDB sessions)';