-- //
-- Add is_admin field to calendar_users table to support admin role
-- This enables the bootstrap functionality for creating the first admin user
-- //

-- Add is_admin column to calendar_users table
ALTER TABLE calendar_users
ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT false;

-- Create index for quickly finding admin users
CREATE INDEX idx_calendar_users_admin ON calendar_users(is_admin) WHERE is_admin = true;

-- Add comment for documentation
COMMENT ON COLUMN calendar_users.is_admin IS 'Flag indicating whether user has admin privileges';

-- //@UNDO

-- Remove the index
DROP INDEX IF EXISTS idx_calendar_users_admin;

-- Remove the is_admin column
ALTER TABLE calendar_users
DROP COLUMN IF EXISTS is_admin;
