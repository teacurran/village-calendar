-- //
-- Create page_views table for analytics tracking
-- Captures user navigation patterns and behavior for product insights
-- Requires: 000_enable_postgis.sql (for uuid-ossp extension), 001_initial_schema.sql (for calendar_users table)
--
-- ARCHITECTURAL NOTE:
-- This table supports analytics features planned for iteration I4:
-- - User behavior tracking (anonymous and authenticated)
-- - Funnel analysis (template browsing → customization → checkout)
-- - Session reconstruction via session_id
-- - Referral source tracking
-- //

-- Create page_views table
CREATE TABLE page_views (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id VARCHAR(255) NOT NULL,
    user_id UUID,
    path VARCHAR(500) NOT NULL,
    referrer VARCHAR(500),
    user_agent VARCHAR(1000),
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_page_views_user FOREIGN KEY (user_id) REFERENCES calendar_users(id) ON DELETE SET NULL
);

-- Create index for session-based analysis (funnel reconstruction)
CREATE INDEX idx_page_views_session ON page_views(session_id, created DESC);

-- Create index for user behavior tracking (authenticated users)
CREATE INDEX idx_page_views_user ON page_views(user_id, created DESC) WHERE user_id IS NOT NULL;

-- Create index for path analysis (popular pages, conversion paths)
CREATE INDEX idx_page_views_path ON page_views(path, created DESC);

-- Create index for time-based analytics queries
CREATE INDEX idx_page_views_created ON page_views(created DESC);

-- Add comments for documentation
COMMENT ON TABLE page_views IS 'Analytics tracking for user navigation and behavior analysis';
COMMENT ON COLUMN page_views.session_id IS 'Session identifier (links anonymous and authenticated activity)';
COMMENT ON COLUMN page_views.user_id IS 'Reference to authenticated user (nullable for anonymous visitors, ON DELETE SET NULL)';
COMMENT ON COLUMN page_views.path IS 'URL path visited (e.g., /templates, /calendar/123/edit)';
COMMENT ON COLUMN page_views.referrer IS 'HTTP Referer header (traffic source attribution)';
COMMENT ON COLUMN page_views.user_agent IS 'Browser User-Agent string (device/browser analysis)';

-- //@UNDO

-- Drop indexes
DROP INDEX IF EXISTS idx_page_views_created;
DROP INDEX IF EXISTS idx_page_views_path;
DROP INDEX IF EXISTS idx_page_views_user;
DROP INDEX IF EXISTS idx_page_views_session;

-- Drop table
DROP TABLE IF EXISTS page_views;
