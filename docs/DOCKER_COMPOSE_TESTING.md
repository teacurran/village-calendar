# Docker Compose Integration Testing

This document provides comprehensive instructions for testing the Village Calendar service in the Docker Compose environment.

## Prerequisites

- Docker and Docker Compose installed
- Ports available: 8030, 5430, 8130, 1030, 16687
- Minimum 4GB RAM available for Docker

## Quick Start

```bash
# Navigate to village-calendar directory
cd village-calendar

# Start all services
docker-compose up -d

# Wait for services to be healthy (30-60 seconds)
docker-compose ps

# View logs
docker-compose logs -f calendar-app

# Stop services when done
docker-compose down
```

## Detailed Testing Steps

### 1. Start Services

```bash
docker-compose up -d
```

Expected output:
```
[+] Running 4/4
 ✔ Container village-calendar-calendar-db-1   Healthy
 ✔ Container village-calendar-jaeger-1        Started
 ✔ Container village-calendar-mailpit-1       Started
 ✔ Container village-calendar-calendar-app-1  Started
```

### 2. Verify Service Health

#### Check Container Status

```bash
docker-compose ps
```

All containers should show `Up` status. The `calendar-db` container should show `healthy`.

#### Check Database Readiness

```bash
docker-compose logs calendar-db | grep "ready to accept connections"
```

You should see:
```
calendar-db-1  | LOG:  database system is ready to accept connections
```

#### Check Application Logs

```bash
docker-compose logs calendar-app | tail -50
```

Look for:
- `Quarkus X.X.X started in XXXms`
- `Listening on: http://0.0.0.0:8030`
- No error messages

### 3. Test Health Endpoints

#### Liveness Probe

```bash
curl -s http://localhost:8030/q/health/live | jq
```

Expected response:
```json
{
  "status": "UP",
  "checks": []
}
```

#### Readiness Probe

```bash
curl -s http://localhost:8030/q/health/ready | jq
```

Expected response:
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

### 4. Test GraphQL Endpoint

#### Basic Schema Introspection

```bash
curl -X POST http://localhost:8030/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ __typename }"}' | jq
```

Expected response:
```json
{
  "data": {
    "__typename": "Query"
  }
}
```

#### Query Templates

```bash
curl -X POST http://localhost:8030/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "query { templates(isActive: true) { id name description } }"}' | jq
```

Expected: List of available templates (may be empty if no templates seeded).

### 5. Verify Database Connectivity

#### Connect to Database

```bash
docker-compose exec calendar-db psql -U calendar_user -d village_calendar
```

Inside PostgreSQL shell, run:

```sql
-- Check tables exist
\dt

-- Check calendar_templates table
SELECT COUNT(*) FROM calendar_templates;

-- Check calendar_users table
SELECT COUNT(*) FROM calendar_users;

-- Exit
\q
```

Expected: Tables should exist (even if empty).

#### Check Schema Migrations

```bash
docker-compose exec calendar-db psql -U calendar_user -d village_calendar -c "SELECT * FROM schema_version ORDER BY installed_rank;"
```

This shows the migration history if MyBatis migrations have run.

### 6. Verify Supporting Services

#### Jaeger UI (Distributed Tracing)

Open in browser: http://localhost:16687

- You should see the Jaeger UI
- Select `calendar-service` from the Service dropdown
- Look for traces from your API calls

#### Mailpit UI (Email Testing)

Open in browser: http://localhost:8130

- You should see the Mailpit interface
- Any emails sent by the application will appear here
- Useful for testing OAuth callback emails (future feature)

### 7. Test Application Metrics

```bash
curl -s http://localhost:8030/q/metrics | grep -E "jvm_|http_server_requests"
```

Expected: Various JVM and HTTP metrics.

### 8. Run Integration Tests Against Docker Environment

**Note**: By default, integration tests use H2 in-memory database. To run tests against the Docker PostgreSQL instance:

#### Option A: Run tests from within Docker

```bash
docker-compose exec calendar-app mvn test -Dtest=*IntegrationTest
```

#### Option B: Configure tests to connect to Docker PostgreSQL

Create `src/test/resources/application-docker.properties`:

```properties
%docker.quarkus.datasource.db-kind=postgresql
%docker.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5430/village_calendar
%docker.quarkus.datasource.username=calendar_user
%docker.quarkus.datasource.password=calendar_pass
%docker.quarkus.hibernate-orm.database.generation=drop-and-create
```

Then run:

```bash
mvn test -Dquarkus.profile=docker -Dtest=*IntegrationTest
```

**Recommended**: Keep integration tests using H2 for speed, and use Docker Compose for manual testing and production-like validation.

### 9. Performance and Load Testing

#### Check Resource Usage

```bash
docker stats --no-stream
```

Look for:
- `calendar-app`: Should use < 512MB RAM under normal load
- `calendar-db`: Should use < 256MB RAM
- CPU usage should be low when idle

#### Generate Multiple Calendars (Manual Test)

