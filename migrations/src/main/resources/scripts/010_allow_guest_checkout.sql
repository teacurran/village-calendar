-- //
-- Allow guest checkout by making user_id nullable on calendar_orders
-- //

ALTER TABLE calendar_orders ALTER COLUMN user_id DROP NOT NULL;

-- //@UNDO

-- Note: Cannot easily undo this as existing data may have NULL user_ids
-- ALTER TABLE calendar_orders ALTER COLUMN user_id SET NOT NULL;
