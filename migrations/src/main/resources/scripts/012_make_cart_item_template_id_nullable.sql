-- Allow cart items without template ID (for static product page purchases)
-- These items have their full configuration stored instead of referencing a template
ALTER TABLE cart_items ALTER COLUMN template_id DROP NOT NULL;
