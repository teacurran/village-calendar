# Village Calendar Service - Beta Environment Deployment

This directory contains Kubernetes manifests and deployment instructions for the Village Calendar service beta environment.

## Overview

- **Namespace**: `calendar-beta`
- **Replicas**: 2 (for high availability)
- **Environment**: Beta
- **URL**: `https://beta.calendar.villagecompute.com`
- **Database**: `village_calendar_beta` on PostgreSQL server at `10.50.0.10:5432`

## Prerequisites

1. **K3s Cluster**: Running on `10.50.0.20` (AlmaLinux)
2. **PostgreSQL Database**: Server at `10.50.0.10` with `village_calendar_beta` database created
3. **Docker Registry Access**: Docker Hub credentials for pulling images
4. **Required Secrets**: All environment variables listed below must be set
5. **VPN Access**: Connected to WireGuard VPN (`10.200.0.0/24`)

## Deployment Methods

### Method 1: Ansible Playbook (Recommended)

The Ansible playbook automates database migrations, secret creation, and Kubernetes deployment.

#### 1. Set Environment Variables

```bash
# Registry credentials
export REGISTRY_USER="villagecompute"
export REGISTRY_PASSWORD="your-docker-hub-token"
export IMAGE_TAG="beta-latest"

# Database configuration
export DB_HOST="10.50.0.10"
export DB_PORT="5432"
export DB_NAME="village_calendar_beta"
export DB_USER="calendar_user_beta"
export DB_PASSWORD="your-secure-db-password"

# OAuth2 credentials
export GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="your-client-secret"
export FACEBOOK_CLIENT_ID=""  # Optional
export FACEBOOK_CLIENT_SECRET=""  # Optional

# JWT keys (use base64-encoded PEM format)
export JWT_PUBLIC_KEY="$(cat jwt-public-key.pem)"
export JWT_PRIVATE_KEY="$(cat jwt-private-key.pem)"

# Email / SMTP configuration
export MAIL_FROM="orders@villagecompute.com"
export MAIL_HOST="smtp.sendgrid.net"
export MAIL_PORT="587"
export MAIL_USERNAME="apikey"
export MAIL_PASSWORD="your-sendgrid-api-key"

# Cloudflare R2 configuration
export R2_ENDPOINT="https://your-account-id.r2.cloudflarestorage.com"
export R2_BUCKET="village-calendar-beta"
export R2_ACCESS_KEY="your-r2-access-key"
export R2_SECRET_KEY="your-r2-secret-key"
export R2_PUBLIC_URL="https://calendars-beta.villagecompute.com"

# Stripe configuration (use test keys for beta)
export STRIPE_SECRET_KEY="sk_test_your-secret-key"
export STRIPE_PUBLISHABLE_KEY="pk_test_your-publishable-key"
export STRIPE_WEBHOOK_SECRET="whsec_your-webhook-secret"
```

#### 2. Run Ansible Playbook

```bash
cd villagecompute/infra/ansible

# Deploy with all tasks (migrations + app)
ansible-playbook -i inventory/beta deploy-calendar.yml

# Deploy only migrations
ansible-playbook -i inventory/beta deploy-calendar.yml --tags migrations

# Deploy only application (skip migrations)
ansible-playbook -i inventory/beta deploy-calendar.yml --tags app
```

### Method 2: Manual Deployment

#### 1. Create Database

Connect to PostgreSQL server and create the database:

```bash
ssh tea@10.50.0.10

# Switch to postgres user
sudo -u postgres psql

# Create role and database
CREATE ROLE calendar_user_beta WITH LOGIN PASSWORD 'your-secure-password';
CREATE DATABASE village_calendar_beta OWNER calendar_user_beta;

# Connect to database and enable extensions
\c village_calendar_beta
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS postgis;

# Grant privileges
GRANT ALL PRIVILEGES ON DATABASE village_calendar_beta TO calendar_user_beta;
GRANT ALL PRIVILEGES ON SCHEMA public TO calendar_user_beta;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO calendar_user_beta;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO calendar_user_beta;
```

#### 2. Run Database Migrations

```bash
cd village-calendar/migrations

# Set database credentials
export DB_USERNAME="calendar_user_beta"
export DB_PASSWORD="your-secure-password"

# Run migrations
mvn mybatis-migrations:up -Dmigration.env=beta

# Verify migrations
PGPASSWORD=$DB_PASSWORD psql -h 10.50.0.10 -U calendar_user_beta -d village_calendar_beta \
  -c "SELECT * FROM schema_version ORDER BY applied_at DESC LIMIT 5;"
```

#### 3. Create Kubernetes Secrets

