#!/bin/bash

###############################################################################
# E2E Test Environment Setup Script
#
# This script prepares the environment for running Playwright E2E tests:
# 1. Starts Docker Compose services
# 2. Waits for services to be healthy
# 3. Seeds test data (optional)
# 4. Installs Playwright browsers (if needed)
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
MAX_WAIT_TIME=120  # Maximum wait time in seconds
CHECK_INTERVAL=5   # Health check interval in seconds

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}E2E Test Environment Setup${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

###############################################################################
# Step 1: Check Prerequisites
###############################################################################

echo -e "${YELLOW}→ Checking prerequisites...${NC}"

# Check Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗ Docker is not installed${NC}"
    exit 1
fi

# Check Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${RED}✗ Docker Compose is not installed${NC}"
    exit 1
fi

# Check Node.js
if ! command -v node &> /dev/null; then
    echo -e "${RED}✗ Node.js is not installed${NC}"
    exit 1
fi

echo -e "${GREEN}✓ All prerequisites met${NC}"
echo ""

###############################################################################
# Step 2: Start Docker Compose Services
###############################################################################

echo -e "${YELLOW}→ Starting Docker Compose services...${NC}"

cd "$PROJECT_ROOT"

# Stop any existing containers
docker-compose down 2>/dev/null || true

# Build and start services
docker-compose up -d --build

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Failed to start Docker Compose services${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker Compose services started${NC}"
echo ""

###############################################################################
# Step 3: Wait for Services to be Healthy
###############################################################################

echo -e "${YELLOW}→ Waiting for services to be healthy...${NC}"

# Function to check if a service is healthy
check_service_health() {
    local service_name=$1
    local health_status=$(docker-compose ps -q $service_name | xargs docker inspect --format='{{.State.Health.Status}}' 2>/dev/null || echo "unknown")

    if [ "$health_status" = "healthy" ]; then
        return 0
    else
        return 1
    fi
}

# Function to check if a URL is responding
check_url() {
    local url=$1
    curl -s -o /dev/null -w "%{http_code}" "$url" | grep -q "200\|302\|404"
}

# Wait for database
echo -n "  Waiting for calendar-db..."
elapsed=0
while [ $elapsed -lt $MAX_WAIT_TIME ]; do
    if check_service_health "calendar-db"; then
        echo -e " ${GREEN}✓${NC}"
        break
    fi
    sleep $CHECK_INTERVAL
    elapsed=$((elapsed + CHECK_INTERVAL))
    echo -n "."
done

if [ $elapsed -ge $MAX_WAIT_TIME ]; then
    echo -e " ${RED}✗ Timeout${NC}"
    exit 1
fi

# Wait for calendar app
echo -n "  Waiting for calendar-app..."
elapsed=0
while [ $elapsed -lt $MAX_WAIT_TIME ]; do
    if check_url "http://localhost:8030/q/health/ready"; then
        echo -e " ${GREEN}✓${NC}"
        break
    fi
    sleep $CHECK_INTERVAL
    elapsed=$((elapsed + CHECK_INTERVAL))
    echo -n "."
done

if [ $elapsed -ge $MAX_WAIT_TIME ]; then
    echo -e " ${RED}✗ Timeout${NC}"
    exit 1
fi

# Wait for Mailpit
echo -n "  Waiting for Mailpit..."
elapsed=0
while [ $elapsed -lt $MAX_WAIT_TIME ]; do
    if check_url "http://localhost:8130/"; then
        echo -e " ${GREEN}✓${NC}"
        break
    fi
    sleep $CHECK_INTERVAL
    elapsed=$((elapsed + CHECK_INTERVAL))
    echo -n "."
done

if [ $elapsed -ge $MAX_WAIT_TIME ]; then
    echo -e " ${YELLOW}⚠ Mailpit not responding (email tests may fail)${NC}"
fi

echo -e "${GREEN}✓ All services are healthy${NC}"
echo ""

###############################################################################
# Step 4: Install Playwright Browsers (if needed)
###############################################################################

echo -e "${YELLOW}→ Checking Playwright browsers...${NC}"

cd "$PROJECT_ROOT/e2e-tests"

# Install npm dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "  Installing npm dependencies..."
    cd "$PROJECT_ROOT/src/main/webui"
    npm install
fi

# Install Playwright browsers
if ! npx playwright --version &> /dev/null; then
    echo "  Installing Playwright..."
    cd "$PROJECT_ROOT/src/main/webui"
    npm install -D @playwright/test
fi

echo "  Installing Playwright browsers..."
cd "$PROJECT_ROOT/src/main/webui"
npx playwright install chromium

echo -e "${GREEN}✓ Playwright browsers installed${NC}"
echo ""

###############################################################################
# Step 5: Verify Environment
###############################################################################

echo -e "${YELLOW}→ Verifying test environment...${NC}"

# Check GraphQL endpoint
if check_url "http://localhost:8030/graphql"; then
    echo -e "  GraphQL endpoint: ${GREEN}✓${NC}"
else
    echo -e "  GraphQL endpoint: ${RED}✗${NC}"
fi

# Check frontend (if running)
if check_url "http://localhost:5176"; then
    echo -e "  Frontend (Vite): ${GREEN}✓${NC}"
else
    echo -e "  Frontend (Vite): ${YELLOW}⚠ Not running (use 'npm run dev' in src/main/webui)${NC}"
fi

# Check Mailpit API
if check_url "http://localhost:8130/api/v1/messages"; then
    echo -e "  Mailpit API: ${GREEN}✓${NC}"
else
    echo -e "  Mailpit API: ${YELLOW}⚠ Not responding${NC}"
fi

echo ""

###############################################################################
# Summary
###############################################################################

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Environment Setup Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Service URLs:"
echo "  Calendar App: http://localhost:8030"
echo "  Frontend (Vite): http://localhost:5176"
echo "  Mailpit UI: http://localhost:8130"
echo "  GraphQL: http://localhost:8030/graphql"
echo ""
echo "Run E2E tests:"
echo "  cd $PROJECT_ROOT/src/main/webui"
echo "  npm run test:e2e"
echo ""
echo "View test results:"
echo "  npx playwright show-report"
echo ""
echo -e "${YELLOW}Note: Make sure Vite dev server is running (npm run dev) before running tests${NC}"
echo ""