Use GraphQL Playground or curl to create multiple calendars and verify performance remains acceptable.

### 10. Clean Shutdown

#### Stop Services

```bash
docker-compose down
```

#### Stop and Remove Volumes (Clean Slate)

```bash
docker-compose down -v
```

**Warning**: This deletes all data in the PostgreSQL database and Mailpit email archive.

## Troubleshooting

### Calendar App Won't Start

**Check logs**:
```bash
docker-compose logs calendar-app
```

Common issues:
- Database not ready: Wait longer for `calendar-db` to become healthy
- Port 8030 already in use: Stop conflicting service
- Build failed: Run `docker-compose build --no-cache calendar-app`

### Database Connection Errors

**Verify database is healthy**:
```bash
docker-compose ps calendar-db
```

**Check connection from app**:
```bash
docker-compose exec calendar-app psql -h calendar-db -U calendar_user -d village_calendar
```

If connection fails:
- Restart database: `docker-compose restart calendar-db`
- Check environment variables in `docker-compose.yml`

### GraphQL Endpoint Returns 404

**Verify app is running**:
```bash
curl http://localhost:8030/q/health/live
```

**Check if GraphQL is enabled**:
```bash
docker-compose logs calendar-app | grep -i graphql
```

### No Templates Available

Templates are not auto-seeded. You need to either:
1. Insert templates via SQL
2. Use the admin API (future feature)
3. Run migrations that include template seed data

**Manual template insert**:
```bash
docker-compose exec calendar-db psql -U calendar_user -d village_calendar -c "
INSERT INTO calendar_templates (id, name, description, is_active, is_featured, display_order, configuration, created_at, updated_at)
VALUES (
  gen_random_uuid(),
  'Default Template',
  'Basic calendar template',
  true,
  true,
  1,
  '{\"theme\": \"modern\", \"showMoonPhases\": true}'::jsonb,
  NOW(),
  NOW()
);
"
```

## Acceptance Criteria Verification

This section maps to the task acceptance criteria:

### ✅ All integration tests pass: `mvn verify`

```bash
cd village-calendar
mvn verify
```

Expected: All `*IntegrationTest` classes pass.

### ✅ Docker Compose environment runs all services

```bash
docker-compose up -d
docker-compose ps
```

Expected: All 4 services running (calendar-app, calendar-db, jaeger, mailpit).

### ✅ Calendar service health checks pass

```bash
curl http://localhost:8030/q/health/live
curl http://localhost:8030/q/health/ready
```

Expected: Both return `{"status":"UP"}`.

### ✅ PDF generation produces valid PDF file

**Note**: Full PDF generation requires:
1. Creating a calendar via GraphQL
2. Triggering PDF generation
3. Downloading from R2 URL (requires R2 credentials)

For local testing, verify:
- SVG generation works (check `calendar.generatedSvg` field)
- PDF rendering service doesn't throw errors
- Storage service mock is called (in integration tests)

**Production validation**: After deploying with real R2 credentials, manually:
1. Create a calendar
2. Generate PDF
3. Download PDF from returned URL
4. Open in PDF viewer (Preview, Adobe Acrobat, etc.)

### ✅ OAuth2 flow works with test accounts

**Note**: OAuth2 is **disabled** in Docker Compose (`QUARKUS_OIDC_GOOGLE_ENABLED: "false"`).

Integration tests use **mock SecurityIdentity** to simulate OAuth callbacks.

For production OAuth2 testing with real Google/Facebook:
1. Enable OIDC in production environment
2. Configure OAuth2 client credentials
3. Test actual OAuth flow through browser

### ✅ No critical bugs found (P0/P1 issues)

Run all tests and manual validation steps above. Document any issues found.

## Continuous Integration

For CI/CD pipelines (GitHub Actions, GitLab CI, etc.):

```yaml
# Example GitHub Actions workflow
name: Integration Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgis/postgis:15-3.4
        env:
          POSTGRES_USER: calendar_user
          POSTGRES_PASSWORD: calendar_pass
          POSTGRES_DB: village_calendar
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn verify
```

## Additional Resources

- **Quarkus Health Checks**: https://quarkus.io/guides/smallrye-health
- **GraphQL Testing**: https://quarkus.io/guides/smallrye-graphql
- **Docker Compose**: https://docs.docker.com/compose/
- **PostgreSQL + PostGIS**: https://hub.docker.com/r/postgis/postgis

## Summary

This document provides comprehensive testing procedures for the Village Calendar Docker Compose environment. Follow the steps in order to validate all components of the system.

**Key Validation Points**:
1. ✅ All containers start successfully
2. ✅ Health checks pass (liveness and readiness)
3. ✅ GraphQL endpoint responds correctly
4. ✅ Database connectivity works
5. ✅ Supporting services (Jaeger, Mailpit) are accessible
6. ✅ Integration tests pass with `mvn verify`

For production deployment, additional validation of OAuth2 flow and R2 storage is required.