```bash
kubectl create namespace calendar-beta

# Create Docker registry secret
kubectl create secret docker-registry docker-registry-secret \
  --docker-server=docker.io \
  --docker-username=villagecompute \
  --docker-password=your-token \
  --namespace=calendar-beta

# Create application secrets
kubectl create secret generic calendar-secrets \
  --from-literal=QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://10.50.0.10:5432/village_calendar_beta" \
  --from-literal=QUARKUS_DATASOURCE_USERNAME="calendar_user_beta" \
  --from-literal=QUARKUS_DATASOURCE_PASSWORD="your-password" \
  --from-literal=GOOGLE_CLIENT_ID="your-client-id" \
  --from-literal=GOOGLE_CLIENT_SECRET="your-secret" \
  --from-literal=FACEBOOK_CLIENT_ID="" \
  --from-literal=FACEBOOK_CLIENT_SECRET="" \
  --from-literal=JWT_PUBLIC_KEY="$(cat jwt-public-key.pem)" \
  --from-literal=JWT_PRIVATE_KEY="$(cat jwt-private-key.pem)" \
  --from-literal=MAIL_FROM="orders@villagecompute.com" \
  --from-literal=MAIL_HOST="smtp.sendgrid.net" \
  --from-literal=MAIL_PORT="587" \
  --from-literal=MAIL_USERNAME="apikey" \
  --from-literal=MAIL_PASSWORD="your-api-key" \
  --from-literal=R2_ENDPOINT="your-endpoint" \
  --from-literal=R2_BUCKET="village-calendar-beta" \
  --from-literal=R2_ACCESS_KEY="your-key" \
  --from-literal=R2_SECRET_KEY="your-secret" \
  --from-literal=R2_PUBLIC_URL="your-url" \
  --from-literal=STRIPE_SECRET_KEY="sk_test_your-key" \
  --from-literal=STRIPE_PUBLISHABLE_KEY="pk_test_your-key" \
  --from-literal=STRIPE_WEBHOOK_SECRET="whsec_your-secret" \
  --namespace=calendar-beta
```

#### 4. Apply Kubernetes Manifests

```bash
# Apply all manifests
kubectl apply -f village-calendar/k8s/beta/

# Verify deployment
kubectl get pods -n calendar-beta
kubectl get svc -n calendar-beta
kubectl get ingress -n calendar-beta

# Check logs
kubectl logs -f -n calendar-beta -l app=calendar-app

# Check rollout status
kubectl rollout status deployment/calendar-app -n calendar-beta
```

## Post-Deployment Configuration

### 1. Cloudflare DNS Configuration

Create DNS A record or CNAME:
- **Name**: `beta.calendar.villagecompute.com`
- **Type**: CNAME or A record
- **Target**: K3s cluster ingress IP or Cloudflare Tunnel

### 2. Cloudflare Tunnel Configuration

Configure tunnel route:
- **Hostname**: `beta.calendar.villagecompute.com`
- **Service**: `http://calendar-service.calendar-beta.svc.cluster.local:80`
- **Path**: `/` (all paths)

### 3. OAuth2 Redirect URIs

Update OAuth2 application settings:

**Google Cloud Console**:
- Authorized redirect URI: `https://beta.calendar.villagecompute.com/auth/google/callback`
- Authorized JavaScript origins: `https://beta.calendar.villagecompute.com`

**Facebook Developers**:
- Valid OAuth Redirect URI: `https://beta.calendar.villagecompute.com/auth/facebook/callback`
- Website URL: `https://beta.calendar.villagecompute.com`

### 4. Stripe Webhook Configuration

Configure webhook endpoint:
- **URL**: `https://beta.calendar.villagecompute.com/api/webhooks/stripe`
- **Events**: `checkout.session.completed`, `payment_intent.succeeded`, `payment_intent.failed`
- **Webhook Secret**: Copy to `STRIPE_WEBHOOK_SECRET` environment variable

## Smoke Tests

### 1. Health Check

```bash
curl https://beta.calendar.villagecompute.com/q/health
curl https://beta.calendar.villagecompute.com/q/health/live
curl https://beta.calendar.villagecompute.com/q/health/ready
```

Expected response: `{"status":"UP",...}`

### 2. GraphQL Introspection

```bash
curl -X POST https://beta.calendar.villagecompute.com/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{__schema{types{name}}}"}'
```

Expected response: JSON with GraphQL schema types

### 3. GraphQL UI

Open in browser: `https://beta.calendar.villagecompute.com/graphql-ui`

### 4. Prometheus Metrics

```bash
curl https://beta.calendar.villagecompute.com/q/metrics
```

