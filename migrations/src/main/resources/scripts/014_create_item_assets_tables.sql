-- //
-- Create item_assets table for storing generated SVGs
-- This supports both calendars and mazes (and future generators)
-- //

-- Create item_assets table for storing generated SVG content
CREATE TABLE item_assets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    asset_key VARCHAR(50) NOT NULL,  -- 'main', 'answer_key', 'thumbnail', etc.
    content_type VARCHAR(50) NOT NULL DEFAULT 'image/svg+xml',
    svg_content TEXT NOT NULL,
    width_inches NUMERIC(5,2),
    height_inches NUMERIC(5,2),
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE item_assets IS 'Stores generated SVG content for cart items and orders';
COMMENT ON COLUMN item_assets.asset_key IS 'Key identifying the asset type: main, answer_key, thumbnail, etc.';
COMMENT ON COLUMN item_assets.content_type IS 'MIME type of the content, typically image/svg+xml';
COMMENT ON COLUMN item_assets.svg_content IS 'The actual SVG content';
COMMENT ON COLUMN item_assets.width_inches IS 'Width of the asset in inches (e.g., 35 for poster)';
COMMENT ON COLUMN item_assets.height_inches IS 'Height of the asset in inches (e.g., 23 for poster)';

-- Junction table for cart_items to item_assets (many-to-many)
CREATE TABLE cart_item_assets (
    cart_item_id UUID NOT NULL REFERENCES cart_items(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES item_assets(id) ON DELETE CASCADE,
    PRIMARY KEY (cart_item_id, asset_id)
);

CREATE INDEX idx_cart_item_assets_cart_item ON cart_item_assets(cart_item_id);
CREATE INDEX idx_cart_item_assets_asset ON cart_item_assets(asset_id);

-- Junction table for order_items to item_assets (many-to-many)
CREATE TABLE order_item_assets (
    order_item_id UUID NOT NULL REFERENCES calendar_order_items(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES item_assets(id) ON DELETE CASCADE,
    PRIMARY KEY (order_item_id, asset_id)
);

CREATE INDEX idx_order_item_assets_order_item ON order_item_assets(order_item_id);
CREATE INDEX idx_order_item_assets_asset ON order_item_assets(asset_id);

-- Add generator_type and description columns to cart_items
ALTER TABLE cart_items ADD COLUMN generator_type VARCHAR(50);
ALTER TABLE cart_items ADD COLUMN description VARCHAR(500);

COMMENT ON COLUMN cart_items.generator_type IS 'Type of generator: calendar, maze, etc.';
COMMENT ON COLUMN cart_items.description IS 'User-facing description like "2026 Calendar" or "Hard Orthogonal Maze"';

-- Add generator_type and description columns to calendar_order_items
ALTER TABLE calendar_order_items ADD COLUMN generator_type VARCHAR(50);
ALTER TABLE calendar_order_items ADD COLUMN description VARCHAR(500);

COMMENT ON COLUMN calendar_order_items.generator_type IS 'Type of generator: calendar, maze, etc.';
COMMENT ON COLUMN calendar_order_items.description IS 'User-facing description like "2026 Calendar" or "Hard Orthogonal Maze"';

-- //@UNDO

DROP TABLE IF EXISTS order_item_assets;
DROP TABLE IF EXISTS cart_item_assets;
DROP TABLE IF EXISTS item_assets;

ALTER TABLE cart_items DROP COLUMN IF EXISTS generator_type;
ALTER TABLE cart_items DROP COLUMN IF EXISTS description;

ALTER TABLE calendar_order_items DROP COLUMN IF EXISTS generator_type;
ALTER TABLE calendar_order_items DROP COLUMN IF EXISTS description;
