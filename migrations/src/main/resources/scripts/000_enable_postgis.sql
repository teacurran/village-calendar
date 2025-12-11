-- //
-- Enable PostgreSQL extensions required for Village Calendar application
-- This migration must run before any schema migrations
--
-- Extensions enabled:
-- 1. uuid-ossp: Provides UUID generation functions (uuid_generate_v4())
-- 2. postgis: Provides geospatial data types and functions for location-based features
-- 3. postgis_topology: Provides topology data types and functions (advanced spatial operations)
--
-- NOTE: Extensions must be created by a superuser BEFORE running migrations.
-- This script only verifies they exist and will fail if they don't.
-- Run as superuser first:
--   CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
--   CREATE EXTENSION IF NOT EXISTS postgis;
--   CREATE EXTENSION IF NOT EXISTS postgis_topology;
-- //

-- Verify extensions exist (will succeed if already created by superuser)
-- These use IF NOT EXISTS so they're idempotent
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- //@UNDO

-- Note: Extensions should not be dropped as they may be shared
-- and require superuser to drop
SELECT 1;
