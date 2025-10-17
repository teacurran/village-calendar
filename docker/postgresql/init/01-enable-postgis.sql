-- Enable PostGIS extension for Village Calendar
-- This script is automatically executed by PostgreSQL on database initialization
--
-- PostGIS provides geospatial data types and functions needed for:
-- - Future location-based features
-- - Shipping calculations
-- - Geographic queries
--
-- Note: This script is idempotent and can be safely run multiple times

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Enable PostGIS topology extension (optional, for advanced spatial features)
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- Verify PostGIS installation and log version
DO $$
BEGIN
    RAISE NOTICE 'PostGIS Version: %', PostGIS_Version();
END $$;

-- Create a simple test to ensure PostGIS is working correctly
DO $$
DECLARE
    test_distance DOUBLE PRECISION;
BEGIN
    -- Test basic PostGIS functionality
    test_distance := ST_Distance(ST_Point(0,0), ST_Point(1,1));
    RAISE NOTICE 'PostGIS functionality test: ST_Distance(Point(0,0), Point(1,1)) = %', test_distance;
END $$;

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'PostGIS initialization completed successfully';
END $$;
