#!/bin/bash
# Start village-calendar in development mode with database migrations

set -e  # Exit on error

echo "========================================"
echo "Village Calendar Development Startup"
echo "========================================"
echo ""

# Step 1: Run database migrations
echo "[1/2] Running database migrations..."
cd /app/migrations

# Wait for database to be ready (in case of race condition)
echo "Waiting for database to be ready..."
for i in {1..30}; do
    if mvn org.mybatis.maven:migrations-maven-plugin:status -Dmigration.env=development &>/dev/null; then
        echo "Database is ready!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "ERROR: Database not ready after 30 seconds"
        exit 1
    fi
    sleep 1
done

# Apply pending migrations
echo "Applying migrations..."
mvn org.mybatis.maven:migrations-maven-plugin:up -Dmigration.env=development

if [ $? -eq 0 ]; then
    echo "✓ Database migrations completed successfully"
else
    echo "✗ Database migrations failed"
    exit 1
fi

echo ""

# Step 2: Start Quarkus in dev mode
echo "[2/2] Starting Quarkus dev mode..."
echo "Backend: http://localhost:8030"
echo "Frontend dev server: http://localhost:5176"
echo "GraphQL UI: http://localhost:8030/graphql-ui"
echo "Health check: http://localhost:8030/q/health"
echo ""

cd /app
exec ./mvnw quarkus:dev
