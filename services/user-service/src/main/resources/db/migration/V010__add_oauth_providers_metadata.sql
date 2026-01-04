-- Add OAuth providers metadata column to users table
-- This stores list of linked OAuth providers (GOOGLE, GITHUB) as JSONB
-- Auth-service publishes events when users link/unlink OAuth accounts
-- User-service consumes these events and updates this metadata

ALTER TABLE users ADD COLUMN oauth_providers JSONB;

-- Create index for querying users by OAuth provider
CREATE INDEX idx_users_oauth_providers ON users USING GIN (oauth_providers);

-- Comment explaining the data structure
COMMENT ON COLUMN users.oauth_providers IS 'Array of linked OAuth providers with metadata: [{"provider":"GOOGLE","email":"user@gmail.com","linkedAt":"2024-01-01T00:00:00Z"}]';