-- //
-- Create analytics_rollups table for aggregated metrics
-- Pre-computed analytics for fast dashboard queries and reporting
-- Requires: 000_enable_postgis.sql (for uuid-ossp extension)
--
-- ARCHITECTURAL NOTE:
-- This table supports analytics features planned for iteration I5:
-- - Pre-aggregated metrics (daily/weekly/monthly rollups)
-- - Fast dashboard queries (avoid scanning page_views/orders tables)
-- - Multi-dimensional analysis (metric + dimension slicing)
-- - Time-series data for trend visualization
--
-- Example metrics:
-- - metric_name: "page_views", dimension_key: "path", dimension_value: "/templates"
-- - metric_name: "revenue", dimension_key: "status", dimension_value: "PAID"
-- - metric_name: "conversions", dimension_key: "template_id", dimension_value: "<uuid>"
-- //

-- Create analytics_rollups table
CREATE TABLE analytics_rollups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    metric_name VARCHAR(255) NOT NULL,
    dimension_key VARCHAR(255),
    dimension_value VARCHAR(500),
    value DECIMAL(15, 2) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_analytics_rollups_period CHECK (period_end > period_start)
);

-- Create composite index for metric queries with dimension filtering
CREATE INDEX idx_analytics_rollups_metric ON analytics_rollups(metric_name, period_start, dimension_key);

-- Create index for time-range queries (dashboard date pickers)
CREATE INDEX idx_analytics_rollups_period ON analytics_rollups(period_start, period_end);

-- Create unique constraint to prevent duplicate rollups
CREATE UNIQUE INDEX uk_analytics_rollups_unique ON analytics_rollups(
    metric_name,
    COALESCE(dimension_key, ''),
    COALESCE(dimension_value, ''),
    period_start,
    period_end
);

-- Add comments for documentation
COMMENT ON TABLE analytics_rollups IS 'Pre-computed analytics aggregations for fast dashboard queries';
COMMENT ON COLUMN analytics_rollups.metric_name IS 'Name of the metric being tracked (e.g., page_views, revenue, conversions)';
COMMENT ON COLUMN analytics_rollups.dimension_key IS 'Dimension category for slicing (e.g., path, template_id, status)';
COMMENT ON COLUMN analytics_rollups.dimension_value IS 'Specific dimension value (e.g., /templates, <uuid>, PAID)';
COMMENT ON COLUMN analytics_rollups.value IS 'Aggregated metric value (count, sum, average, etc.)';
COMMENT ON COLUMN analytics_rollups.period_start IS 'Start of aggregation period (inclusive)';
COMMENT ON COLUMN analytics_rollups.period_end IS 'End of aggregation period (exclusive)';

-- //@UNDO

-- Drop indexes and constraints
DROP INDEX IF EXISTS uk_analytics_rollups_unique;
DROP INDEX IF EXISTS idx_analytics_rollups_period;
DROP INDEX IF EXISTS idx_analytics_rollups_metric;

-- Drop table
DROP TABLE IF EXISTS analytics_rollups;
