--
-- Create changelog table and enable PostgreSQL extensions
--
-- // create changelog table

-- Create the schema_version (changelog) table for tracking migrations
CREATE TABLE ${changelog} (
  ID NUMERIC(20,0) NOT NULL,
  APPLIED_AT VARCHAR(25) NOT NULL,
  DESCRIPTION VARCHAR(255) NOT NULL
);

ALTER TABLE ${changelog}
  ADD CONSTRAINT PK_${changelog}
  PRIMARY KEY (ID);

-- Verify extensions exist (will succeed if already created by superuser)
-- NOTE: Extensions must be created by a superuser BEFORE running migrations.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- //@UNDO

DROP TABLE ${changelog};
