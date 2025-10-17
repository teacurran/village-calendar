# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Set up PostgreSQL database connection configuration in Quarkus (application.properties with JDBC URL, credentials from environment variables). Create MyBatis Migrations directory structure (migrations/scripts/, migrations/environments/). Configure development and production migration environments. Document database setup instructions (create database, install PostGIS extension). Test database connectivity with Quarkus datasource health check.

**Acceptance Criteria:**
- `./mvnw quarkus:dev` connects to PostgreSQL without errors
- `curl http://localhost:8080/q/health/ready` returns 200 with datasource check passing
- Database setup guide tested on fresh PostgreSQL 17 installation
- Environment variables DB_USERNAME, DB_PASSWORD, DB_URL override defaults

---

## Issues Detected

*   **Critical - PostGIS Extension Not Enabled:** The database does NOT have the PostGIS extension enabled. When attempting to query `SELECT PostGIS_Version();`, the error was: `ERROR: function postgis_version() does not exist`. This is a critical requirement mentioned in the task description and architectural context.

*   **Warning - PostgreSQL Version Mismatch:** The current database is running PostgreSQL 16.8, but the requirements specify PostgreSQL 17+. The task description states "PostgreSQL 17+ with PostGIS requirement" and acceptance criteria mentions "Database setup guide tested on fresh PostgreSQL 17 installation".

*   **Minor - Health Check Port Mismatch:** The acceptance criteria mentions testing the health check at `http://localhost:8080/q/health/ready`, but the application is configured to run on port 8030 (see `quarkus.http.port=8030` in application.properties). The documentation file `docs/guides/database-setup.md` correctly notes this discrepancy at line 539, but the acceptance criteria needs to be validated against the actual port.

*   **Not Verified - Quarkus Health Check:** The Quarkus application was not fully verified to connect to the database and report a healthy datasource status. Testing was interrupted due to the PostGIS issue discovery.

---

## Best Approach to Fix

### 1. Enable PostGIS Extension in the Database

You MUST enable the PostGIS extension in the PostgreSQL database. The documentation at `docs/guides/database-setup.md` provides clear instructions (lines 147-171), but this step needs to be EXECUTED, not just documented.

**Action Required:**
```sql
-- Connect to the calendar database as a superuser
\c calendar postgres

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Verify PostGIS installation
SELECT PostGIS_Version();
```

If you do not have superuser access, you need to either:
- Update the database setup documentation to include a migration script that enables PostGIS (though this typically requires superuser privileges)
- Create a Docker Compose setup that automatically enables PostGIS on database initialization
- Provide clear instructions for DBAs to enable PostGIS before running migrations

### 2. Address PostgreSQL Version Requirement

The task specifies PostgreSQL 17+, but the current database is PostgreSQL 16.8. You should:

**Option A (Recommended):** Update the Docker Compose configuration to use PostgreSQL 17 with PostGIS:
- Modify `docker-compose.yml` (if it exists) to use `postgis/postgis:17-3.4` or `postgres:17` image
- Restart the database container
- Re-run the database setup steps

**Option B:** Update the database setup guide to clarify that PostgreSQL 16+ is acceptable if testing reveals no compatibility issues. However, this contradicts the explicit requirement for PostgreSQL 17+.

### 3. Verify Health Check Endpoint

After enabling PostGIS and ensuring the database is properly configured:

1. Start the Quarkus application: `./mvnw quarkus:dev`
2. Wait for startup to complete (look for "Quarkus started" message)
3. Test the health endpoint on the CORRECT port:
   ```bash
   curl http://localhost:8030/q/health/ready
   ```
4. Verify the response shows the datasource health check as UP:
   ```json
   {
     "status": "UP",
     "checks": [
       {
         "name": "Database connections health check",
         "status": "UP"
       }
     ]
   }
   ```

### 4. Update Documentation if Needed

If you discover any discrepancies between the documented steps and the actual setup process, update `docs/guides/database-setup.md` to reflect the correct procedure.

---

## Priority

**CRITICAL:** Enable PostGIS extension in the database - this is blocking the task completion and is explicitly required by the architecture documentation.

**HIGH:** Verify PostgreSQL version meets the 17+ requirement or document why 16.8 is acceptable.

**MEDIUM:** Verify and document the correct health check endpoint URL (port 8030, not 8080).
