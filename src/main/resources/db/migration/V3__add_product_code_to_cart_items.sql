-- Add product_code column to cart_items table
ALTER TABLE cart_items ADD COLUMN product_code VARCHAR(50);

-- Update existing items to use default product code
UPDATE cart_items SET product_code = 'print' WHERE product_code IS NULL;
