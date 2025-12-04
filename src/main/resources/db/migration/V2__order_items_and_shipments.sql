-- V2: Add order items and shipments support
-- This migration enables orders with multiple line items and shipment tracking

-- 1. Make calendar_id nullable in calendar_orders (new orders use items, not single calendar)
ALTER TABLE calendar_orders ALTER COLUMN calendar_id DROP NOT NULL;

-- 2. Add new columns to calendar_orders
ALTER TABLE calendar_orders ADD COLUMN IF NOT EXISTS customer_email VARCHAR(255);
ALTER TABLE calendar_orders ADD COLUMN IF NOT EXISTS billing_address JSONB;
ALTER TABLE calendar_orders ADD COLUMN IF NOT EXISTS subtotal DECIMAL(10,2);
ALTER TABLE calendar_orders ADD COLUMN IF NOT EXISTS shipping_cost DECIMAL(10,2);
ALTER TABLE calendar_orders ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(10,2);

-- Make quantity and unit_price nullable (new orders store these on items)
ALTER TABLE calendar_orders ALTER COLUMN quantity DROP NOT NULL;
ALTER TABLE calendar_orders ALTER COLUMN unit_price DROP NOT NULL;

-- 3. Create shipments table
CREATE TABLE IF NOT EXISTS shipments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES calendar_orders(id),
    carrier VARCHAR(50),
    tracking_number VARCHAR(255),
    tracking_url VARCHAR(500),
    status VARCHAR(50) DEFAULT 'PENDING',
    label_created_at TIMESTAMP WITH TIME ZONE,
    shipped_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_shipments_order ON shipments(order_id);
CREATE INDEX IF NOT EXISTS idx_shipments_tracking ON shipments(tracking_number);
CREATE INDEX IF NOT EXISTS idx_shipments_status ON shipments(status);

-- 4. Create calendar_order_items table
CREATE TABLE IF NOT EXISTS calendar_order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES calendar_orders(id),
    calendar_id UUID REFERENCES user_calendars(id),
    product_type VARCHAR(50) NOT NULL DEFAULT 'PRINT',
    product_name VARCHAR(255),
    calendar_year INTEGER,
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) NOT NULL,
    configuration JSONB,
    shipment_id UUID REFERENCES shipments(id),
    item_status VARCHAR(50) DEFAULT 'PENDING',
    created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON calendar_order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_calendar ON calendar_order_items(calendar_id);
CREATE INDEX IF NOT EXISTS idx_order_items_shipment ON calendar_order_items(shipment_id);

-- 5. Migrate existing orders to have items (for any orders that have calendar_id set)
-- This creates an order item for each existing order that has a calendar
INSERT INTO calendar_order_items (id, order_id, calendar_id, product_type, product_name, calendar_year, quantity, unit_price, line_total, item_status, created, updated, version)
SELECT
    gen_random_uuid(),
    co.id,
    co.calendar_id,
    'PRINT',
    uc.name,
    uc.year,
    COALESCE(co.quantity, 1),
    COALESCE(co.unit_price, 29.99),
    COALESCE(co.total_price, 29.99),
    CASE
        WHEN co.status = 'DELIVERED' THEN 'DELIVERED'
        WHEN co.status = 'SHIPPED' THEN 'SHIPPED'
        WHEN co.status = 'CANCELLED' THEN 'CANCELLED'
        ELSE 'PENDING'
    END,
    co.created,
    co.updated,
    0
FROM calendar_orders co
JOIN user_calendars uc ON co.calendar_id = uc.id
WHERE co.calendar_id IS NOT NULL
AND NOT EXISTS (
    SELECT 1 FROM calendar_order_items coi WHERE coi.order_id = co.id
);

-- 6. Update customer_email from user for existing orders
UPDATE calendar_orders co
SET customer_email = cu.email
FROM calendar_users cu
WHERE co.user_id = cu.id
AND co.customer_email IS NULL;

-- 7. Set subtotal from total_price for existing orders that don't have it
UPDATE calendar_orders
SET subtotal = total_price
WHERE subtotal IS NULL AND total_price IS NOT NULL;
