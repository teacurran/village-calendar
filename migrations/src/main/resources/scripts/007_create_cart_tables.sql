-- //
-- Create cart tables for calendar shopping cart functionality
-- //

-- Create carts table
CREATE TABLE carts (
    id UUID PRIMARY KEY,
    created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 0,
    user_id UUID REFERENCES calendar_users(id),
    session_id VARCHAR(255)
);

CREATE INDEX idx_carts_user ON carts(user_id);
CREATE INDEX idx_carts_session ON carts(session_id);

-- Create cart_items table
CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 0,
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    template_id VARCHAR(255) NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    calendar_year INTEGER NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price NUMERIC(10,2) NOT NULL,
    configuration TEXT
);

CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);

-- //@UNDO

DROP INDEX IF EXISTS idx_cart_items_cart;
DROP TABLE IF EXISTS cart_items;
DROP INDEX IF EXISTS idx_carts_session;
DROP INDEX IF EXISTS idx_carts_user;
DROP TABLE IF EXISTS carts;