Expected response: Prometheus metrics in text format

### 5. OAuth2 Login Flow

1. Navigate to `https://beta.calendar.villagecompute.com`
2. Click "Sign in with Google"
3. Complete OAuth2 flow
4. Verify redirect back to application

### 6. Calendar Creation Test

```bash
# Get JWT token from login
TOKEN="your-jwt-token-from-login"

# Create test calendar
curl -X POST https://beta.calendar.villagecompute.com/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "mutation { createCalendar(name: \"Test Calendar\", year: 2024) { id name } }"
  }'
```

Expected response: Calendar created with ID and name

## Monitoring

### Kubernetes Dashboard

```bash
# Get pod status
kubectl get pods -n calendar-beta -o wide

# Get events
kubectl get events -n calendar-beta --sort-by='.lastTimestamp'

# View logs
kubectl logs -n calendar-beta -l app=calendar-app --tail=100 -f

# Describe deployment
kubectl describe deployment calendar-app -n calendar-beta
```

### Prometheus Metrics

Metrics are exposed at `/q/metrics` and scraped by Prometheus with the following labels:
- `prometheus.io/scrape: "true"`
- `prometheus.io/port: "8030"`
- `prometheus.io/path: "/q/metrics"`

### Jaeger Traces

Distributed tracing is enabled via OpenTelemetry. View traces in Jaeger UI:
- Filter by service: `village-calendar`
- Filter by environment: `beta`

## Troubleshooting

### Pods Not Starting

```bash
# Check pod status
kubectl get pods -n calendar-beta

# Describe pod
kubectl describe pod <pod-name> -n calendar-beta

# Check events
kubectl get events -n calendar-beta --sort-by='.lastTimestamp'

# Check logs
kubectl logs -n calendar-beta <pod-name> --previous
```

Common issues:
- **ImagePullBackOff**: Check Docker registry credentials
- **CrashLoopBackOff**: Check application logs for errors
- **Pending**: Check resource availability (CPU, memory)

### Database Connection Issues

```bash
# Test database connection from pod
kubectl exec -n calendar-beta -it <pod-name> -- bash
psql -h 10.50.0.10 -U calendar_user_beta -d village_calendar_beta

# Check database secrets
kubectl get secret calendar-secrets -n calendar-beta -o yaml

# Verify database is reachable from K3s cluster
kubectl run -n calendar-beta test-db --image=postgres:17 --rm -it --restart=Never -- \
  psql -h 10.50.0.10 -U calendar_user_beta -d village_calendar_beta
```

### Health Check Failures

```bash
# Test health endpoint from within pod
kubectl exec -n calendar-beta -it <pod-name> -- \
  curl http://localhost:8030/q/health

# Check readiness probe logs
kubectl logs -n calendar-beta <pod-name> | grep -i "health\|ready"
```

### OAuth2 Issues

- Verify redirect URIs match exactly in OAuth2 provider settings
- Check client ID and secret are correct in secrets
- Ensure HTTPS is enabled (required by OAuth2 providers)
- Check application logs for OAuth2 errors

## Rollback

To rollback to a previous version:

```bash
# View rollout history
kubectl rollout history deployment/calendar-app -n calendar-beta

# Rollback to previous version
kubectl rollout undo deployment/calendar-app -n calendar-beta

# Rollback to specific revision
kubectl rollout undo deployment/calendar-app -n calendar-beta --to-revision=2
```

## Cleanup

To remove the deployment:

```bash
# Delete all resources
kubectl delete namespace calendar-beta

# Or delete individual resources
kubectl delete -f village-calendar/k8s/beta/
```

## Files Reference

### Kubernetes Manifests
- `deployment.yaml` - Deployment with 2 replicas, health probes, resource limits
- `service.yaml` - ClusterIP service on port 80 → 8030
- `ingress.yaml` - Ingress routing `/calendar` path to service
- `configmap.yaml` - Non-secret configuration (log level, feature flags)
- `secrets.yaml.example` - Template for creating secrets (DO NOT COMMIT)

### Infrastructure Files
- `villagecompute/infra/ansible/deploy-calendar.yml` - Ansible playbook
- `villagecompute/infra/ansible/inventory/beta` - Ansible inventory for beta environment
- `villagecompute/infra/postgres-db/calendar-beta-config.yml` - Database configuration
- `village-calendar/migrations/src/main/resources/environments/beta.properties` - Migration configuration

## Support

For issues or questions:
1. Check application logs: `kubectl logs -n calendar-beta -l app=calendar-app`
2. Check Kubernetes events: `kubectl get events -n calendar-beta`
3. Review this README and troubleshooting section
4. Contact DevOps team
