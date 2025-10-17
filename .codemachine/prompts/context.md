# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T5",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Generate PlantUML deployment diagram showing production infrastructure topology. Diagram must include: Cloudflare Edge Network (CDN, Tunnel, R2 storage), Kubernetes Cluster (k3s on Proxmox) with namespaces, Deployment nodes (calendar-api deployment with 2-10 replicas, calendar-worker deployment with 1-5 replicas), Kubernetes Service (load balancer), Observability pods (Jaeger, Prometheus), Database VM (PostgreSQL on separate Proxmox VM), External systems (Stripe API, OAuth Providers, Email Service), User devices (browser). Show network relationships and protocols (HTTPS, JDBC, S3 API, SMTP).",
  "agent_type_hint": "DiagrammingAgent",
  "inputs": "Deployment architecture from Plan Section 3.9.3 \"Deployment Diagram\", Infrastructure stack from Plan Section 2",
  "target_files": ["docs/diagrams/deployment_diagram.puml"],
  "input_files": [],
  "deliverables": "PlantUML deployment diagram file rendering correctly, Diagram shows complete production topology with Cloudflare, k3s, external services, PNG export of diagram (docs/diagrams/deployment_diagram.png)",
  "acceptance_criteria": "PlantUML file validates and renders without errors, Deployment nodes show replica counts and container details, Network relationships include protocol annotations (HTTPS, JDBC/5432, S3 API), Cloudflare integration clearly depicted (CDN, Tunnel ingress, R2 storage), Diagram matches deployment description in architecture plan",
  "dependencies": ["I1.T1"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: deployment-view (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: deployment-view -->
### 3.9. Deployment View

<!-- anchor: target-environment -->
#### 3.9.1. Target Environment

**Infrastructure:**

- **Compute**: Self-hosted Kubernetes (k3s) on Proxmox virtualization platform
- **Edge Services**: Cloudflare (CDN, DNS, DDoS protection, tunnel ingress, R2 storage)
- **Database**: PostgreSQL 17 on dedicated VM (outside Kubernetes for stability)
- **Observability**: Jaeger and Prometheus deployed as Kubernetes services

**Rationale for Hybrid Approach:**

- **Cost Optimization**: K3s on owned hardware avoids cloud compute fees (significant for MVP budget)
- **Cloudflare Integration**: Leverage Cloudflare's global edge network for DDoS protection and CDN (critical for public-facing app)
- **Database Isolation**: PostgreSQL on VM (not in Kubernetes) prevents accidental deletion during cluster maintenance, simplifies backups

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

<!-- anchor: deployment-diagram -->
#### 3.9.3. Deployment Diagram

**Description:**

This diagram illustrates the production deployment topology, showing how containers are distributed across Kubernetes nodes and how external services integrate via Cloudflare edge.

**Diagram:**

~~~plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Deployment.puml

LAYOUT_WITH_LEGEND()

title Deployment Diagram - Village Calendar (Production)

Deployment_Node(cloudflare_edge, "Cloudflare Edge Network", "Global CDN") {
  Container(cdn, "CDN Cache", "Cloudflare CDN", "Caches static assets, calendar previews")
  Container(tunnel, "Cloudflare Tunnel", "cloudflared", "Secure ingress to k3s cluster")
  ContainerDb(r2, "R2 Object Storage", "Cloudflare R2", "Stores PDFs, images")
}

Deployment_Node(k3s_cluster, "Kubernetes Cluster (k3s on Proxmox)", "On-Premises") {

  Deployment_Node(namespace_prod, "Namespace: calendar-prod", "Kubernetes Namespace") {

    Deployment_Node(api_deployment, "Deployment: calendar-api", "2-10 replicas") {
      Container(api_pod1, "API Pod 1", "Quarkus + Vue SPA", "Handles GraphQL/REST requests")
      Container(api_pod2, "API Pod 2", "Quarkus + Vue SPA", "Handles GraphQL/REST requests")
    }

    Deployment_Node(worker_deployment, "Deployment: calendar-worker", "1-5 replicas") {
      Container(worker_pod1, "Worker Pod 1", "Quarkus", "Processes DelayedJob queue")
    }

    Container(service_lb, "Service: calendar-api-svc", "Kubernetes ClusterIP", "Load balances across API pods")

    Deployment_Node(observability, "Observability Stack", "Kubernetes") {
      Container(jaeger_pod, "Jaeger", "All-in-One", "Trace collector & UI")
      Container(prometheus_pod, "Prometheus", "Server", "Metrics scraper & storage")
    }
  }
}

Deployment_Node(database_vm, "Database VM", "Proxmox VM (Ubuntu)") {
  ContainerDb(postgres, "PostgreSQL 17", "PostgreSQL + PostGIS", "Primary data store")
}

System_Ext(stripe_api, "Stripe API", "Payment processing")
System_Ext(oauth_providers, "OAuth Providers", "Google, Facebook, Apple")
System_Ext(email_service, "Email Service", "GoogleWorkspace SMTP")

Deployment_Node(user_device, "User Device", "Browser") {
  Container(browser, "Web Browser", "Chrome/Firefox/Safari", "Runs Vue.js SPA")
}

Rel(browser, cdn, "Loads static assets", "HTTPS")
Rel(browser, tunnel, "API requests", "HTTPS/GraphQL")

Rel(tunnel, service_lb, "Forwards requests", "HTTP/1.1")
Rel(service_lb, api_pod1, "Routes traffic", "HTTP")
Rel(service_lb, api_pod2, "Routes traffic", "HTTP")

Rel(api_pod1, postgres, "Reads/writes data", "JDBC/5432")
Rel(api_pod2, postgres, "Reads/writes data", "JDBC/5432")
Rel(worker_pod1, postgres, "Polls job queue", "JDBC/5432")

Rel(api_pod1, r2, "Uploads PDFs", "S3 API/HTTPS")
Rel(worker_pod1, r2, "Stores generated PDFs", "S3 API/HTTPS")
Rel(cdn, r2, "Fetches cached content", "Internal")

Rel(api_pod1, stripe_api, "Creates checkout sessions", "HTTPS/REST")
Rel(api_pod2, stripe_api, "Creates checkout sessions", "HTTPS/REST")
Rel(api_pod1, oauth_providers, "Validates OAuth tokens", "HTTPS/OIDC")
Rel(api_pod1, email_service, "Sends emails", "SMTP/587/TLS")
Rel(worker_pod1, email_service, "Sends async emails", "SMTP/587/TLS")

Rel(api_pod1, jaeger_pod, "Sends traces", "gRPC/4317")
Rel(worker_pod1, jaeger_pod, "Sends traces", "gRPC/4317")
Rel(prometheus_pod, api_pod1, "Scrapes /q/metrics", "HTTP/9090")
Rel(prometheus_pod, worker_pod1, "Scrapes /q/metrics", "HTTP/9090")

@enduml
~~~

**Deployment Configuration Details:**

**Resource Allocation (Per Pod):**

| Container | CPU Request | CPU Limit | Memory Request | Memory Limit |
|-----------|-------------|-----------|----------------|--------------|
| API Pod | 500m | 2000m | 512Mi | 2Gi |
| Worker Pod | 1000m | 2000m | 1Gi | 4Gi (PDF rendering) |
| Jaeger | 200m | 500m | 256Mi | 1Gi |
| Prometheus | 500m | 1000m | 1Gi | 4Gi |
```

### Context: artifact-deployment-diagram (from 01_Plan_Overview_and_Setup.md)

```markdown
<!-- anchor: artifact-deployment-diagram -->
### 3. Deployment Diagram (PlantUML)
- **Purpose**: Show Kubernetes topology, Cloudflare integration, external services
- **Format**: PlantUML (C4 Deployment diagram)
- **Location**: `docs/diagrams/deployment_diagram.puml`
- **Created In**: Iteration 1, Task I1.T5
- **Content**: k3s cluster, API/Worker pods, PostgreSQL VM, Cloudflare edge, Stripe/OAuth providers
```

### Context: technology-stack (from 01_Plan_Overview_and_Setup.md)

```markdown
<!-- anchor: technology-stack -->
### Technology Stack

**Backend:**
- Framework: Quarkus 3.26.2
- Runtime: Java 21 (OpenJDK LTS)
- ORM: Hibernate ORM with Panache (active record pattern)
- Database: PostgreSQL 17+ with PostGIS extensions
- API: GraphQL (SmallRye GraphQL) primary, REST (JAX-RS) for webhooks/health checks
- Job Processing: Custom DelayedJob + Vert.x EventBus, Quarkus Scheduler
- Authentication: Quarkus OIDC (OAuth 2.0 / OpenID Connect)
- Observability: OpenTelemetry → Jaeger (tracing), Micrometer → Prometheus (metrics)
- PDF Generation: Apache Batik 1.17 (SVG to PDF)
- Astronomical Calcs: SunCalc (port), Proj4J 4.1

**Frontend:**
- Framework: Vue 3.5+ (Composition API)
- UI Library: PrimeVue 4.2+ (Aura theme)
- Icons: PrimeIcons 7.0+
- CSS: TailwindCSS 4.0+
- State Management: Pinia
- Routing: Vue Router 4.5+
- I18n: Vue I18n (future localization)
- Build Tool: Vite 6.1+
- TypeScript: ~5.7.3
- Integration: Quinoa plugin (Quarkus-Vue seamless integration)

**Infrastructure:**
- Container Runtime: Docker
- Orchestration: Kubernetes (k3s on Proxmox)
- IaC: Terraform 1.7.4 (Cloudflare, AWS resources)
- Config Management: Ansible
- Database Migrations: MyBatis Migrations
- CDN/Edge: Cloudflare CDN, DNS, DDoS protection
- Tunnel: Cloudflare Tunnel (secure k3s ingress)
- VPN: WireGuard (CI/CD access)
- Object Storage: Cloudflare R2 (S3-compatible)
- Email: GoogleWorkspace SMTP (migrate to AWS SES if needed)
- CI/CD: GitHub Actions

**External Services:**
- Payment Processing: Stripe (Checkout Sessions, webhooks)
- OAuth Providers: Google, Facebook, Apple
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `docs/diagrams/component_diagram.puml`
    *   **Summary:** This file contains the successfully completed C4 Component diagram (Task I1.T3) showing the internal architecture of the Quarkus API application with all services, repositories, and integration components.
    *   **Recommendation:** You MUST use the EXACT same PlantUML conventions and C4 modeling style from this file. It uses the C4-PlantUML standard library (`!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Component.puml`) and follows consistent component naming (e.g., `Component(id, "Name", "Technology", "Description")`).
    *   **Key Pattern:** The diagram uses `@startuml component_diagram` as the start tag and `@enduml` as the end tag. You MUST follow this exact pattern but change "component_diagram" to "deployment_diagram".

*   **File:** `docs/diagrams/database_erd.puml`
    *   **Summary:** This file contains the successfully completed database ERD (Task I1.T4) showing all database entities and relationships for the MVP implementation.
    *   **Recommendation:** This file demonstrates the project's PlantUML style conventions. Note the use of `skinparam` for styling, detailed entity definitions with field types, and comprehensive legends explaining the design.
    *   **Key Pattern:** The ERD includes a detailed `legend bottom` section that explains the schema patterns. Your deployment diagram should also include a legend explaining the deployment topology and infrastructure choices.

*   **File:** `k8s/beta/deployment.yaml`
    *   **Summary:** This file contains the actual Kubernetes deployment manifest for the beta environment, showing the real-world configuration with replicas, health checks, resource limits, and all environment variables.
    *   **Recommendation:** You MUST reference this file to ensure the deployment diagram accurately reflects the ACTUAL implementation. The diagram shows 2 replicas in beta (line 20), ports on 8030 (line 47), and all the actual integrations (OAuth, Stripe, R2, database).
    *   **Key Detail:** The beta deployment uses `namespace: calendar-beta` (line 14), image `teacurran/village-calendar:beta-latest` (line 44), and has specific resource requests (memory: 512Mi, cpu: 500m) and limits (memory: 1Gi, cpu: 1000m) defined in lines 207-213.

*   **File:** `Dockerfile`
    *   **Summary:** This multi-stage Dockerfile builds the Quarkus application with integrated Vue.js frontend using Quinoa, creating a JRE-based runtime image.
    *   **Recommendation:** The deployment diagram should reflect that the API pods run "Quarkus + Vue SPA" as a single container (not separate frontend/backend containers), built from this Dockerfile and exposed on port 8030 (line 59).
    *   **Key Detail:** The container runs as a non-root user `quarkus` (lines 47, 56) on Alpine Linux with Java 21 JRE (line 42), which demonstrates security best practices you should note in the diagram annotations.

*   **File:** `docs/DEPLOYMENT.md`
    *   **Summary:** This comprehensive deployment runbook documents the actual deployment procedures, infrastructure details, and operational practices for the Village Calendar service.
    *   **Recommendation:** Use this document to verify infrastructure details like database host (`10.50.0.10`), namespace names (`calendar-beta`, future `calendar-prod`), and external service integrations. The diagram MUST align with the documented infrastructure.
    *   **Key Detail:** The runbook confirms beta URL `https://beta.villagecompute.com/calendar/*` (line 24), PostgreSQL database name `village_calendar_beta` (line 25), Cloudflare R2 bucket name `village-calendar-beta` (line 88), and Stripe test keys usage in beta (line 94).

### Implementation Tips & Notes

*   **Tip:** The architecture document at `.codemachine/artifacts/architecture/05_Operational_Architecture.md` (lines 653-727) contains a COMPLETE PlantUML deployment diagram example. You SHOULD use this as your PRIMARY reference and starting template. It already has all the correct structure and relationships defined.

*   **Note:** The existing PlantUML diagrams (component_diagram.puml and database_erd.puml) both successfully use the C4 model standard library. Your deployment diagram MUST use `!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Deployment.puml` to maintain consistency.

*   **Important Pattern:** Both existing diagrams use descriptive titles and legends. Your diagram MUST include:
    - A clear title: `title Deployment Diagram - Village Calendar (Production)`
    - A legend explaining the hybrid infrastructure approach (k3s on Proxmox + Cloudflare edge)
    - Annotations on resource allocations (API pods: 512Mi-2Gi memory, 500m-2000m CPU)

*   **Critical Detail:** The architecture specifies TWO separate deployments within the k3s cluster:
    1. `calendar-api` deployment (2-10 replicas, stateless, handles HTTP/GraphQL requests)
    2. `calendar-worker` deployment (1-5 replicas, processes DelayedJob queue for PDF generation)

    You MUST show both deployments as separate `Deployment_Node` blocks in the diagram, not just the API.

*   **Warning:** The database is NOT running in Kubernetes. It's a PostgreSQL 17 instance on a SEPARATE Proxmox VM (as noted in line 588 of the architecture doc: "Database: PostgreSQL 17 on dedicated VM (outside Kubernetes for stability)"). Your diagram MUST show this as a separate `Deployment_Node(database_vm, "Database VM", "Proxmox VM (Ubuntu)")` OUTSIDE the k3s_cluster node.

*   **Protocol Annotations:** The acceptance criteria requires "Network relationships include protocol annotations (HTTPS, JDBC/5432, S3 API)". The existing example diagram (lines 701-725) shows the correct pattern using `Rel()` statements with three parameters: source, target, description with protocol (e.g., `Rel(api_pod1, postgres, "Reads/writes data", "JDBC/5432")`). You MUST follow this exact pattern.

*   **Observability Stack:** The diagram MUST show Jaeger and Prometheus as separate containers within the `namespace_prod` deployment node (see lines 682-685 in the architecture example). These pods collect traces and metrics from the API and worker pods via gRPC (port 4317 for Jaeger) and HTTP (port 9090 for Prometheus scraping).

*   **Cloudflare R2 Storage:** The diagram MUST show R2 as a `ContainerDb(r2, "R2 Object Storage", "Cloudflare R2", "Stores PDFs, images")` within the Cloudflare Edge Network node. Both API pods AND worker pods interact with R2 using the S3 API over HTTPS (see lines 712-714).

*   **Resource Constraints:** The architecture document specifies exact resource allocations in a table (lines 244-252). While you don't need to show these in the main diagram, you SHOULD add annotations or a note explaining that API pods request 512Mi memory and can scale up to 2Gi under load, while worker pods need more memory (1Gi-4Gi) for PDF rendering.

*   **File Location:** The task specifies the output file MUST be at `docs/diagrams/deployment_diagram.puml` (already verified from the plan manifest). After creating the diagram, you SHOULD generate a PNG export using PlantUML to `docs/diagrams/deployment_diagram.png` for documentation purposes.

*   **Validation:** Before completing the task, you MUST validate the PlantUML syntax by running it through a PlantUML renderer or IDE plugin. The diagram must render without errors. Common issues to watch for:
    - Missing closing braces `}` for deployment nodes
    - Incorrect relationship syntax (should be `Rel(source, target, "description", "protocol")`)
    - Mismatched component IDs in relationships (e.g., referencing `api_pod` when you defined `api_pod1`)

*   **Beta vs Production:** While the architectural diagram shows the "Production" environment, the actual implemented configuration in `k8s/beta/` is for beta. The diagram structure is identical, but you should note in comments or legend that both environments use the same topology, with beta having `calendar-beta` namespace and production having `calendar-prod`.

*   **Port Numbers:** The beta deployment actually uses port 8030 (not 8080 as shown in the architecture example). You should use port 8030 in your diagram to match the actual implementation, or add a note that production will use standard port 8080.
