#!/bin/bash

################################################################################
# Village Calendar - Beta Environment Deployment Script
# Task: I4.T2 - Beta Environment Deployment (Database + Application)
################################################################################

set -e  # Exit on error
set -u  # Exit on undefined variable

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_command() {
    if ! command -v $1 &> /dev/null; then
        log_error "$1 is not installed. Please install it first."
        exit 1
    fi
}

################################################################################
# Step 1: Prerequisites Check
################################################################################

log_info "Checking prerequisites..."
check_command mvn
check_command docker
check_command ssh
check_command ansible-playbook
check_command kubectl
log_success "All required tools are installed"

################################################################################
# Step 2: Environment Variables Validation
################################################################################

log_info "Validating environment variables..."

REQUIRED_VARS=(
    "REGISTRY_USER"
    "REGISTRY_PASSWORD"
    "DB_PASSWORD"
    "GOOGLE_CLIENT_ID"
    "GOOGLE_CLIENT_SECRET"
    "JWT_PUBLIC_KEY"
    "JWT_PRIVATE_KEY"
    "MAIL_HOST"
    "MAIL_USERNAME"
    "MAIL_PASSWORD"
    "R2_ENDPOINT"
    "R2_ACCESS_KEY"
    "R2_SECRET_KEY"
    "R2_PUBLIC_URL"
    "STRIPE_SECRET_KEY"
    "STRIPE_PUBLISHABLE_KEY"
    "STRIPE_WEBHOOK_SECRET"
)

MISSING_VARS=()
for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var:-}" ]; then
        MISSING_VARS+=("$var")
    fi
done

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
    log_error "Missing required environment variables:"
    for var in "${MISSING_VARS[@]}"; do
        echo "  - $var"
    done
    echo ""
    echo "Please set these variables before running this script."
    echo "Example:"
    echo "  export REGISTRY_USER=villagecompute"
    echo "  export REGISTRY_PASSWORD=your-docker-hub-token"
    echo "  # ... (set all required variables)"
    exit 1
fi

log_success "All required environment variables are set"

################################################################################
# Step 3: Set Default Variables
################################################################################

export ENVIRONMENT="${ENVIRONMENT:-beta}"
export IMAGE_TAG="${IMAGE_TAG:-beta-latest}"
export DB_HOST="${DB_HOST:-10.50.0.10}"
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME="${DB_NAME:-village_calendar_beta}"
export DB_USER="${DB_USER:-calendar_user_beta}"
export MAIL_FROM="${MAIL_FROM:-orders@villagecompute.com}"
export MAIL_PORT="${MAIL_PORT:-587}"
export R2_BUCKET="${R2_BUCKET:-village-calendar-beta}"
export PROJECT_ROOT="$(pwd)"

log_info "Environment: $ENVIRONMENT"
log_info "Image: $REGISTRY_USER/village-calendar:$IMAGE_TAG"
log_info "Database: $DB_HOST:$DB_PORT/$DB_NAME"

################################################################################
# Step 4: Test Database Connection
################################################################################

log_info "Testing database connection..."
if ssh tea@10.50.0.20 "PGPASSWORD='$DB_PASSWORD' psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c 'SELECT version();'" > /dev/null 2>&1; then
    log_success "Database connection successful"
else
    log_error "Database connection failed!"
    log_info "Attempting to verify database exists..."
    ssh tea@10.50.0.10 "sudo -u postgres psql -c '\\l $DB_NAME'" || {
        log_error "Database $DB_NAME does not exist. Please create it first."
        exit 1
    }
    exit 1
fi

################################################################################
# Step 5: Build Maven Project
################################################################################

log_info "Building Maven project..."
mvn clean package -DskipTests -Dquarkus.quinoa.build=true
log_success "Maven build completed"

################################################################################
# Step 6: Build Docker Image
################################################################################

log_info "Building Docker image..."
docker build -t $REGISTRY_USER/village-calendar:$IMAGE_TAG .
log_success "Docker image built successfully"

################################################################################
# Step 7: Push Docker Image to Registry
################################################################################

log_info "Logging in to Docker registry..."
echo "$REGISTRY_PASSWORD" | docker login -u "$REGISTRY_USER" --password-stdin

log_info "Pushing Docker image to registry..."
docker push $REGISTRY_USER/village-calendar:$IMAGE_TAG
log_success "Docker image pushed successfully"

################################################################################
# Step 8: Deploy with Ansible
################################################################################

log_info "Deploying to K3s cluster with Ansible..."

cd ../villagecompute/infra/ansible

