-- V003__add_user_profile_features.sql
-- Add User Profile features: Avatar, 2FA, Settings, Account Management

-- Avatar Management
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);

ALTER TABLE users
ADD COLUMN IF NOT EXISTS avatar_public_id VARCHAR(255);

ALTER TABLE users
ADD COLUMN IF NOT EXISTS avatar_uploaded_at TIMESTAMP;

-- Two-Factor Authentication
ALTER TABLE users
ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT FALSE;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS two_factor_method VARCHAR(20);
-- 'TOTP' or 'EMAIL'
ALTER TABLE users
ADD COLUMN IF NOT EXISTS two_factor_secret VARCHAR(255);

ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_backup_codes TEXT[];
-- Array of backup codes
ALTER TABLE users
ADD COLUMN IF NOT EXISTS two_factor_enabled_at TIMESTAMP;

-- Notification Settings (JSONB for flexibility)
ALTER TABLE users ADD COLUMN IF NOT EXISTS notification_settings JSONB DEFAULT '{
  "email": {
    "enabled": true,
    "applicationUpdates": true,
    "jobMatches": true,
    "messages": true,
    "newsletter": false
  },
  "push": {
    "enabled": false,
    "applicationUpdates": false,
    "jobMatches": false,
    "messages": false
  },
  "sms": {
    "enabled": false,
    "applicationUpdates": false,
    "jobMatches": false
  }
}'::jsonb;

-- Privacy Settings (JSONB for flexibility)
ALTER TABLE users ADD COLUMN IF NOT EXISTS privacy_settings JSONB DEFAULT '{
  "profile": {
    "visibility": "public",
    "showEmail": false,
    "showPhone": false
  },
  "cv": {
    "allowDownload": true,
    "showToRecruiters": true
  },
  "activity": {
    "showLastActive": true,
    "showApplicationHistory": false
  }
}'::jsonb;

-- Account Management
ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP;

ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivation_reason TEXT;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS deletion_scheduled_at TIMESTAMP;

ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_date DATE;

ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_avatar_public_id ON users (avatar_public_id);

CREATE INDEX IF NOT EXISTS idx_users_two_factor_enabled ON users (two_factor_enabled)
WHERE
    two_factor_enabled = TRUE;

CREATE INDEX IF NOT EXISTS idx_users_deactivated_at ON users (deactivated_at)
WHERE
    deactivated_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_deletion_scheduled_at ON users (deletion_scheduled_at)
WHERE
    deletion_scheduled_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_deleted_at ON users (deleted_at)
WHERE
    deleted_at IS NOT NULL;

-- Add comments for documentation
COMMENT ON COLUMN users.avatar_url IS 'Cloudinary URL for user avatar';

COMMENT ON COLUMN users.avatar_public_id IS 'Cloudinary public ID for avatar management';

COMMENT ON COLUMN users.two_factor_enabled IS 'Whether 2FA is enabled for this user';

COMMENT ON COLUMN users.two_factor_method IS 'Method: TOTP (Google Authenticator) or EMAIL';

COMMENT ON COLUMN users.two_factor_secret IS 'Secret key for TOTP generation';

COMMENT ON COLUMN users.two_factor_backup_codes IS 'Array of one-time backup codes';

COMMENT ON COLUMN users.notification_settings IS 'JSON object for email/push/sms notification preferences';

COMMENT ON COLUMN users.privacy_settings IS 'JSON object for profile/cv/activity privacy controls';

COMMENT ON COLUMN users.deactivated_at IS 'Timestamp when account was deactivated (30 days retention)';

COMMENT ON COLUMN users.deletion_scheduled_at IS 'Timestamp when deletion was requested';

COMMENT ON COLUMN users.deletion_date IS 'Scheduled date for permanent deletion (7 days grace period)';

COMMENT ON COLUMN users.deleted_at IS 'Timestamp of permanent deletion';