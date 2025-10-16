# Village Calendar Service - Deployment Runbook

This document provides comprehensive instructions for deploying the Village Calendar Service to the beta Kubernetes (K3s) environment.

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Pre-Deployment Checklist](#pre-deployment-checklist)
4. [Deployment Steps](#deployment-steps)
5. [Post-Deployment Verification](#post-deployment-verification)
6. [Rollback Procedure](#rollback-procedure)
7. [Troubleshooting](#troubleshooting)
8. [Monitoring and Observability](#monitoring-and-observability)

---

## Overview

The Village Calendar Service is deployed to a Kubernetes (K3s) cluster running on Proxmox infrastructure. The beta environment uses:

- **Namespace:** `calendar-beta`
- **Replicas:** 2 (for high availability and zero-downtime deployments)
- **Ingress URL:** `https://beta.villagecompute.com/calendar/*`
- **Database:** PostgreSQL (`village_calendar_beta` schema on shared instance)
- **Storage:** Cloudflare R2 for PDF files and template images

---

## Prerequisites

### Required Tools

- `kubectl` configured with access to the K3s cluster
- `ansible` (version 2.9 or higher) for automated deployments
- `docker` for building container images
- `psql` for database operations
- `git` for version control

### Required Access

- SSH access to the K3s cluster nodes
- Database credentials for `village_calendar_beta` database
- Docker registry credentials (Docker Hub or private registry)
- Cloudflare R2 access credentials
- OAuth2 application credentials (Google, optionally Facebook)
- Stripe API credentials (test keys for beta)

### Environment Variables

Create a `.env` file or set the following environment variables before deployment:

```bash
# Environment Configuration
export ENVIRONMENT="beta"
export IMAGE_TAG="beta-1.0.0"  # or "beta-latest"

# Container Registry
export REGISTRY_USER="villagecompute"
export REGISTRY_PASSWORD="your-docker-hub-token"

# Database Configuration
export DB_HOST="10.50.0.10"
export DB_PORT="5432"
export DB_NAME="village_calendar_beta"
export DB_USER="calendar_user_beta"
export DB_PASSWORD="your-secure-db-password"

# OAuth2 Credentials
export GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="your-google-client-secret"
export FACEBOOK_CLIENT_ID="your-facebook-app-id"  # Optional
export FACEBOOK_CLIENT_SECRET="your-facebook-app-secret"  # Optional

# JWT Keys (base64-encoded PEM files)
export JWT_PUBLIC_KEY="$(cat jwt-public-key.pem)"
export JWT_PRIVATE_KEY="$(cat jwt-private-key.pem)"

# Email/Mailer Configuration
export MAIL_FROM="orders@villagecompute.com"
export MAIL_HOST="smtp.sendgrid.net"
export MAIL_PORT="587"
export MAIL_USERNAME="apikey"
export MAIL_PASSWORD="your-sendgrid-api-key"

# Cloudflare R2 Configuration
export R2_ENDPOINT="https://your-account-id.r2.cloudflarestorage.com"
export R2_BUCKET="village-calendar-beta"
export R2_ACCESS_KEY="your-r2-access-key"
export R2_SECRET_KEY="your-r2-secret-key"
export R2_PUBLIC_URL="https://calendars-beta.villagecompute.com"

# Stripe Configuration (use test keys for beta)
export STRIPE_SECRET_KEY="sk_test_your_stripe_secret_key"
export STRIPE_PUBLISHABLE_KEY="pk_test_your_stripe_publishable_key"
export STRIPE_WEBHOOK_SECRET="whsec_your_webhook_secret"
```

---

## Pre-Deployment Checklist

Complete this checklist before every deployment to ensure a smooth rollout.

### Database Preparation

- [ ] **Backup the database**
  ```bash
  PGPASSWORD=$DB_PASSWORD pg_dump -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME \
    > backup_village_calendar_beta_$(date +%Y%m%d_%H%M%S).sql
  ```

- [ ] **Verify database connection**
  ```bash
  PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT version();"
  ```

- [ ] **Check current migration status**
  ```bash
  PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME \
    -c "SELECT id, applied_at, description FROM changelog ORDER BY id DESC LIMIT 5;"
  ```

### Application Preparation

- [ ] **Build and test Docker image locally**
  ```bash
  cd village-calendar
  docker build -t villagecompute/village-calendar:$IMAGE_TAG .
  docker run --rm -p 8030:8030 \
    -e QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME" \
    -e QUARKUS_DATASOURCE_USERNAME="$DB_USER" \
    -e QUARKUS_DATASOURCE_PASSWORD="$DB_PASSWORD" \
    villagecompute/village-calendar:$IMAGE_TAG
  # Test: curl http://localhost:8030/q/health/ready
  ```

- [ ] **Push image to registry**
  ```bash
  docker login -u $REGISTRY_USER -p $REGISTRY_PASSWORD
  docker push villagecompute/village-calendar:$IMAGE_TAG
  ```

- [ ] **Verify image in registry**
  ```bash
  docker manifest inspect villagecompute/village-calendar:$IMAGE_TAG
  ```

### Kubernetes Cluster Preparation

- [ ] **Verify kubectl access**
  ```bash
  kubectl cluster-info
  kubectl get nodes
  ```

- [ ] **Check namespace exists (create if needed)**
  ```bash
  kubectl get namespace calendar-beta || kubectl create namespace calendar-beta
  ```

- [ ] **Validate manifests**
  ```bash
  kubectl apply -f k8s/beta/ --dry-run=client
  ```

- [ ] **Check current deployment status (if exists)**
  ```bash
  kubectl get deployment calendar-app -n calendar-beta
  kubectl get pods -n calendar-beta
  ```

### Secrets and Configuration

- [ ] **Verify all environment variables are set**
  ```bash
  echo "Checking required environment variables..."
  for var in DB_PASSWORD GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET STRIPE_SECRET_KEY R2_ACCESS_KEY R2_SECRET_KEY; do
    if [ -z "${!var}" ]; then
      echo "ERROR: $var is not set!"
    else
      echo "✓ $var is set"
    fi
  done
  ```

- [ ] **Review secrets template**
  ```bash
  cat k8s/beta/secrets.yaml.example
  ```

---

## Deployment Steps

### Option 1: Automated Deployment (Recommended)

Use the Ansible playbook for automated deployment with built-in error handling and rollback.

```bash
# From the villagecompute/infra/ansible directory
ansible-playbook -i inventory.yml deploy-calendar.yml \
  --tags all \
  --extra-vars "environment=beta image_tag=$IMAGE_TAG"
```

The playbook will:
1. Run database migrations
2. Create/update Kubernetes secrets
3. Apply ConfigMap
4. Deploy application manifests
5. Verify rollout status
6. Display deployment summary

### Option 2: Manual Deployment

Follow these steps for manual deployment (useful for understanding the process or troubleshooting):

#### Step 1: Run Database Migrations

```bash
cd village-calendar

# Run MyBatis migrations
mvn mybatis-migrations:up -Penvironment=beta \
  -Ddb.url=jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME \
  -Ddb.username=$DB_USER \
  -Ddb.password=$DB_PASSWORD

# Verify migrations
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME \
  -c "SELECT COUNT(*) as migration_count FROM changelog;"
```

#### Step 2: Create Kubernetes Secrets

```bash
# Create Docker registry secret
kubectl create secret docker-registry docker-registry-secret \
  --docker-server=docker.io \
  --docker-username=$REGISTRY_USER \
  --docker-password=$REGISTRY_PASSWORD \
  --namespace=calendar-beta \
  --dry-run=client -o yaml | kubectl apply -f -

# Create application secrets
kubectl create secret generic calendar-secrets \
  --from-literal=QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME" \
  --from-literal=QUARKUS_DATASOURCE_USERNAME="$DB_USER" \
  --from-literal=QUARKUS_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  --from-literal=GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \
  --from-literal=GOOGLE_CLIENT_SECRET="$GOOGLE_CLIENT_SECRET" \
  --from-literal=FACEBOOK_CLIENT_ID="$FACEBOOK_CLIENT_ID" \
  --from-literal=FACEBOOK_CLIENT_SECRET="$FACEBOOK_CLIENT_SECRET" \
  --from-literal=JWT_PUBLIC_KEY="$JWT_PUBLIC_KEY" \
  --from-literal=JWT_PRIVATE_KEY="$JWT_PRIVATE_KEY" \
  --from-literal=MAIL_FROM="$MAIL_FROM" \
  --from-literal=MAIL_HOST="$MAIL_HOST" \
  --from-literal=MAIL_PORT="$MAIL_PORT" \
  --from-literal=MAIL_USERNAME="$MAIL_USERNAME" \
  --from-literal=MAIL_PASSWORD="$MAIL_PASSWORD" \
  --from-literal=R2_ENDPOINT="$R2_ENDPOINT" \
  --from-literal=R2_BUCKET="$R2_BUCKET" \
  --from-literal=R2_ACCESS_KEY="$R2_ACCESS_KEY" \
  --from-literal=R2_SECRET_KEY="$R2_SECRET_KEY" \
  --from-literal=R2_PUBLIC_URL="$R2_PUBLIC_URL" \
  --from-literal=STRIPE_SECRET_KEY="$STRIPE_SECRET_KEY" \
  --from-literal=STRIPE_PUBLISHABLE_KEY="$STRIPE_PUBLISHABLE_KEY" \
  --from-literal=STRIPE_WEBHOOK_SECRET="$STRIPE_WEBHOOK_SECRET" \
  --namespace=calendar-beta \
  --dry-run=client -o yaml | kubectl apply -f -
```

#### Step 3: Apply Kubernetes Manifests

Apply manifests in the correct order to ensure dependencies are met:

```bash
cd village-calendar/k8s/beta

# 1. Namespace (already created in deployment.yaml)
kubectl apply -f deployment.yaml  # Creates namespace

# 2. ConfigMap
kubectl apply -f configmap.yaml

# 3. Service
kubectl apply -f service.yaml

# 4. Deployment (waits for secrets and configmap)
kubectl apply -f deployment.yaml

# 5. Ingress
kubectl apply -f ingress.yaml
```

#### Step 4: Monitor Rollout

```bash
# Watch deployment rollout
kubectl rollout status deployment/calendar-app -n calendar-beta --timeout=300s

# Watch pod status
kubectl get pods -n calendar-beta -w

# Check events for any issues
kubectl get events -n calendar-beta --sort-by='.lastTimestamp'
```

---

## Post-Deployment Verification

### Health Checks

```bash
# Check pod health
kubectl get pods -n calendar-beta
# Expected output: 2 pods in Running state with 2/2 READY

# Check deployment status
kubectl get deployment calendar-app -n calendar-beta
# Expected: READY 2/2, UP-TO-DATE 2, AVAILABLE 2

# Describe pods for detailed health probe status
kubectl describe pod -l app=calendar-app -n calendar-beta | grep -A 5 "Liveness\|Readiness"
```

### Service Connectivity

```bash
# Verify service endpoints
kubectl get svc calendar-service -n calendar-beta -o wide
kubectl get endpoints calendar-service -n calendar-beta

# Port-forward for local testing
kubectl port-forward svc/calendar-service -n calendar-beta 8030:80
```

### Application Endpoints

Test the following endpoints to ensure the application is functioning:

```bash
# Health endpoints
curl http://localhost:8030/q/health/live
# Expected: {"status": "UP", ...}

curl http://localhost:8030/q/health/ready
# Expected: {"status": "UP", ...}

# GraphQL endpoint
curl -X POST http://localhost:8030/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ __typename }"}'
# Expected: {"data": {"__typename": "Query"}}

# Metrics endpoint
curl http://localhost:8030/q/metrics
# Expected: Prometheus metrics output
```

### Ingress Verification

```bash
# Check ingress configuration
kubectl get ingress calendar-ingress -n calendar-beta
kubectl describe ingress calendar-ingress -n calendar-beta

# Test via public URL (requires DNS and Cloudflare Tunnel setup)
curl https://beta.villagecompute.com/calendar/q/health/ready
# Expected: {"status": "UP", ...}
```

### Database Connection

```bash
# Check database connection from pod
POD_NAME=$(kubectl get pod -l app=calendar-app -n calendar-beta -o jsonpath="{.items[0].metadata.name}")
kubectl exec -it $POD_NAME -n calendar-beta -- sh -c \
  "wget -qO- http://localhost:8030/q/health/ready | grep datasource"
# Expected: datasource check should be UP
```

### End-to-End Smoke Test

Perform a basic order flow test:

1. Navigate to `https://beta.villagecompute.com/calendar`
2. Click "Sign In with Google" and authenticate
3. Browse available calendar templates
4. Add a template to cart
5. Proceed to checkout (use Stripe test card: 4242 4242 4242 4242)
6. Verify order confirmation email is sent
7. Check order appears in admin dashboard

### Log Review

```bash
# View application logs
kubectl logs -l app=calendar-app -n calendar-beta --tail=100 --follow

# Check for errors
kubectl logs -l app=calendar-app -n calendar-beta --since=10m | grep -i error

# View logs from previous deployment (if rollback needed)
kubectl logs -l app=calendar-app -n calendar-beta --previous
```

---

## Rollback Procedure

If issues are detected post-deployment, follow this rollback procedure:

### Quick Rollback (Kubernetes)

```bash
# Rollback to previous deployment revision
kubectl rollout undo deployment/calendar-app -n calendar-beta

# Monitor rollback progress
kubectl rollout status deployment/calendar-app -n calendar-beta

# Verify rollback
kubectl get pods -n calendar-beta
kubectl logs -l app=calendar-app -n calendar-beta --tail=50
```

### Full Rollback (Database + Application)

If database migrations were applied and need to be reverted:

```bash
# 1. Scale down application to prevent database access
kubectl scale deployment calendar-app -n calendar-beta --replicas=0

# 2. Restore database from backup
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME \
  < backup_village_calendar_beta_YYYYMMDD_HHMMSS.sql

# 3. Rollback to previous application version
kubectl set image deployment/calendar-app \
  calendar=villagecompute/village-calendar:previous-tag \
  -n calendar-beta

# 4. Scale application back up
kubectl scale deployment calendar-app -n calendar-beta --replicas=2

# 5. Verify rollback
kubectl get pods -n calendar-beta
curl https://beta.villagecompute.com/calendar/q/health/ready
```

### Rollback Decision Criteria

Initiate rollback if:

- [ ] Pods fail to reach Running state within 5 minutes
- [ ] Health checks (liveness/readiness) continuously fail
- [ ] Database connection errors persist
- [ ] Critical functionality is broken (e.g., authentication, orders, payment processing)
- [ ] Error rate exceeds 10% in logs/metrics
- [ ] User-reported critical bugs affecting beta testers

---

## Troubleshooting

### Common Issues and Solutions

#### Issue: Pods in ImagePullBackOff State

**Symptoms:**
```bash
kubectl get pods -n calendar-beta
# NAME                            READY   STATUS             RESTARTS   AGE
# calendar-app-xxxxx-yyyyy        0/1     ImagePullBackOff   0          2m
```

**Diagnosis:**
```bash
kubectl describe pod calendar-app-xxxxx-yyyyy -n calendar-beta
# Look for "Failed to pull image" or "unauthorized"
```

**Solutions:**
1. Verify image exists in registry:
   ```bash
   docker manifest inspect villagecompute/village-calendar:$IMAGE_TAG
   ```

2. Check Docker registry secret:
   ```bash
   kubectl get secret docker-registry-secret -n calendar-beta
   # If missing, recreate it (see Step 2 of Manual Deployment)
   ```

3. Verify image pull policy:
   ```bash
   kubectl get deployment calendar-app -n calendar-beta -o yaml | grep imagePullPolicy
   # Should be "Always" for latest tag or "IfNotPresent" for versioned tags
   ```

#### Issue: Pods in CrashLoopBackOff State

**Symptoms:**
```bash
kubectl get pods -n calendar-beta
# NAME                            READY   STATUS             RESTARTS   AGE
# calendar-app-xxxxx-yyyyy        0/1     CrashLoopBackOff   5          3m
```

**Diagnosis:**
```bash
# Check container logs
kubectl logs calendar-app-xxxxx-yyyyy -n calendar-beta --tail=100

# Check previous container logs if restarted
kubectl logs calendar-app-xxxxx-yyyyy -n calendar-beta --previous
```

**Common Causes:**

1. **Database connection failure:**
   - Check database credentials in secret
   - Verify database is accessible from cluster
   - Test connection: `kubectl exec -it POD_NAME -n calendar-beta -- sh`

2. **Missing environment variables:**
   ```bash
   kubectl get configmap calendar-config -n calendar-beta -o yaml
   kubectl get secret calendar-secrets -n calendar-beta -o yaml
   ```

3. **Application startup errors:**
   - Review logs for Java exceptions
   - Check for missing OAuth2 credentials
   - Verify JWT keys are properly formatted

#### Issue: Database Connection Failures

**Symptoms:**
- Logs show: `java.sql.SQLException: Connection refused`
- Health check shows datasource DOWN

**Solutions:**

1. Verify database is accessible from cluster:
   ```bash
   kubectl run -it --rm debug --image=postgres:15 --restart=Never -n calendar-beta -- \
     psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME
   ```

2. Check database credentials:
   ```bash
   kubectl get secret calendar-secrets -n calendar-beta -o jsonpath='{.data.QUARKUS_DATASOURCE_PASSWORD}' | base64 -d
   ```

3. Verify network policies allow egress to database:
   ```bash
   kubectl get networkpolicies -n calendar-beta
   ```

4. Check database logs for authentication failures

#### Issue: OAuth2 Callback Errors

**Symptoms:**
- Users see "Redirect URI mismatch" error
- Logs show: `OAuthException: redirect_uri_mismatch`

**Solutions:**

1. Verify OAuth2 redirect URI configuration:
   - Google: https://console.cloud.google.com/apis/credentials
   - Expected: `https://beta.villagecompute.com/calendar/auth/google/callback`

2. Check Ingress rewrite-target annotation:
   ```bash
   kubectl get ingress calendar-ingress -n calendar-beta -o yaml | grep rewrite-target
   ```

3. Test OAuth2 flow manually:
   ```bash
   curl -v https://beta.villagecompute.com/calendar/auth/google
   # Follow redirect and verify callback URL
   ```

#### Issue: Health Checks Failing

**Symptoms:**
- Pods show 0/1 READY
- Readiness probe failures in events

**Diagnosis:**
```bash
kubectl describe pod calendar-app-xxxxx-yyyyy -n calendar-beta | grep -A 10 "Readiness"
kubectl exec -it calendar-app-xxxxx-yyyyy -n calendar-beta -- wget -qO- http://localhost:8030/q/health/ready
```

**Solutions:**

1. Increase `initialDelaySeconds` if application takes longer to start:
   ```yaml
   readinessProbe:
     initialDelaySeconds: 30  # Increase from 10
   ```

2. Check health check dependencies:
   ```bash
   curl http://localhost:8030/q/health/ready | jq
   # Look for DOWN status in datasource, redis, etc.
   ```

3. Verify health check endpoint is accessible:
   ```bash
   kubectl port-forward pod/calendar-app-xxxxx-yyyyy -n calendar-beta 8030:8030
   curl http://localhost:8030/q/health/ready
   ```

#### Issue: Ingress Not Routing Traffic

**Symptoms:**
- 404 errors when accessing `https://beta.villagecompute.com/calendar`
- Ingress shows no backends

**Solutions:**

1. Verify Ingress configuration:
   ```bash
   kubectl describe ingress calendar-ingress -n calendar-beta
   # Check "Backends" shows correct service and endpoints
   ```

2. Check service endpoints:
   ```bash
   kubectl get endpoints calendar-service -n calendar-beta
   # Should show pod IPs
   ```

3. Test service directly:
   ```bash
   kubectl run -it --rm debug --image=curlimages/curl --restart=Never -n calendar-beta -- \
     curl http://calendar-service.calendar-beta.svc.cluster.local/q/health/ready
   ```

4. Verify Ingress controller is running:
   ```bash
   kubectl get pods -n ingress-nginx
   kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx
   ```

---

## Monitoring and Observability

### Metrics

The calendar service exposes Prometheus metrics at `/q/metrics`:

```bash
# Scrape metrics
kubectl port-forward svc/calendar-service -n calendar-beta 8030:80
curl http://localhost:8030/q/metrics
```

**Key Metrics to Monitor:**

- `application_ready_time_seconds` - Application startup time
- `http_server_requests_seconds` - HTTP request latency
- `jvm_memory_used_bytes` - JVM memory usage
- `database_connections_active` - Active database connections
- `calendar_orders_total` - Total calendar orders processed
- `stripe_payment_failures_total` - Failed payment attempts

### Logs

Centralized logging with structured output:

```bash
# Stream logs
kubectl logs -f -l app=calendar-app -n calendar-beta

# Filter by log level
kubectl logs -l app=calendar-app -n calendar-beta | grep "ERROR"

# Search for specific patterns
kubectl logs -l app=calendar-app -n calendar-beta | grep "StripeWebhook"
```

### Distributed Tracing

OpenTelemetry tracing is enabled in beta (configured via ConfigMap):

- **Jaeger UI:** http://jaeger.villagecompute.com (internal)
- **Trace context:** Automatically propagated to database queries and external API calls

### Alerting

Set up alerts for:

- Pod restart count > 3 in 5 minutes
- Memory usage > 90% of limit
- Response time P95 > 2 seconds
- Error rate > 5%
- Database connection pool exhaustion

---

## Additional Resources

- **Architecture Documentation:** `docs/architecture.md`
- **API Documentation:** https://beta.villagecompute.com/calendar/graphql-ui
- **Infrastructure Plan:** `docs/calendar-infrastructure-plan.md`
- **Kubernetes Dashboard:** http://dashboard.villagecompute.com (internal)

---

**Document Version:** 1.0
**Last Updated:** 2025-10-15
**Maintained By:** VillageCompute DevOps Team