# Run deployment playbook
ansible-playbook -i inventory/beta deploy-calendar.yml \
    --tags migrations,app \
    -e "ENVIRONMENT=$ENVIRONMENT" \
    -e "IMAGE_TAG=$IMAGE_TAG" \
    -e "REGISTRY_USER=$REGISTRY_USER" \
    -e "REGISTRY_PASSWORD=$REGISTRY_PASSWORD" \
    -e "DB_HOST=$DB_HOST" \
    -e "DB_PORT=$DB_PORT" \
    -e "DB_NAME=$DB_NAME" \
    -e "DB_USER=$DB_USER" \
    -e "DB_PASSWORD=$DB_PASSWORD" \
    -e "GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID" \
    -e "GOOGLE_CLIENT_SECRET=$GOOGLE_CLIENT_SECRET" \
    -e "FACEBOOK_CLIENT_ID=${FACEBOOK_CLIENT_ID:-}" \
    -e "FACEBOOK_CLIENT_SECRET=${FACEBOOK_CLIENT_SECRET:-}" \
    -e "JWT_PUBLIC_KEY=$JWT_PUBLIC_KEY" \
    -e "JWT_PRIVATE_KEY=$JWT_PRIVATE_KEY" \
    -e "MAIL_FROM=$MAIL_FROM" \
    -e "MAIL_HOST=$MAIL_HOST" \
    -e "MAIL_PORT=$MAIL_PORT" \
    -e "MAIL_USERNAME=$MAIL_USERNAME" \
    -e "MAIL_PASSWORD=$MAIL_PASSWORD" \
    -e "R2_ENDPOINT=$R2_ENDPOINT" \
    -e "R2_BUCKET=$R2_BUCKET" \
    -e "R2_ACCESS_KEY=$R2_ACCESS_KEY" \
    -e "R2_SECRET_KEY=$R2_SECRET_KEY" \
    -e "R2_PUBLIC_URL=$R2_PUBLIC_URL" \
    -e "STRIPE_SECRET_KEY=$STRIPE_SECRET_KEY" \
    -e "STRIPE_PUBLISHABLE_KEY=$STRIPE_PUBLISHABLE_KEY" \
    -e "STRIPE_WEBHOOK_SECRET=$STRIPE_WEBHOOK_SECRET" \
    -e "PROJECT_ROOT=$PROJECT_ROOT"

cd -

log_success "Ansible deployment completed"

################################################################################
# Step 9: Verify Deployment
################################################################################

log_info "Verifying deployment..."

# Check pods
log_info "Checking pod status..."
ssh tea@10.50.0.20 "kubectl get pods -n calendar-beta"

# Check service
log_info "Checking service status..."
ssh tea@10.50.0.20 "kubectl get svc -n calendar-beta calendar-service"

# Check ingress
log_info "Checking ingress status..."
ssh tea@10.50.0.20 "kubectl get ingress -n calendar-beta calendar-ingress"

# Wait for pods to be ready
log_info "Waiting for pods to be ready (this may take a few minutes)..."
ssh tea@10.50.0.20 "kubectl wait --for=condition=ready pod -l app=calendar-app -n calendar-beta --timeout=300s" || {
    log_warning "Pods did not become ready within 5 minutes. Checking logs..."
    ssh tea@10.50.0.20 "kubectl logs -n calendar-beta -l app=calendar-app --tail=50"
}

log_success "Deployment verification completed"

################################################################################
# Step 10: Smoke Tests
################################################################################

log_info "Running smoke tests..."

# Test health endpoint from within cluster
log_info "Testing health endpoint..."
HEALTH_CHECK=$(ssh tea@10.50.0.20 "kubectl exec -n calendar-beta -it \$(kubectl get pod -n calendar-beta -l app=calendar-app -o jsonpath='{.items[0].metadata.name}') -- wget -qO- http://localhost:8030/q/health" 2>/dev/null || echo "FAILED")

if [[ "$HEALTH_CHECK" == *"UP"* ]]; then
    log_success "Health check passed"
else
    log_error "Health check failed: $HEALTH_CHECK"
fi

# Test readiness endpoint
log_info "Testing readiness endpoint..."
READINESS_CHECK=$(ssh tea@10.50.0.20 "kubectl exec -n calendar-beta -it \$(kubectl get pod -n calendar-beta -l app=calendar-app -o jsonpath='{.items[0].metadata.name}') -- wget -qO- http://localhost:8030/q/health/ready" 2>/dev/null || echo "FAILED")

if [[ "$READINESS_CHECK" == *"UP"* ]]; then
    log_success "Readiness check passed"
else
    log_error "Readiness check failed: $READINESS_CHECK"
fi

################################################################################
# Step 11: Display Deployment Summary
################################################################################

echo ""
echo "================================================================================"
echo "                    DEPLOYMENT SUMMARY"
echo "================================================================================"
echo ""
echo "Environment:       $ENVIRONMENT"
echo "Image:             $REGISTRY_USER/village-calendar:$IMAGE_TAG"
echo "Namespace:         calendar-$ENVIRONMENT"
echo "Database:          $DB_HOST:$DB_PORT/$DB_NAME"
echo ""
echo "Application URL:   https://beta.villagecompute.com/calendar"
echo "GraphQL UI:        https://beta.villagecompute.com/calendar/graphql-ui"
echo "Health Check:      https://beta.villagecompute.com/calendar/q/health"
echo "Metrics:           https://beta.villagecompute.com/calendar/q/metrics"
echo ""
echo "================================================================================"
echo ""
log_success "Deployment completed successfully!"
echo ""
log_info "Next steps:"
echo "  1. Configure Cloudflare DNS for beta.calendar.villagecompute.com (Task I4.T4)"
echo "  2. Update OAuth2 redirect URIs in Google Cloud Console"
echo "  3. Configure Stripe webhook endpoint"
echo "  4. Run comprehensive smoke tests"
echo ""
