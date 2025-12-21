-- //
-- Make legacy cart_items fields nullable for non-calendar generators
-- template_name and calendar_year are only used for legacy calendar items
-- //

-- Make template_name nullable (it's deprecated, use description instead)
ALTER TABLE cart_items ALTER COLUMN template_name DROP NOT NULL;

-- Make calendar_year nullable (it's deprecated, use configuration JSON instead)
ALTER TABLE cart_items ALTER COLUMN calendar_year DROP NOT NULL;

-- //@UNDO

ALTER TABLE cart_items ALTER COLUMN template_name SET NOT NULL;
ALTER TABLE cart_items ALTER COLUMN calendar_year SET NOT NULL;
