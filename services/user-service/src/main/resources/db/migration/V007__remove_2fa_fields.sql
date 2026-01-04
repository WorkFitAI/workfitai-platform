-- Migration: Remove 2FA fields from users table
-- Date: December 17, 2025
-- Description:
--   2FA functionality is now fully handled by auth-service and stored in MongoDB
--   (collection: two_factor_auth). These PostgreSQL fields are no longer needed.
--   This migration removes: two_factor_enabled, two_factor_method, two_factor_secret,
--   two_factor_backup_codes, two_factor_enabled_at columns and related indexes.

-- Drop index first
DROP INDEX IF EXISTS idx_users_two_factor_enabled;

-- Drop columns
ALTER TABLE users DROP COLUMN IF EXISTS two_factor_enabled;

ALTER TABLE users DROP COLUMN IF EXISTS two_factor_method;

ALTER TABLE users DROP COLUMN IF EXISTS two_factor_secret;

ALTER TABLE users DROP COLUMN IF EXISTS two_factor_backup_codes;

ALTER TABLE users DROP COLUMN IF EXISTS two_factor_enabled_at;

-- Comments for documentation
COMMENT ON
TABLE users IS 'User table - 2FA data migrated to auth-service MongoDB (collection: two_factor_auth)';