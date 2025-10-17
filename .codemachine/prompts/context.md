# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T12",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Create GitHub Actions workflow for continuous integration and deployment. Workflow should trigger on push to main branch and pull requests. CI jobs: (1) Backend - Maven compile, run unit tests, build JAR; (2) Frontend - npm install, run linting (ESLint), build production bundle; (3) Docker - build Docker image, push to Docker Hub (villagecompute/calendar-api:${GIT_SHA} and :latest tags). CD job (production deployment): Connect to k3s cluster via WireGuard VPN, apply Kubernetes manifests (update image tag), wait for rollout completion, run smoke tests (health check, sample GraphQL query). Configure secrets: DOCKER_HUB_TOKEN, K3S_KUBECONFIG, WIREGUARD_PRIVATE_KEY. Create separate workflows for beta and production environments.",
  "agent_type_hint": "SetupAgent",
  "inputs": "CI/CD requirements from Plan Section 3.9.2, GitHub Actions documentation, Existing WireGuard VPN setup to k3s cluster",
  "target_files": [
    ".github/workflows/ci.yml",
    ".github/workflows/deploy-beta.yml",
    ".github/workflows/deploy-production.yml",
    "docs/guides/cicd-setup.md"
  ],
  "input_files": [
    "pom.xml",
    "frontend/package.json",
    "Dockerfile"
  ],
  "deliverables": "GitHub Actions CI workflow runs on every push/PR, CI workflow compiles backend, builds frontend, creates Docker image, Beta deployment workflow deploys to calendar-beta namespace on k3s, Production deployment workflow requires manual approval, deploys to calendar-prod namespace, CICD setup guide with secret configuration instructions",
  "acceptance_criteria": "Push to main branch triggers CI workflow, all jobs pass, Docker image pushed to Docker Hub with correct tags, Beta deployment workflow successfully updates k3s deployment, Production workflow shows approval gate in GitHub Actions UI, Smoke tests pass after deployment (health check returns 200), CICD guide tested with fresh GitHub repository setup",
  "dependencies": [
    "I1.T1",
    "I1.T10"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: deployment-strategy (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: deployment-strategy -->
#### 3.9.2. Deployment Strategy

**Continuous Deployment Pipeline (GitHub Actions):**

1. **Trigger**: Git push to `main` branch or manual workflow dispatch
2. **Build**:
   - Compile Quarkus application (Maven `./mvnw package`)
   - Build Vue.js frontend (Vite `npm run build`, bundled into Quarkus via Quinoa)
   - Run unit tests (JUnit for backend, Vitest for frontend)
   - Build Docker image (Quarkus native or JVM mode)
   - Push image to Docker Hub (`villagecompute/calendar-api:${GIT_SHA}`)
3. **Deploy**:
   - Connect to k3s cluster via WireGuard VPN
   - Apply Kubernetes manifests (Ansible playbook or `kubectl apply`)
   - Update Deployment image tag (`kubectl set image deployment/calendar-api calendar-api=villagecompute/calendar-api:${GIT_SHA}`)
   - Wait for rollout completion (`kubectl rollout status`)
   - Run smoke tests (health check, sample GraphQL query)
4. **Rollback**:
   - If smoke tests fail: `kubectl rollout undo deployment/calendar-api`
   - Notify #deploy-alerts Slack channel

**Deployment Environments:**

- **Beta**: `calendar-beta.villagecompute.com` (Cloudflare Tunnel → k3s namespace `calendar-beta`)
  - Purpose: Pre-production testing, early access features
  - Data: Separate PostgreSQL database (copy of production schema, synthetic test data)
  - Deployment: Automatic on merge to `beta` branch
- **Production**: `calendar.villagecompute.com` (Cloudflare Tunnel → k3s namespace `calendar-prod`)
  - Purpose: Live user traffic
  - Data: Production PostgreSQL database
  - Deployment: Manual approval required (GitHub Actions protected environment)

**Rolling Update Strategy:**

- **Max Surge**: 1 pod (allow N+1 pods during deployment for zero downtime)
- **Max Unavailable**: 0 pods (ensure at least N pods always running)
- **Health Checks**: New pod must pass readiness probe before old pod is terminated
- **Rollout Timeline**: Stagger pod updates by 30 seconds (gradual traffic shift)

**Database Migration Strategy:**

- **Timing**: Migrations run before application deployment (Kubernetes init container or separate job)
- **Locking**: PostgreSQL advisory locks prevent concurrent migrations
- **Backward Compatibility**: Schema changes must support N and N-1 application versions (e.g., add column with default value, deploy app, then remove old column in next release)
- **Rollback**: Down migrations supported in dev/staging, not used in production (forward-only migrations with feature flags)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `/Users/tea/dev/VillageCompute/code/village-calendar/.github/workflows/deploy-k3s-wireguard.yml`
    *   **Summary:** This is an EXISTING production deployment workflow that already implements WireGuard VPN connectivity, Docker image building, and Ansible-based deployment to k3s. It deploys on push to `main` and includes comprehensive WireGuard connection validation, Docker build/push, and Ansible deployment steps.
    *   **Recommendation:** You SHOULD use this file as the **foundation** for your production deployment workflow (`.github/workflows/deploy-production.yml`). This file already contains working WireGuard setup, secret handling, and deployment logic that matches the architecture requirements. You MUST adapt it to add the manual approval gate required by the task specifications.
    *   **Critical Insight:** This workflow uses **Ansible** (`ansible/deploy-calendar.yml`) for deployment orchestration, NOT direct `kubectl` commands. You MUST maintain this pattern for consistency.

*   **File:** `/Users/tea/dev/VillageCompute/code/village-calendar/pom.xml`
    *   **Summary:** Maven POM with comprehensive Quarkus dependencies including Quinoa (for Vue.js integration), GraphQL, OIDC, health checks, metrics, and all required libraries. Build is configured with Java 21, Quarkus 3.26.2, and Surefire for testing.
    *   **Recommendation:** The CI workflow MUST use Maven commands `./mvnw compile` and `./mvnw test` for backend validation. The POM already configures Quinoa to build the Vue frontend during `./mvnw package`, so you do NOT need separate frontend build steps in CI - the frontend is automatically built by Maven via Quinoa.

*   **File:** `/Users/tea/dev/VillageCompute/code/village-calendar/Dockerfile`
    *   **Summary:** Multi-stage Dockerfile using maven:3.9-eclipse-temurin-21 for build stage (includes Node.js installation for frontend build) and eclipse-temurin:21-jre-alpine for runtime. Exposes port 8030 and includes health checks.
    *   **Recommendation:** The CI workflow MUST build the Docker image from this existing Dockerfile. Use `docker build -t <registry>/<image>:<tag> .` as shown in the existing workflow. The image MUST be tagged with both `${GIT_SHA}` and `latest` as per task requirements.

*   **File:** `/Users/tea/dev/VillageCompute/code/village-calendar/src/main/webui/package.json`
    *   **Summary:** Frontend package.json is located at `src/main/webui/package.json` (Quinoa standard location, NOT root-level `frontend/`). Contains ESLint configuration and build scripts.
    *   **Recommendation:** For the CI workflow linting step, you MUST run ESLint from the `src/main/webui` directory. The command should be: `cd src/main/webui && npm install && npm run lint`. Note: The task mentions `frontend/package.json` in input_files but the ACTUAL location is `src/main/webui/package.json` - you MUST use the actual location.

*   **File:** `/Users/tea/dev/VillageCompute/code/village-calendar/ansible/deploy-calendar.yml`
    *   **Summary:** Ansible playbook exists for deployment automation. This is used by the current production workflow.
    *   **Recommendation:** Your deployment workflows (both beta and production) SHOULD continue using the Ansible playbook approach shown in the existing workflow. DO NOT replace it with raw `kubectl` commands unless the architecture explicitly requires it.

### Implementation Tips & Notes

*   **Tip:** The project uses **port 8030** (not 8080) for the Quarkus application. Health check endpoints are at `http://localhost:8030/q/health/live` and `http://localhost:8030/q/health/ready`. Smoke tests in the deployment workflow MUST use port 8030.

*   **Note:** The existing `deploy-k3s-wireguard.yml` workflow already handles:
    - Docker Hub authentication via `DOCKER_USERNAME` and `DOCKER_PASSWORD` secrets
    - WireGuard VPN connection with comprehensive validation
    - SSH key setup for k3s access
    - Ansible playbook execution with environment variable injection
    - Artifact upload for debugging

    You SHOULD reuse this proven infrastructure rather than reimplementing from scratch.

*   **Warning:** The task specification mentions "separate workflows for beta and production environments." The existing workflow deploys to production only. You MUST create THREE workflows:
    1. **`.github/workflows/ci.yml`** - Pure CI (compile, test, lint, build Docker image) triggered on ALL pushes and PRs
    2. **`.github/workflows/deploy-beta.yml`** - Automatic deployment to beta environment (triggered on push to `beta` branch or main branch)
    3. **`.github/workflows/deploy-production.yml`** - Manual approval deployment to production (triggered on push to `main` branch with manual approval gate)

*   **Critical:** The task specifies creating a "CICD setup guide" (`docs/guides/cicd-setup.md`). This documentation MUST include:
    - Required GitHub secrets (DOCKER_USERNAME, DOCKER_PASSWORD, WIREGUARD_*, K3S_*, DB_*, etc.)
    - How to configure GitHub environments for manual approval (production)
    - How to test the workflows locally (if possible)
    - Troubleshooting common issues (WireGuard connection failures, Docker Hub auth, etc.)

*   **Architecture Note:** The architecture document specifies that production deployments require "manual approval" (GitHub Actions protected environment). You MUST configure the production workflow to use a GitHub Environment (e.g., `production`) with required reviewers.

*   **Frontend Linting:** The task explicitly mentions "run linting (ESLint)" as part of CI. The frontend at `src/main/webui` has an `eslint.config.js` file. You MUST add a linting step: `cd src/main/webui && npm run lint` (or `npm run lint:fix` if auto-fixing is desired).

*   **Smoke Test Requirement:** The architecture specifies running "sample GraphQL query" as part of smoke tests. A simple health check is NOT sufficient. You SHOULD include a basic GraphQL query test (e.g., `curl -X POST http://localhost:8030/graphql -H "Content-Type: application/json" -d '{"query":"query { __typename }"}'`) to verify the API is responding correctly.

*   **Docker Registry:** The task specifies pushing to Docker Hub with image name `villagecompute/calendar-api`. The existing workflow uses `${{ secrets.DOCKER_USERNAME }}/village-calendar`. You MUST align on the correct naming: the task specification should take precedence (`villagecompute/calendar-api`), but verify that `villagecompute` is the actual Docker Hub username secret value.

*   **IMPORTANT - Quinoa Build Integration:** The Maven build (`./mvnw package`) automatically builds the frontend via Quinoa. You do NOT need a separate frontend build job in CI. The frontend linting step should run independently, but the actual frontend build happens during Maven package. This is already configured in the POM.

*   **CI Workflow Structure:** For efficiency, the CI workflow should have three parallel jobs:
    1. **backend-build** - Maven compile and test
    2. **frontend-lint** - ESLint check (separate from build)
    3. **docker-build** - Build and push Docker image (depends on backend-build success)

*   **Beta vs Production Namespaces:** The Ansible playbook likely has variables for namespace configuration. You MUST ensure:
    - Beta workflow deploys to namespace `calendar-beta`
    - Production workflow deploys to namespace `calendar-prod`
    - These namespaces must already exist in your k3s cluster, or the playbook must create them

*   **Existing Secrets Note:** The current workflow uses many secrets. Your documentation must clearly list ALL required secrets. Based on the existing workflow, these include:
    - `DOCKER_USERNAME`, `DOCKER_PASSWORD`
    - `K3S_SSH_PRIVATE_KEY`, `K3S_HOST`, `K3S_USER`, `K3S_HOST_KEY`
    - `WIREGUARD_ADDRESS`, `WIREGUARD_PRIVATE_KEY`, `WIREGUARD_PEER_PUBLIC_KEY`, `WIREGUARD_ENDPOINT`, `WIREGUARD_ALLOWED_IPS`, `USE_WIREGUARD`
    - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
    - `OBJECT_STORAGE_*` (R2 credentials)
    - `SESSION_ENCRYPTION_KEY`
