# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create GitHub Actions workflow for continuous integration and deployment. Workflow should trigger on push to main branch and pull requests. CI jobs: (1) Backend - Maven compile, run unit tests, build JAR; (2) Frontend - npm install, run linting (ESLint), build production bundle; (3) Docker - build Docker image, push to Docker Hub (villagecompute/calendar-api:${GIT_SHA} and :latest tags). CD job (production deployment): Connect to k3s cluster via WireGuard VPN, apply Kubernetes manifests (update image tag), wait for rollout completion, run smoke tests (health check, sample GraphQL query). Configure secrets: DOCKER_HUB_TOKEN, K3S_KUBECONFIG, WIREGUARD_PRIVATE_KEY. Create separate workflows for beta and production environments.

---

## Issues Detected

### Critical Issues

*   **Port Mismatch**: The application is configured to run on port **8030** (as specified in `src/main/resources/application.properties`), but the Ansible playbook (`ansible/deploy-calendar.yml`) uses port **8080** throughout. This causes:
    - Container port is set to 8080 (line 135)
    - Health check probes target port 8080 (lines 224, 232)
    - Service targetPort is 8080 (line 257)
    - Smoke tests in deployment workflows target port 8080 (not explicitly visible in workflow, but would fail)

*   **Docker Image Name Mismatch**: The task specification requires the Docker image to be named `villagecompute/calendar-api`, but:
    - The Ansible playbook uses `{{ registry_user }}/village-calendar` (line 132 in `ansible/deploy-calendar.yml`)
    - The workflows correctly push to `villagecompute/calendar-api`, but the Ansible playbook pulls from the wrong image name
    - This creates a mismatch where the CI pushes images that the deployment cannot find

*   **Namespace Override Not Working Correctly**: The deployment workflows pass `-e "namespace=${DEPLOYMENT_NAMESPACE}"` to override the namespace, but the Ansible playbook needs to support pulling from the correct Docker image registry path, which is currently hardcoded to use `registry_user` variable.

### Moderate Issues

*   **Documentation Port Error**: The CI/CD setup guide (`docs/guides/cicd-setup.md`) mentions "Application port mismatch" troubleshooting on line 560, but incorrectly states the default Quarkus port is 8080. It should state that the Village Calendar application uses port **8030**.

*   **Smoke Test Assumptions**: The smoke tests in both deployment workflows assume the service is available at `http://calendar-service.${DEPLOYMENT_NAMESPACE}.svc.cluster.local/q/health/live`, but they don't specify the port. Since the service exposes port 80 externally, this should work, but the internal container must be listening on 8030, not 8080.

---

## Best Approach to Fix

You MUST make the following changes to fix all identified issues:

### 1. Update Ansible Playbook (`ansible/deploy-calendar.yml`)

**Changes Required:**

1. **Fix Image Name** (line 132):
   - Change: `image: {{ registry_user }}/village-calendar:{{ image_tag }}`
   - To: `image: villagecompute/calendar-api:{{ image_tag }}`
   - Explanation: Use the task-specified image name consistently

2. **Fix All Port References** (multiple lines):
   - Change all occurrences of port `8080` to `8030`:
     - Line 135: `containerPort: 8080` → `containerPort: 8030`
     - Line 224: `port: 8080` (livenessProbe) → `port: 8030`
     - Line 232: `port: 8080` (readinessProbe) → `port: 8030`
     - Line 257: `targetPort: 8080` → `targetPort: 8030`
   - Explanation: The application runs on port 8030 as configured in `application.properties`

3. **Remove Environment Variable Override** (line 211):
   - Remove or comment out: `- name: QUARKUS_HTTP_PORT` and `value: "8080"`
   - Explanation: Let the application use its configured port (8030) from application.properties

### 2. Update Documentation (`docs/guides/cicd-setup.md`)

**Changes Required:**

1. **Fix Port Documentation** (around line 560-565):
   - Change the text that says "Verify application is listening on port 8080 (default Quarkus port)"
   - To: "Verify application is listening on port 8030 (configured in application.properties)"
   - Add clarification: "Note: The Village Calendar application uses port 8030 internally. The Kubernetes Service exposes this as port 80 externally."

### 3. Verify Smoke Tests (No Changes Needed, But Verify)

The smoke tests in `.github/workflows/deploy-beta.yml` and `.github/workflows/deploy-production.yml` should work correctly once the Ansible playbook is fixed, because:
- They target the Kubernetes Service at port 80 (default HTTP)
- The Service forwards to container port 8030
- Health endpoint is at `/q/health/live`

**Verification Step:** After fixing the Ansible playbook, verify that the smoke test curl commands use the correct URL format:
```bash
curl http://calendar-service.${DEPLOYMENT_NAMESPACE}.svc.cluster.local/q/health/live
```
(This is correct - no port needed because Service listens on port 80)

### 4. Test the Fixes

After making these changes, you MUST verify:

1. **Ansible Playbook Syntax**:
   ```bash
   ansible-playbook --syntax-check ansible/deploy-calendar.yml
   ```

2. **YAML Validity**:
   ```bash
   python3 -c "import yaml; yaml.safe_load(open('ansible/deploy-calendar.yml'))"
   ```

3. **Grep Verification**:
   ```bash
   # Verify no references to 8080 remain in Ansible playbook
   grep -n "8080" ansible/deploy-calendar.yml
   # Expected: No results (or only in comments)

   # Verify 8030 is used consistently
   grep -n "8030" ansible/deploy-calendar.yml
   # Expected: Multiple results (containerPort, health probes, Service targetPort)

   # Verify correct image name
   grep -n "calendar-api" ansible/deploy-calendar.yml
   # Expected: Line showing "image: villagecompute/calendar-api:{{ image_tag }}"
   ```

### Summary of Required Changes

**File: `ansible/deploy-calendar.yml`**
- Line 132: Change image to `villagecompute/calendar-api:{{ image_tag }}`
- Line 135: Change `containerPort: 8080` to `containerPort: 8030`
- Line 211: Remove `QUARKUS_HTTP_PORT` environment variable override (or set to 8030)
- Line 224: Change liveness probe port from `8080` to `8030`
- Line 232: Change readiness probe port from `8080` to `8030`
- Line 257: Change Service targetPort from `8080` to `8030`

**File: `docs/guides/cicd-setup.md`**
- Around line 560-565: Update documentation to reflect correct port (8030, not 8080)

**Verification Commands:**
```bash
# Check for remaining 8080 references (should be none after fix)
grep -r "8080" ansible/deploy-calendar.yml

# Verify correct image name
grep "villagecompute/calendar-api" ansible/deploy-calendar.yml

# Validate YAML syntax
python3 -c "import yaml; yaml.safe_load(open('ansible/deploy-calendar.yml'))"
```

Once these changes are complete, all workflows should function correctly:
- CI workflow builds and pushes `villagecompute/calendar-api:${GIT_SHA}`
- Deployment workflows pull the correct image
- Health checks target the correct port (8030)
- Smoke tests pass successfully
