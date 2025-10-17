-- //
-- Initial database schema for Village Calendar service
-- Creates calendar_users, calendar_templates, user_calendars, and calendar_orders tables
-- //

-- Enable UUID extension for PostgreSQL
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create calendar_users table for OAuth authenticated users
CREATE TABLE calendar_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    oauth_provider VARCHAR(50) NOT NULL,
    oauth_subject VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    profile_image_url VARCHAR(500),
    last_login_at TIMESTAMPTZ,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_calendar_users_oauth UNIQUE (oauth_provider, oauth_subject)
);

CREATE INDEX idx_calendar_users_email ON calendar_users(email);
CREATE INDEX idx_calendar_users_last_login ON calendar_users(last_login_at DESC);

-- Create calendar_templates table for reusable calendar designs
CREATE TABLE calendar_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    thumbnail_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_featured BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0,
    configuration JSONB NOT NULL,
    preview_svg TEXT,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_calendar_templates_name ON calendar_templates(name);
CREATE INDEX idx_calendar_templates_active ON calendar_templates(is_active, display_order, name);
CREATE INDEX idx_calendar_templates_featured ON calendar_templates(is_featured, is_active, display_order);
CREATE INDEX idx_calendar_templates_config_gin ON calendar_templates USING GIN(configuration);

-- Create user_calendars table linking users to templates with customizations
CREATE TABLE user_calendars (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID,
    session_id VARCHAR(255),
    template_id UUID,
    name VARCHAR(255) NOT NULL,
    year INTEGER NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT true,
    configuration JSONB,
    generated_svg TEXT,
    generated_pdf_url VARCHAR(500),
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_calendars_user FOREIGN KEY (user_id) REFERENCES calendar_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_calendars_template FOREIGN KEY (template_id) REFERENCES calendar_templates(id) ON DELETE SET NULL
);

CREATE INDEX idx_user_calendars_user ON user_calendars(user_id, year DESC);
CREATE INDEX idx_user_calendars_session ON user_calendars(session_id, updated DESC);
CREATE INDEX idx_user_calendars_template ON user_calendars(template_id);
CREATE INDEX idx_user_calendars_public ON user_calendars(is_public, updated DESC);

-- Create calendar_orders table for e-commerce functionality
CREATE TABLE calendar_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    calendar_id UUID NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    shipping_address JSONB,
    stripe_payment_intent_id VARCHAR(255),
    stripe_charge_id VARCHAR(255),
    notes TEXT,
    paid_at TIMESTAMPTZ,
    shipped_at TIMESTAMPTZ,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_calendar_orders_user FOREIGN KEY (user_id) REFERENCES calendar_users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_calendar_orders_calendar FOREIGN KEY (calendar_id) REFERENCES user_calendars(id) ON DELETE RESTRICT
);

CREATE INDEX idx_calendar_orders_user ON calendar_orders(user_id, created DESC);
CREATE INDEX idx_calendar_orders_status ON calendar_orders(status, created DESC);
CREATE INDEX idx_calendar_orders_calendar ON calendar_orders(calendar_id);
CREATE INDEX idx_calendar_orders_stripe_payment ON calendar_orders(stripe_payment_intent_id);

-- Add comments for documentation
COMMENT ON TABLE calendar_users IS 'OAuth authenticated users for the calendar service';
COMMENT ON TABLE calendar_templates IS 'Reusable calendar design templates with JSONB configuration';
COMMENT ON TABLE user_calendars IS 'User-created calendars with customizations, supports both authenticated users and anonymous sessions';
COMMENT ON TABLE calendar_orders IS 'E-commerce orders for printed calendars with Stripe payment integration';

COMMENT ON COLUMN calendar_users.oauth_provider IS 'OAuth provider (GOOGLE, FACEBOOK)';
COMMENT ON COLUMN calendar_users.oauth_subject IS 'Unique identifier from OAuth provider (sub claim)';
COMMENT ON COLUMN calendar_templates.configuration IS 'JSONB field for template configuration (colors, layout, features)';
COMMENT ON COLUMN user_calendars.user_id IS 'Reference to authenticated user (nullable for anonymous sessions)';
COMMENT ON COLUMN user_calendars.session_id IS 'Session identifier for anonymous users';
COMMENT ON COLUMN user_calendars.year IS 'Calendar year (e.g., 2025)';
COMMENT ON COLUMN calendar_orders.status IS 'Order status: PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED';
COMMENT ON COLUMN calendar_orders.shipping_address IS 'JSONB field for shipping address details';

-- //@UNDO

-- Drop tables in reverse order due to foreign key constraints
DROP TABLE IF EXISTS calendar_orders;
DROP TABLE IF EXISTS user_calendars;
DROP TABLE IF EXISTS calendar_templates;
DROP TABLE IF EXISTS calendar_users;

-- Drop UUID extension (only if not used by other schemas)
-- DROP EXTENSION IF EXISTS "uuid-ossp";
