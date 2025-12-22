-- //
-- Rename delayed_jobs.queue column to queue_name
-- The new queue_name stores handler class simple names instead of enum values
-- //

-- Drop the old index
DROP INDEX IF EXISTS idx_delayed_jobs_queue_run_at;

-- Rename the column
ALTER TABLE delayed_jobs RENAME COLUMN queue TO queue_name;

-- Increase column size to accommodate handler class names
ALTER TABLE delayed_jobs ALTER COLUMN queue_name TYPE VARCHAR(100);

-- Migrate existing data from enum values to handler class names
UPDATE delayed_jobs SET queue_name = 'OrderEmailJobHandler' WHERE queue_name = 'EMAIL_ORDER_CONFIRMATION';
UPDATE delayed_jobs SET queue_name = 'ShippingNotificationJobHandler' WHERE queue_name = 'EMAIL_SHIPPING_NOTIFICATION';
UPDATE delayed_jobs SET queue_name = 'OrderCancellationJobHandler' WHERE queue_name = 'EMAIL_GENERAL';

-- Create new index with renamed column
CREATE INDEX idx_delayed_jobs_queue_name_run_at ON delayed_jobs(queue_name, run_at, complete, locked);

-- Update column comment
COMMENT ON COLUMN delayed_jobs.queue_name IS 'Handler class simple name (e.g., OrderEmailJobHandler)';

-- //@UNDO

-- Drop new index
DROP INDEX IF EXISTS idx_delayed_jobs_queue_name_run_at;

-- Migrate data back to enum values
UPDATE delayed_jobs SET queue_name = 'EMAIL_ORDER_CONFIRMATION' WHERE queue_name = 'OrderEmailJobHandler';
UPDATE delayed_jobs SET queue_name = 'EMAIL_SHIPPING_NOTIFICATION' WHERE queue_name = 'ShippingNotificationJobHandler';
UPDATE delayed_jobs SET queue_name = 'EMAIL_GENERAL' WHERE queue_name = 'OrderCancellationJobHandler';

-- Reduce column size back
ALTER TABLE delayed_jobs ALTER COLUMN queue_name TYPE VARCHAR(50);

-- Rename column back
ALTER TABLE delayed_jobs RENAME COLUMN queue_name TO queue;

-- Recreate original index
CREATE INDEX idx_delayed_jobs_queue_run_at ON delayed_jobs(queue, run_at, complete, locked);

-- Restore original comment
COMMENT ON COLUMN delayed_jobs.queue IS 'Job queue name (e.g., ORDER_PROCESSING, PDF_GENERATION)';
