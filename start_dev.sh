#!/bin/bash
# Start village-calendar in development mode

echo "Starting Village Calendar in development mode..."
echo "Backend: http://localhost:8031"
echo "Frontend dev server: http://localhost:5176"
echo ""

# Run Quarkus dev mode (includes Quinoa for Vue integration)
./mvnw quarkus:dev
