-- //
-- Create delayed_jobs table for asynchronous job processing with retry logic
-- Jobs are executed via Vert.x EventBus with scheduled fallback
-- Requires: 000_enable_postgis.sql (for uuid-ossp extension)
--
-- ARCHITECTURAL NOTE:
-- This table supports the async job queue pattern for operations like:
-- - Order processing and fulfillment
-- - Calendar PDF generation
-- - Email notifications
-- - Analytics aggregation
-- //

-- Create delayed_jobs table
CREATE TABLE delayed_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    priority INTEGER NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0,
    queue VARCHAR(50) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    last_error TEXT,
    run_at TIMESTAMPTZ NOT NULL,
    locked BOOLEAN NOT NULL DEFAULT false,
    locked_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    complete BOOLEAN NOT NULL DEFAULT false,
    completed_at TIMESTAMPTZ,
    completed_with_failure BOOLEAN NOT NULL DEFAULT false,
    failure_reason TEXT,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Create composite index for efficient job queue queries
-- Index covers: queue selection → runnable jobs → not locked → ordered by priority/time
CREATE INDEX idx_delayed_jobs_queue_run_at ON delayed_jobs(queue, run_at, complete, locked);

-- Create index for finding locked jobs that need timeout recovery
CREATE INDEX idx_delayed_jobs_locked ON delayed_jobs(locked, locked_at) WHERE locked = true;

-- Create index for monitoring failed jobs
CREATE INDEX idx_delayed_jobs_failed ON delayed_jobs(failed_at DESC) WHERE failed_at IS NOT NULL;

-- Add comments for documentation
COMMENT ON TABLE delayed_jobs IS 'Asynchronous job queue with retry logic for background processing';
COMMENT ON COLUMN delayed_jobs.priority IS 'Job priority (higher values run first, default 0)';
COMMENT ON COLUMN delayed_jobs.attempts IS 'Number of execution attempts (incremented on retry)';
COMMENT ON COLUMN delayed_jobs.queue IS 'Job queue name (e.g., ORDER_PROCESSING, PDF_GENERATION)';
COMMENT ON COLUMN delayed_jobs.actor_id IS 'UUID of the entity this job operates on (e.g., CalendarOrder ID)';
COMMENT ON COLUMN delayed_jobs.last_error IS 'Error message from most recent failed attempt';
COMMENT ON COLUMN delayed_jobs.run_at IS 'Timestamp when job should be executed (enables scheduled jobs)';
COMMENT ON COLUMN delayed_jobs.locked IS 'Flag indicating job is currently being processed';
COMMENT ON COLUMN delayed_jobs.locked_at IS 'Timestamp when job was locked for processing';
COMMENT ON COLUMN delayed_jobs.failed_at IS 'Timestamp of permanent failure (after max retries)';
COMMENT ON COLUMN delayed_jobs.complete IS 'Flag indicating job has finished (success or failure)';
COMMENT ON COLUMN delayed_jobs.completed_at IS 'Timestamp when job completed';
COMMENT ON COLUMN delayed_jobs.completed_with_failure IS 'Flag indicating job completed but with failure';
COMMENT ON COLUMN delayed_jobs.failure_reason IS 'Final failure reason if job could not be completed';

-- //@UNDO

-- Drop indexes
DROP INDEX IF EXISTS idx_delayed_jobs_failed;
DROP INDEX IF EXISTS idx_delayed_jobs_locked;
DROP INDEX IF EXISTS idx_delayed_jobs_queue_run_at;

-- Drop table
DROP TABLE IF EXISTS delayed_jobs;
