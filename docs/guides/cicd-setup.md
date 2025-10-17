# CI/CD Setup Guide

This guide provides comprehensive instructions for setting up the Continuous Integration and Continuous Deployment (CI/CD) pipeline for the Village Calendar application using GitHub Actions.

## Table of Contents

1. [Overview](#overview)
2. [Workflows](#workflows)
3. [Required GitHub Secrets](#required-github-secrets)
4. [GitHub Environment Configuration](#github-environment-configuration)
5. [Setting Up a New Repository](#setting-up-a-new-repository)
6. [Testing the Pipeline](#testing-the-pipeline)
7. [Troubleshooting](#troubleshooting)

---

## Overview

The Village Calendar CI/CD pipeline consists of three GitHub Actions workflows:

1. **CI Workflow** (`.github/workflows/ci.yml`) - Runs on every push and pull request to validate code quality
2. **Beta Deployment** (`.github/workflows/deploy-beta.yml`) - Automatically deploys to the beta environment
3. **Production Deployment** (`.github/workflows/deploy-production.yml`) - Deploys to production with manual approval

### Workflow Triggers

| Workflow | Trigger | Environment | Approval Required |
|----------|---------|-------------|-------------------|
| CI | Push/PR to any branch | N/A | No |
| Beta Deployment | Push to `beta` or `main` branch | `calendar-beta` namespace | No |
| Production Deployment | Push to `main` branch | `calendar-prod` namespace | **Yes** |

---

## Workflows

### 1. CI Workflow

**Purpose**: Validate code quality without deploying

**Jobs**:
- `backend-build`: Compile backend code and run JUnit tests
- `frontend-lint`: Run ESLint on Vue.js frontend
- `docker-build`: Build Docker image and push to Docker Hub (depends on backend-build success)

**Docker Image Tags**:
- `villagecompute/calendar-api:sha-<full-git-sha>` (all commits)
- `villagecompute/calendar-api:latest` (only on default branch)

### 2. Beta Deployment Workflow

**Purpose**: Automatically deploy to beta environment for pre-production testing

**Steps**:
1. Build Docker image with tags: `<branch>-<short-sha>`, `<full-sha>`, `beta-latest`
2. Connect to k3s cluster via WireGuard VPN (if enabled)
3. Deploy to `calendar-beta` namespace using Ansible
4. Wait for rollout completion
5. Run smoke tests:
   - Health check at `/q/health/live`
   - Sample GraphQL query (`{ __typename }`)

**Access**: `https://calendar-beta.villagecompute.com`

### 3. Production Deployment Workflow

**Purpose**: Deploy to production with manual approval gate

**Steps**:
1. Build Docker image with tags: `<branch>-<short-sha>`, `<full-sha>`, `latest`
2. **Wait for manual approval** (GitHub Environment protection rule)
3. Connect to k3s cluster via WireGuard VPN (if enabled)
4. Deploy to `calendar-prod` namespace using Ansible
5. Wait for rollout completion
6. Run smoke tests (same as beta)

**Access**: `https://calendar.villagecompute.com`

**Rollback**: If deployment fails, instructions are printed in the GitHub Actions log

---

## Required GitHub Secrets

All secrets must be configured in GitHub repository settings: `Settings > Secrets and variables > Actions > Repository secrets`

### Docker Hub Authentication

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `DOCKER_USERNAME` | Docker Hub username | `villagecompute` |
| `DOCKER_PASSWORD` | Docker Hub access token or password | `dckr_pat_abcd1234...` |

**How to obtain**:
1. Log in to [Docker Hub](https://hub.docker.com/)
2. Go to Account Settings > Security > New Access Token
3. Create token with Read, Write, Delete permissions
4. Copy token value to `DOCKER_PASSWORD` secret

### WireGuard VPN Configuration

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `USE_WIREGUARD` | Enable WireGuard connection (set to `true` or `false`) | `true` |
| `WIREGUARD_ADDRESS` | WireGuard interface IP address (CIDR notation) | `10.50.0.100/24` |
| `WIREGUARD_PRIVATE_KEY` | WireGuard private key for GitHub Actions runner | `gA1bc2Def3GHI...` (44 characters) |
| `WIREGUARD_PEER_PUBLIC_KEY` | Public key of k3s cluster WireGuard peer | `xYz4Abc5Def6GHI...` (44 characters) |
| `WIREGUARD_ENDPOINT` | WireGuard endpoint (hostname:port or IP:port) | `vpn.example.com:51820` or `203.0.113.1:51820` |
| `WIREGUARD_ALLOWED_IPS` | Allowed IP ranges (CIDR notation, comma-separated) | `10.50.0.0/24` |

**How to generate WireGuard keys**:
```bash
# Generate private key
wg genkey

# Generate public key from private key
echo "<private-key>" | wg pubkey
```

**Important Notes**:
- Keys must be exactly 44 characters (Base64 encoded)
- Remove any trailing whitespace or newlines from secret values
- Endpoint must be in format `hostname:port` or `ip:port`

### K3s Cluster Access

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `K3S_HOST` | K3s cluster hostname or IP (public IP if not using WireGuard) | `k3s.example.com` or `203.0.113.1` |
| `K3S_USER` | SSH username for k3s cluster | `ubuntu` or `deploy` |
| `K3S_SSH_PRIVATE_KEY` | SSH private key for k3s cluster access (RSA or Ed25519) | `-----BEGIN OPENSSH PRIVATE KEY-----\n...` |
| `K3S_HOST_KEY` | SSH host key (only if not using WireGuard) | `ssh-rsa AAAAB3NzaC1yc2...` |

**How to obtain SSH host key** (if not using WireGuard):
```bash
ssh-keyscan -t rsa <k3s-host>
```

### Database Configuration

#### Production Database

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `DB_HOST` | PostgreSQL hostname or IP | `postgres.example.com` or `10.50.0.30` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Production database name | `calendar_prod` |
| `DB_USER` | Database username | `calendar_app` |
| `DB_PASSWORD` | Database password | `secure_random_password` |

#### Beta Database (Optional - Separate Database for Beta)

If using a separate database for beta, configure these secrets. Otherwise, beta will use the production database secrets with a different database name (`calendar_beta`).

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `DB_HOST_BETA` | Beta PostgreSQL hostname (optional, falls back to `DB_HOST`) | `postgres-beta.example.com` |
| `DB_PORT_BETA` | Beta PostgreSQL port (optional, falls back to `DB_PORT`) | `5432` |
| `DB_NAME_BETA` | Beta database name (optional, defaults to `calendar_beta`) | `calendar_beta` |
| `DB_USER_BETA` | Beta database username (optional, falls back to `DB_USER`) | `calendar_app_beta` |
| `DB_PASSWORD_BETA` | Beta database password (optional, falls back to `DB_PASSWORD`) | `beta_password` |

### Object Storage (Cloudflare R2)

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `OBJECT_STORAGE_REGION` | R2 region (usually `auto`) | `auto` |
| `OBJECT_STORAGE_BUCKET_NAME` | Production R2 bucket name | `village-calendar-uploads` |
| `OBJECT_STORAGE_BUCKET_NAME_BETA` | Beta R2 bucket name (optional, falls back to production bucket) | `village-calendar-uploads-beta` |
| `OBJECT_STORAGE_ENDPOINT_URL` | R2 S3-compatible endpoint | `https://<account-id>.r2.cloudflarestorage.com` |
| `OBJECT_STORAGE_CDN_URL` | R2 public CDN URL | `https://uploads.villagecompute.com` |
| `OBJECT_STORAGE_ACCESS_KEY` | R2 access key ID | `abc123...` |
| `OBJECT_STORAGE_SECRET_KEY` | R2 secret access key | `xyz789...` |

**How to obtain R2 credentials**:
1. Log in to Cloudflare Dashboard
2. Go to R2 > Overview
3. Click "Manage R2 API Tokens"
4. Create a new API token with appropriate permissions
5. Copy Access Key ID and Secret Access Key

### Session Configuration

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `SESSION_ENCRYPTION_KEY` | Production session encryption key (32 bytes, hex-encoded) | `abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789` |
| `SESSION_ENCRYPTION_KEY_BETA` | Beta session encryption key (optional, falls back to production key) | `fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210` |

**How to generate**:
```bash
# Generate 32 random bytes as hex string (64 characters)
openssl rand -hex 32
```

---

## GitHub Environment Configuration

### Creating the Production Environment with Manual Approval

The production deployment workflow uses a GitHub Environment named `production` to enforce manual approval before deployment.

**Steps**:

1. Go to your GitHub repository
2. Click **Settings** > **Environments**
3. Click **New environment**
4. Name: `production`
5. Click **Configure environment**
6. Enable **Required reviewers**
7. Add team members who should approve production deployments (minimum 1 required)
8. Optionally configure:
   - **Wait timer**: Delay deployment by X minutes after approval
   - **Deployment branches**: Limit to `main` branch only
9. Click **Save protection rules**

**Testing the Approval Gate**:
1. Push a commit to `main` branch
2. Production deployment workflow will start
3. Workflow will pause at the `deploy` job with status "Waiting for approval"
4. Designated reviewers will receive notification
5. Reviewer must approve in GitHub Actions UI before deployment proceeds

### Creating the Beta Environment (Optional)

If you want visibility into beta deployments in the GitHub UI, create a `beta` environment (no approval required):

1. Settings > Environments > New environment
2. Name: `beta`
3. No protection rules needed (auto-deploy)
4. Click **Save**

---

## Setting Up a New Repository

Follow these steps to set up the CI/CD pipeline in a fresh repository:

### 1. Clone the Repository

```bash
git clone https://github.com/villagecompute/village-calendar.git
cd village-calendar
```

### 2. Configure GitHub Secrets

Go to `Settings > Secrets and variables > Actions > New repository secret` and add all secrets listed in [Required GitHub Secrets](#required-github-secrets).

**Recommended order**:
1. Docker Hub secrets (to enable Docker image push)
2. K3s cluster access secrets (SSH and WireGuard)
3. Database secrets
4. Object storage secrets
5. Session encryption keys

### 3. Create GitHub Environments

Follow steps in [GitHub Environment Configuration](#github-environment-configuration) to create:
- `production` environment with required reviewers
- `beta` environment (optional)

### 4. Verify k3s Namespaces Exist

SSH into your k3s cluster and ensure the deployment namespaces exist:

```bash
ssh <k3s-user>@<k3s-host>

# Check if namespaces exist
kubectl get namespace calendar-beta
kubectl get namespace calendar-prod

# Create if missing
kubectl create namespace calendar-beta
kubectl create namespace calendar-prod
```

### 5. Test WireGuard Connection (Optional)

If using WireGuard, test the connection locally before relying on it in GitHub Actions:

```bash
# On your local machine (Linux/macOS)
sudo apt-get install wireguard-tools  # Ubuntu/Debian
brew install wireguard-tools           # macOS

# Create test config
sudo nano /etc/wireguard/wg0.conf

# Paste:
[Interface]
Address = <WIREGUARD_ADDRESS>
PrivateKey = <WIREGUARD_PRIVATE_KEY>

[Peer]
PublicKey = <WIREGUARD_PEER_PUBLIC_KEY>
Endpoint = <WIREGUARD_ENDPOINT>
AllowedIPs = <WIREGUARD_ALLOWED_IPS>
PersistentKeepalive = 25

# Test connection
sudo wg-quick up wg0
ping 10.50.0.20  # Replace with your k3s private IP
sudo wg-quick down wg0
```

### 6. Trigger Test Deployment

Create a test commit to trigger the CI workflow:

```bash
# Create a test change
echo "# CI/CD Test" >> README.md
git add README.md
git commit -m "test: verify CI/CD pipeline"
git push origin main
```

Check GitHub Actions tab to verify:
- CI workflow runs successfully
- Docker image is pushed to Docker Hub
- Beta deployment completes (if pushing to `main`)
- Production deployment waits for approval

---

## Testing the Pipeline

### 1. Test CI Workflow (Code Validation)

**Trigger**: Push to any branch or create a pull request

```bash
git checkout -b test-ci
echo "// test change" >> src/main/java/com/villagecompute/calendar/Application.java
git add .
git commit -m "test: trigger CI workflow"
git push origin test-ci
```

**Expected Results**:
- `backend-build` job compiles code and runs tests
- `frontend-lint` job runs ESLint
- `docker-build` job builds and pushes image with SHA tag (not `latest`)

**Check**:
- Go to GitHub Actions tab
- Click on the workflow run
- Verify all jobs show green checkmarks

### 2. Test Beta Deployment

**Trigger**: Push to `beta` branch

```bash
git checkout -b beta
git push origin beta
```

**Expected Results**:
- Build job creates Docker image
- Deploy job connects to k3s via WireGuard (if enabled)
- Ansible deploys to `calendar-beta` namespace
- Smoke tests pass

**Verify Deployment**:
```bash
# SSH to k3s cluster
ssh <k3s-user>@<k3s-host>

# Check pods in beta namespace
kubectl get pods -n calendar-beta

# Check deployment status
kubectl rollout status deployment/calendar -n calendar-beta

# Test health endpoint
kubectl run test-curl --rm -i --restart=Never --image=curlimages/curl -- \
  curl http://calendar-service.calendar-beta.svc.cluster.local/q/health/live
```

### 3. Test Production Deployment with Approval

**Trigger**: Push to `main` branch

```bash
git checkout main
git merge beta
git push origin main
```

**Expected Results**:
1. Build job completes
2. Deploy job shows "Waiting for approval" status
3. Designated reviewer receives notification
4. After approval, deployment proceeds
5. Smoke tests pass

**Approve Deployment**:
1. Go to GitHub Actions tab
2. Click on the workflow run
3. Click "Review deployments"
4. Select `production` environment
5. Click "Approve and deploy"

**Verify Deployment**:
```bash
# Check production pods
kubectl get pods -n calendar-prod

# Check deployment status
kubectl rollout status deployment/calendar -n calendar-prod
```

### 4. Test Rollback (If Deployment Fails)

If smoke tests fail or deployment has issues:

```bash
# SSH to k3s cluster
ssh <k3s-user>@<k3s-host>

# Rollback to previous version
kubectl rollout undo deployment/calendar -n calendar-prod

# Verify rollback
kubectl rollout status deployment/calendar -n calendar-prod
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. WireGuard Connection Fails

**Symptoms**:
- "Cannot resolve hostname" error
- "Connection timeout" during ping
- "Invalid endpoint format" error

**Solutions**:

**Issue**: Endpoint DNS resolution fails
```bash
# Check if endpoint resolves
getent hosts vpn.example.com

# Use IP address instead of hostname in WIREGUARD_ENDPOINT secret
# Example: 203.0.113.1:51820 instead of vpn.example.com:51820
```

**Issue**: Whitespace in secret values
```bash
# Secrets must not contain trailing newlines or spaces
# Re-create secrets by copying value directly without extra whitespace
```

**Issue**: WireGuard keys invalid length
```bash
# Keys must be exactly 44 characters (Base64)
# Regenerate keys if needed:
wg genkey  # Should output 44 characters
```

**Issue**: Firewall blocking WireGuard port
```bash
# On k3s server, ensure UDP port 51820 is open:
sudo ufw allow 51820/udp
```

#### 2. Docker Hub Authentication Fails

**Symptoms**:
- "unauthorized: authentication required" error
- "denied: requested access to the resource is denied"

**Solutions**:

**Issue**: Invalid Docker Hub credentials
```bash
# Test credentials locally:
echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin

# If fails, regenerate Docker Hub access token:
# 1. Go to https://hub.docker.com/settings/security
# 2. Delete old token
# 3. Create new token with Read, Write, Delete permissions
# 4. Update DOCKER_PASSWORD secret in GitHub
```

**Issue**: Docker Hub rate limiting
```bash
# Ensure you're logged in (authenticated users have higher rate limits)
# Consider using GitHub Container Registry (ghcr.io) instead of Docker Hub
```

#### 3. Ansible Deployment Fails

**Symptoms**:
- "Failed to connect to host" error
- "Permission denied (publickey)" error
- "kubectl: command not found"

**Solutions**:

**Issue**: SSH key not accepted
```bash
# Verify SSH key format in K3S_SSH_PRIVATE_KEY secret:
# - Must include header: -----BEGIN OPENSSH PRIVATE KEY-----
# - Must include footer: -----END OPENSSH PRIVATE KEY-----
# - Must preserve exact newlines (use multiline secret)

# Test SSH connection locally:
ssh -i ~/.ssh/id_rsa <k3s-user>@<k3s-host>
```

**Issue**: kubectl not in PATH
```bash
# On k3s server, create symlink:
sudo ln -s /usr/local/bin/k3s /usr/local/bin/kubectl

# Or install kubectl:
sudo snap install kubectl --classic
```

**Issue**: Ansible cannot find playbook
```bash
# Verify ansible/deploy-calendar.yml exists in repository
ls -la ansible/deploy-calendar.yml

# Check workflow checkout step completed successfully
```

#### 4. Smoke Tests Fail

**Symptoms**:
- "Health check failed with status 000" or "404"
- "GraphQL query failed"

**Solutions**:

**Issue**: Application not ready yet
```bash
# Increase initial delay in Ansible playbook (ansible/deploy-calendar.yml):
# livenessProbe.initialDelaySeconds: 60 (instead of 30)
# readinessProbe.initialDelaySeconds: 30 (instead of 10)
```

**Issue**: Service name mismatch
```bash
# Verify service exists in namespace:
kubectl get svc -n calendar-prod

# Ensure service name matches smoke test:
# Expected: calendar-service.calendar-prod.svc.cluster.local
```

**Issue**: Application port mismatch
```bash
# Verify application is listening on port 8030 (configured in application.properties)
# Note: The Village Calendar application uses port 8030 internally.
# The Kubernetes Service exposes this as port 80 externally.
# If port is changed, update Ansible manifest containerPort and health check port
```

#### 5. Namespace Does Not Exist

**Symptoms**:
- "namespace not found" error during deployment

**Solution**:
```bash
# SSH to k3s cluster
ssh <k3s-user>@<k3s-host>

# Create missing namespaces
kubectl create namespace calendar-beta
kubectl create namespace calendar-prod
```

#### 6. GitHub Environment Approval Not Triggered

**Symptoms**:
- Production deployment proceeds without waiting for approval
- No "Review deployments" button in GitHub Actions UI

**Solution**:
```bash
# Verify environment configuration:
# 1. Go to Settings > Environments > production
# 2. Ensure "Required reviewers" is checked
# 3. At least one reviewer is listed
# 4. Save changes

# Verify workflow uses correct environment name:
# In .github/workflows/deploy-production.yml:
# jobs.deploy.environment must be "production" (lowercase)
```

#### 7. Frontend Linting Fails

**Symptoms**:
- "ESLint errors found" in CI workflow
- "npm ERR! missing script: lint"

**Solutions**:

**Issue**: Lint script not defined
```bash
# Verify src/main/webui/package.json contains:
# "scripts": {
#   "lint": "eslint . --ext .vue,.js,.jsx,.cjs,.mjs --fix --ignore-path .gitignore"
# }
```

**Issue**: ESLint errors in code
```bash
# Run lint locally to see errors:
cd src/main/webui
npm run lint

# Auto-fix issues:
npm run lint -- --fix

# Commit fixes:
git add .
git commit -m "fix: resolve ESLint errors"
```

#### 8. Maven Build Fails

**Symptoms**:
- "Compilation failure" in backend-build job
- "Tests in error" or "Tests failure"

**Solutions**:

**Issue**: Dependencies not resolved
```bash
# Clear Maven cache and rebuild:
./mvnw clean install -DskipTests

# Check for dependency conflicts:
./mvnw dependency:tree
```

**Issue**: Test failures
```bash
# Run tests locally to debug:
./mvnw test

# Skip tests temporarily (NOT recommended for production):
./mvnw package -DskipTests
```

---

## Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [WireGuard Quick Start](https://www.wireguard.com/quickstart/)
- [Ansible Kubernetes Module](https://docs.ansible.com/ansible/latest/collections/kubernetes/core/k8s_module.html)
- [Quarkus Container Images](https://quarkus.io/guides/container-image)
- [K3s Documentation](https://docs.k3s.io/)

---

## Support

If you encounter issues not covered in this guide:

1. Check GitHub Actions workflow logs for detailed error messages
2. Review Ansible playbook execution output
3. SSH to k3s cluster and check pod logs: `kubectl logs -n <namespace> <pod-name>`
4. Contact the DevOps team or open an issue in the repository

---

**Last Updated**: 2025-10-17
**Maintained By**: Village Compute DevOps Team
