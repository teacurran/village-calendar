-- Allow guest checkout by making user_id nullable on calendar_orders
ALTER TABLE calendar_orders ALTER COLUMN user_id DROP NOT NULL;
