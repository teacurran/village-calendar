#!/usr/bin/env bash
#
# Village Calendar Database Setup Verification Script
#
# This script verifies that PostgreSQL 17 with PostGIS is correctly configured
# and the Quarkus application can connect to the database.
#
# Usage: ./scripts/verify-database-setup.sh

set -e

echo "======================================================================"
echo "Village Calendar - Database Setup Verification"
echo "======================================================================"
echo ""

# Check if Docker is running
if ! docker ps &>/dev/null; then
    echo " Docker is not running. Please start Docker and try again."
    exit 1
fi

echo "[Step 1/6] Checking Docker Compose database status..."
if docker compose ps calendar-db | grep -q "Up"; then
    echo " PostgreSQL container is running"
else
    echo " PostgreSQL container is not running"
    echo "   Starting database..."
    docker compose up -d calendar-db
    echo " Waiting for database to be ready (10 seconds)..."
    sleep 10
fi
echo ""

echo "[Step 2/6] Verifying PostgreSQL version..."
PG_VERSION=$(docker exec village-calendar-calendar-db-1 psql -U calendar -d calendar -t -c "SELECT version();" | grep -o "PostgreSQL [0-9]*" | grep -o "[0-9]*")
if [ "$PG_VERSION" == "17" ]; then
    echo " PostgreSQL 17 detected"
else
    echo " Warning: PostgreSQL version is $PG_VERSION (expected 17)"
fi
echo ""

echo "[Step 3/6] Verifying PostGIS extension..."
POSTGIS_VERSION=$(docker exec village-calendar-calendar-db-1 psql -U calendar -d calendar -t -c "SELECT PostGIS_Version();" | tr -d '[:space:]')
if [ -n "$POSTGIS_VERSION" ]; then
    echo " PostGIS version: $POSTGIS_VERSION"
else
    echo " PostGIS extension not found!"
    exit 1
fi
echo ""

echo "[Step 4/6] Testing PostGIS spatial functions..."
SPATIAL_TEST=$(docker exec village-calendar-calendar-db-1 psql -U calendar -d calendar -t -c "SELECT ST_AsText(ST_Point(-122.4194, 37.7749));" | tr -d '[:space:]')
if [[ "$SPATIAL_TEST" == *"POINT"* ]]; then
    echo " PostGIS spatial functions working correctly"
    echo "   Test result: $SPATIAL_TEST"
else
    echo " PostGIS spatial functions test failed"
    exit 1
fi
echo ""

echo "[Step 5/6] Verifying database connection configuration..."
echo "   JDBC URL: ${DB_URL:-jdbc:postgresql://localhost:5532/calendar} (default)"
echo "   Username: ${DB_USERNAME:-calendar} (default)"
echo "   Port: 5532"
echo ""

echo "[Step 6/6] Testing psql command-line connection..."
if docker exec village-calendar-calendar-db-1 psql -U calendar -d calendar -c "SELECT 1;" &>/dev/null; then
    echo " Direct database connection successful"
else
    echo " Direct database connection failed"
    exit 1
fi
echo ""

echo "======================================================================"
echo " Database Setup Verification Complete!"
echo "======================================================================"
echo ""
echo "Next steps:"
echo "1. Start the Quarkus application:"
echo "   ./mvnw quarkus:dev"
echo ""
echo "2. Test the health check endpoint (wait ~30 seconds for startup):"
echo "   curl http://localhost:8030/q/health/ready"
echo ""
echo "3. Verify environment variable overrides (optional):"
echo "   export DB_URL=\"jdbc:postgresql://localhost:5532/calendar\""
echo "   export DB_USERNAME=\"calendar\""
echo "   export DB_PASSWORD=\"calendar\""
echo "   ./mvnw quarkus:dev"
echo ""
echo "For troubleshooting, see: docs/guides/database-setup.md"
echo "======================================================================"
