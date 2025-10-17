-- //
-- Enable PostgreSQL extensions required for Village Calendar application
-- This migration must run before any schema migrations
--
-- Extensions enabled:
-- 1. uuid-ossp: Provides UUID generation functions (uuid_generate_v4())
-- 2. postgis: Provides geospatial data types and functions for location-based features
-- 3. postgis_topology: Provides topology data types and functions (advanced spatial operations)
--
-- IMPORTANT: Requires PostgreSQL superuser privileges or rds_superuser role (AWS RDS)
-- //

-- Enable UUID extension (required for uuid_generate_v4() function used in all table primary keys)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enable PostGIS extension (required for future geospatial features and astronomical calculations)
CREATE EXTENSION IF NOT EXISTS postgis;

-- Enable PostGIS topology extension (for advanced spatial relationships and operations)
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- Add extension metadata comments
COMMENT ON EXTENSION "uuid-ossp" IS 'Provides UUID generation functions for primary keys';
COMMENT ON EXTENSION postgis IS 'PostGIS geometry, geography, and raster spatial types and functions';
COMMENT ON EXTENSION postgis_topology IS 'PostGIS topology spatial types and functions';

-- //@UNDO

-- Drop PostGIS extensions in reverse dependency order
DROP EXTENSION IF EXISTS postgis_topology;
DROP EXTENSION IF EXISTS postgis;
DROP EXTENSION IF EXISTS "uuid-ossp";
