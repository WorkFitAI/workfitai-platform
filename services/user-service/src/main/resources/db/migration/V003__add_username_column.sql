-- Add username column to users table in user-service
-- V003__add_username_column.sql

ALTER TABLE users ADD COLUMN username VARCHAR(255);

CREATE UNIQUE INDEX idx_users_username ON users (username);

-- Update existing users with a default username based on email
UPDATE users
SET
    username = LOWER(SPLIT_PART(email, '@', 1))
WHERE
    username IS NULL;

-- Make username NOT NULL after setting default values
ALTER TABLE users ALTER COLUMN username SET NOT NULL;