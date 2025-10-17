# Village Calendar Database Setup Guide

This guide provides step-by-step instructions for setting up PostgreSQL 17+ with PostGIS for the Village Calendar application.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [PostgreSQL Installation](#postgresql-installation)
3. [Database Creation](#database-creation)
4. [PostGIS Extension Setup](#postgis-extension-setup)
5. [User and Permissions Configuration](#user-and-permissions-configuration)
6. [Connection Verification](#connection-verification)
7. [Environment Variable Configuration](#environment-variable-configuration)
8. [Docker Compose Setup](#docker-compose-setup)
9. [Running Database Migrations](#running-database-migrations)
10. [Troubleshooting](#troubleshooting)

---

## Prerequisites

- PostgreSQL 17 or higher
- PostGIS extension (version compatible with PostgreSQL 17)
- Administrative (superuser) access to PostgreSQL
- Java 21+ (for running the Quarkus application)
- Maven 3.8+ (for building and running migrations)

---

## PostgreSQL Installation

### macOS (Homebrew)

```bash
# Install PostgreSQL 17
brew install postgresql@17

# Install PostGIS
brew install postgis

# Start PostgreSQL service
brew services start postgresql@17

# Add PostgreSQL to PATH (add to ~/.zshrc or ~/.bashrc)
echo 'export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### macOS (Postgres.app)

1. Download Postgres.app from https://postgresapp.com/
2. Choose the version with PostgreSQL 17 and PostGIS included
3. Drag to Applications folder and launch
4. Click "Initialize" to create a new server

### Linux (Ubuntu/Debian)

```bash
# Add PostgreSQL APT repository
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -

# Update package list
sudo apt-get update

# Install PostgreSQL 17 and PostGIS
sudo apt-get install -y postgresql-17 postgresql-17-postgis-3

# Start PostgreSQL service
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

### Linux (Red Hat/CentOS/Fedora)

```bash
# Add PostgreSQL YUM repository
sudo dnf install -y https://download.postgresql.org/pub/repos/yum/reporpms/EL-9-x86_64/pgdg-redhat-repo-latest.noarch.rpm

# Install PostgreSQL 17 and PostGIS
sudo dnf install -y postgresql17-server postgresql17-contrib postgis34_17

# Initialize database
sudo /usr/pgsql-17/bin/postgresql-17-setup initdb

# Start and enable service
sudo systemctl start postgresql-17
sudo systemctl enable postgresql-17
```

### Windows

1. Download PostgreSQL 17 installer from https://www.postgresql.org/download/windows/
2. Run the installer and follow the wizard
3. Install PostGIS using the Stack Builder tool (included with PostgreSQL installer)
4. Or download PostGIS separately from https://postgis.net/windows_downloads/

---

## Database Creation

### Create the Calendar Database

Connect to PostgreSQL as a superuser (default user is `postgres`):

```bash
# macOS/Linux
psql -U postgres

# Or if using peer authentication (Linux)
sudo -u postgres psql
```

Create the database and calendar user:

```sql
-- Create the calendar database
CREATE DATABASE calendar
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    TEMPLATE = template0;

-- Connect to the calendar database
\c calendar

-- Create the calendar application user
CREATE USER calendar WITH PASSWORD 'calendar';

-- Grant privileges to the calendar user
GRANT ALL PRIVILEGES ON DATABASE calendar TO calendar;

-- Grant schema privileges (PostgreSQL 15+)
GRANT ALL ON SCHEMA public TO calendar;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO calendar;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO calendar;

-- Set default privileges for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO calendar;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO calendar;
```

---

## PostGIS Extension Setup

PostGIS provides geospatial capabilities for PostgreSQL. The Village Calendar application requires this extension for future location-based features.

**IMPORTANT:** Installing PostGIS requires superuser privileges.

### Enable PostGIS Extension

While connected to the `calendar` database as a superuser:

```sql
-- Connect to calendar database as superuser
\c calendar postgres

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Enable PostGIS topology extension (optional, for advanced geospatial features)
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- Verify PostGIS installation
SELECT PostGIS_Version();

-- Expected output: something like "3.4 USE_GEOS=1 USE_PROJ=1 ..."
```

### Verify PostGIS Functions

```sql
-- List PostGIS functions (should show hundreds of spatial functions)
\df *ST_*

-- Test a simple spatial query
SELECT ST_AsText(ST_GeomFromText('POINT(-122.4194 37.7749)', 4326));
-- Expected output: "POINT(-122.4194 37.7749)"
```

---

## User and Permissions Configuration

### Development Environment

For local development, the default credentials are:

- **Database:** `calendar`
- **Username:** `calendar`
- **Password:** `calendar`
- **Host:** `localhost`
- **Port:** `5532` (non-standard port used in Docker Compose)

### Production Environment

For production, you should:

1. Use strong, randomly generated passwords
2. Restrict database user permissions to only what's needed
3. Use connection pooling
4. Enable SSL/TLS connections

```sql
-- Create production user with restricted privileges
CREATE USER calendar_prod WITH PASSWORD 'STRONG_RANDOM_PASSWORD_HERE';

-- Grant only necessary privileges
GRANT CONNECT ON DATABASE calendar_production TO calendar_prod;
GRANT USAGE ON SCHEMA public TO calendar_prod;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO calendar_prod;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO calendar_prod;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO calendar_prod;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO calendar_prod;
```

---

## Connection Verification

### Using psql Command Line

```bash
# Connect to the calendar database
psql -h localhost -p 5532 -U calendar -d calendar

# If successful, you should see:
# Password for user calendar:
# psql (17.x)
# Type "help" for help.
#
# calendar=>
```

### Test Basic Operations

```sql
-- Create a test table
CREATE TABLE test_connection (
    id SERIAL PRIMARY KEY,
    message TEXT
);

-- Insert test data
INSERT INTO test_connection (message) VALUES ('Database connection works!');

-- Query test data
SELECT * FROM test_connection;

-- Clean up
DROP TABLE test_connection;
```

### Test PostGIS

```sql
-- Create a test spatial table
CREATE TABLE test_spatial (
    id SERIAL PRIMARY KEY,
    name TEXT,
    location GEOMETRY(Point, 4326)
);

-- Insert test spatial data (San Francisco coordinates)
INSERT INTO test_spatial (name, location)
VALUES ('San Francisco', ST_SetSRID(ST_MakePoint(-122.4194, 37.7749), 4326));

-- Query spatial data
SELECT id, name, ST_AsText(location) FROM test_spatial;

-- Clean up
DROP TABLE test_spatial;
```

---

## Environment Variable Configuration

The Village Calendar application supports environment variable overrides for database configuration.

### Supported Environment Variables

- `DB_URL` - JDBC connection URL (default: `jdbc:postgresql://localhost:5532/calendar`)
- `DB_USERNAME` - Database username (default: `calendar`)
- `DB_PASSWORD` - Database password (default: `calendar`)

### Setting Environment Variables

#### macOS/Linux (bash/zsh)

```bash
# For current terminal session
export DB_URL="jdbc:postgresql://localhost:5532/calendar"
export DB_USERNAME="calendar"
export DB_PASSWORD="calendar"

# For permanent configuration, add to ~/.bashrc or ~/.zshrc
echo 'export DB_URL="jdbc:postgresql://localhost:5532/calendar"' >> ~/.zshrc
echo 'export DB_USERNAME="calendar"' >> ~/.zshrc
echo 'export DB_PASSWORD="calendar"' >> ~/.zshrc
source ~/.zshrc
```

#### Windows (PowerShell)

```powershell
# For current session
$env:DB_URL = "jdbc:postgresql://localhost:5532/calendar"
$env:DB_USERNAME = "calendar"
$env:DB_PASSWORD = "calendar"

# For permanent configuration (requires admin privileges)
[System.Environment]::SetEnvironmentVariable("DB_URL", "jdbc:postgresql://localhost:5532/calendar", "User")
[System.Environment]::SetEnvironmentVariable("DB_USERNAME", "calendar", "User")
[System.Environment]::SetEnvironmentVariable("DB_PASSWORD", "calendar", "User")
```

#### Docker/Kubernetes

```yaml
# docker-compose.yml example
environment:
  - DB_URL=jdbc:postgresql://postgres-host:5432/calendar
  - DB_USERNAME=calendar
  - DB_PASSWORD=secretpassword

# Kubernetes Secret example
apiVersion: v1
kind: Secret
metadata:
  name: calendar-db-credentials
type: Opaque
stringData:
  DB_URL: jdbc:postgresql://postgres-service:5432/calendar
  DB_USERNAME: calendar
  DB_PASSWORD: secretpassword
```

---

## Docker Compose Setup

The project includes a Docker Compose configuration for local development.

### Start PostgreSQL with Docker Compose

```bash
# From the project root directory
docker-compose up -d postgres

# View logs
docker-compose logs -f postgres

# Stop PostgreSQL
docker-compose down
```

### Docker Compose Configuration

The `docker-compose.yml` file configures PostgreSQL with:

- PostgreSQL 17 with PostGIS
- Port mapping: `5532:5432` (host:container)
- Database: `calendar`
- Username: `calendar`
- Password: `calendar`
- Persistent volume for data

---

## Running Database Migrations

The Village Calendar application uses MyBatis Migrations for database schema management.

### Initial Migration

```bash
# Navigate to the migrations directory
cd migrations

# Run pending migrations (development environment)
mvn mybatis-migrations:up

# Check migration status
mvn mybatis-migrations:status
```

### Available MyBatis Migrations Commands

```bash
# Check current migration status
mvn mybatis-migrations:status

# Run all pending migrations
mvn mybatis-migrations:up

# Rollback last migration
mvn mybatis-migrations:down

# Create a new migration script
mvn mybatis-migrations:new -Dmybatis.migration.description="add_new_feature"

# Run migrations for production environment
mvn mybatis-migrations:up -Dmybatis.migration.env=production
```

### Verifying Migrations

After running migrations, verify the schema:

```sql
-- Connect to database
psql -h localhost -p 5532 -U calendar -d calendar

-- Check migration history
SELECT * FROM schema_version ORDER BY applied_on DESC;

-- List all tables
\dt

-- Describe a specific table
\d calendar_users
```

---

## Troubleshooting

### Connection Refused

**Problem:** `psql: error: connection to server at "localhost" (::1), port 5532 failed: Connection refused`

**Solutions:**

1. Verify PostgreSQL is running:
   ```bash
   # macOS
   brew services list | grep postgresql

   # Linux
   sudo systemctl status postgresql
   ```

2. Check if the correct port is configured:
   ```bash
   # Check PostgreSQL configuration
   psql -U postgres -c "SHOW port;"
   ```

3. Verify Docker Compose is running (if using Docker):
   ```bash
   docker-compose ps
   ```

### Authentication Failed

**Problem:** `psql: error: FATAL: password authentication failed for user "calendar"`

**Solutions:**

1. Verify the user exists:
   ```sql
   psql -U postgres -c "\du calendar"
   ```

2. Reset the password:
   ```sql
   psql -U postgres -c "ALTER USER calendar WITH PASSWORD 'calendar';"
   ```

3. Check `pg_hba.conf` authentication settings:
   ```bash
   # Find pg_hba.conf location
   psql -U postgres -c "SHOW hba_file;"

   # Edit the file and ensure you have:
   # local   all   all   md5
   # host    all   all   127.0.0.1/32   md5
   ```

### PostGIS Extension Not Found

**Problem:** `ERROR: could not open extension control file "/usr/share/postgresql/17/extension/postgis.control"`

**Solutions:**

1. Install PostGIS for PostgreSQL 17:
   ```bash
   # macOS
   brew install postgis

   # Ubuntu/Debian
   sudo apt-get install postgresql-17-postgis-3

   # Red Hat/CentOS
   sudo dnf install postgis34_17
   ```

2. Verify installation:
   ```bash
   # Check available extensions
   psql -U postgres -d calendar -c "SELECT * FROM pg_available_extensions WHERE name LIKE 'postgis%';"
   ```

### Quarkus Health Check Failing

**Problem:** Health check endpoint returns database DOWN

**Solutions:**

1. Verify database is accessible:
   ```bash
   psql -h localhost -p 5532 -U calendar -d calendar -c "SELECT 1;"
   ```

2. Check Quarkus logs:
   ```bash
   ./mvnw quarkus:dev
   # Look for database connection errors in the output
   ```

3. Verify environment variables are set correctly:
   ```bash
   echo $DB_URL
   echo $DB_USERNAME
   # Don't echo password for security
   ```

4. Test the health endpoint:
   ```bash
   # Note: The application runs on port 8030, not 8080
   curl http://localhost:8030/q/health/ready

   # Expected output when healthy:
   # {
   #   "status": "UP",
   #   "checks": [
   #     {
   #       "name": "Database connections health check",
   #       "status": "UP"
   #     }
   #   ]
   # }
   ```

### Permission Denied Errors

**Problem:** `ERROR: permission denied for schema public`

**Solutions:**

1. Grant schema permissions:
   ```sql
   GRANT ALL ON SCHEMA public TO calendar;
   GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO calendar;
   GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO calendar;
   ```

2. Fix default privileges:
   ```sql
   ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO calendar;
   ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO calendar;
   ```

---

## Additional Resources

- [PostgreSQL 17 Documentation](https://www.postgresql.org/docs/17/)
- [PostGIS Documentation](https://postgis.net/documentation/)
- [Quarkus Datasources Guide](https://quarkus.io/guides/datasource)
- [MyBatis Migrations Documentation](https://mybatis.org/migrations/)
- [Village Calendar Project Documentation](../README.md)

---

## Support

For issues or questions:

1. Check the [Troubleshooting](#troubleshooting) section above
2. Review application logs: `./mvnw quarkus:dev`
3. Check database logs: `docker-compose logs postgres` (if using Docker)
4. Contact VillageCompute Support: https://villagecompute.com
