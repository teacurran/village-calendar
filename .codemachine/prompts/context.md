# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T1",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Create the foundational project structure for the Village Calendar application. Initialize Maven POM for Quarkus backend, configure Quinoa plugin for Vue.js integration, set up frontend package.json with Vite, and create directory structure as specified in Section 3 of the plan. Configure Quarkus extensions (Hibernate ORM with Panache, SmallRye GraphQL, OIDC, Health, Micrometer, OpenTelemetry). Initialize Vue 3 project with TypeScript, PrimeVue, Pinia, Vue Router, and TailwindCSS.",
  "agent_type_hint": "SetupAgent",
  "inputs": "Directory structure specification from Plan Section 3, Technology stack requirements from Plan Section 2, Reference prototype at /Users/tea/dev/VillageCompute/code/VillageCMS/admin (read-only inspection for patterns)",
  "target_files": [
    "pom.xml",
    "src/main/resources/application.properties",
    "frontend/package.json",
    "frontend/vite.config.ts",
    "frontend/tsconfig.json",
    "frontend/tailwind.config.js",
    "frontend/src/main.ts",
    "README.md",
    ".gitignore",
    "Dockerfile"
  ],
  "input_files": [],
  "deliverables": "Compilable Quarkus application (Maven ./mvnw compile succeeds), Buildable Vue frontend (npm run build succeeds), Integrated build via Quinoa (single ./mvnw package produces JAR with embedded frontend), Basic README with local development setup instructions",
  "acceptance_criteria": "./mvnw quarkus:dev starts Quarkus in dev mode, serves Vue app at http://localhost:8080, Vue app displays PrimeVue-styled \"Hello Village Calendar\" placeholder page, Maven build produces executable JAR (target/village-calendar-1.0.0-runner.jar), Dockerfile builds successfully (docker build -t village-calendar:latest .)",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: technology-stack (from 01_Plan_Overview_and_Setup.md)

```markdown
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

### Context: directory-structure (from 01_Plan_Overview_and_Setup.md)

```markdown
## 3. Directory Structure

**Root Directory**: `village-calendar/`

The proposed structure organizes code by functional domain (modular monolith), with clear separation of backend (Quarkus), frontend (Vue), infrastructure (Terraform/Ansible), and documentation/artifacts.

```
village-calendar/
├── src/                                  # Backend source code (Quarkus application)
│   ├── main/
│   │   ├── java/com/villagecompute/calendar/
│   │   │   ├── api/                     # GraphQL/REST controllers
│   │   │   │   ├── graphql/             # GraphQL resolvers
│   │   │   │   │   ├── CalendarResolver.java
│   │   │   │   │   ├── OrderResolver.java
│   │   │   │   │   └── UserResolver.java
│   │   │   │   └── rest/                # REST controllers (webhooks, downloads)
│   │   │   │       ├── StripeWebhookController.java
│   │   │   │       └── PdfDownloadController.java
│   │   │   ├── service/                 # Business logic layer
│   │   │   │   ├── CalendarService.java
│   │   │   │   ├── UserService.java
│   │   │   │   ├── OrderService.java
│   │   │   │   ├── PdfService.java
│   │   │   │   └── EmailService.java
│   │   │   ├── repository/              # Panache repositories (data access)
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── CalendarRepository.java
│   │   │   │   ├── OrderRepository.java
│   │   │   │   └── DelayedJobRepository.java
│   │   │   ├── model/                   # JPA entities
│   │   │   │   ├── User.java
│   │   │   │   ├── Calendar.java
│   │   │   │   ├── Event.java
│   │   │   │   ├── Order.java
│   │   │   │   ├── OrderItem.java
│   │   │   │   ├── Payment.java
│   │   │   │   ├── CalendarTemplate.java
│   │   │   │   └── DelayedJob.java
│   │   │   ├── jobs/                    # Async job implementations
│   │   │   │   ├── DelayedJobExecutor.java
│   │   │   │   ├── PdfGenerationJob.java
│   │   │   │   ├── EmailJob.java
│   │   │   │   └── AnalyticsRollupJob.java
│   │   │   ├── integration/             # External service clients
│   │   │   │   ├── stripe/              # Stripe API client
│   │   │   │   ├── oauth/               # OAuth provider integration
│   │   │   │   ├── email/               # SMTP email sender
│   │   │   │   └── r2/                  # Cloudflare R2 client
│   │   │   ├── security/                # Authentication/authorization
│   │   │   │   ├── JwtTokenValidator.java
│   │   │   │   └── RoleBasedAccessControl.java
│   │   │   └── util/                    # Utility classes
│   │   │       ├── astronomical/        # Moon phase, Hebrew calendar calcs
│   │   │       └── pdf/                 # Batik rendering utilities
│   │   └── resources/
│   │       ├── application.properties   # Quarkus configuration
│   │       ├── application-dev.properties
│   │       ├── application-prod.properties
│   │       └── db/migration/            # Flyway migrations (if using Flyway)
│   └── test/
│       └── java/com/villagecompute/calendar/
│           ├── api/                     # API integration tests
│           ├── service/                 # Service unit tests
│           └── repository/              # Repository tests
├── frontend/                             # Vue.js SPA (integrated via Quinoa)
│   ├── src/
│   │   ├── assets/                      # Static assets (images, fonts)
│   │   ├── components/                  # Vue components
│   │   │   ├── calendar/                # Calendar editor components
│   │   │   │   ├── CalendarEditor.vue
│   │   │   │   ├── EventPicker.vue
│   │   │   │   └── EmojiSelector.vue
│   │   │   ├── admin/                   # Admin panel components
│   │   │   ├── checkout/                # Checkout flow components
│   │   │   └── common/                  # Shared components (header, footer)
│   │   ├── views/                       # Page-level components (routes)
│   │   │   ├── Home.vue
│   │   │   ├── CalendarEditor.vue
│   │   │   ├── Dashboard.vue
│   │   │   ├── Checkout.vue
│   │   │   └── AdminPanel.vue
│   │   ├── stores/                      # Pinia state management
│   │   │   ├── user.ts
│   │   │   ├── calendar.ts
│   │   │   └── cart.ts
│   │   ├── router/                      # Vue Router configuration
│   │   │   └── index.ts
│   │   ├── graphql/                     # GraphQL queries/mutations
│   │   │   ├── queries.ts
│   │   │   └── mutations.ts
│   │   ├── types/                       # TypeScript type definitions
│   │   │   └── generated.ts             # Auto-generated from GraphQL schema
│   │   └── App.vue
│   ├── public/                          # Static files (favicon, index.html template)
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── tailwind.config.js
├── migrations/                           # MyBatis Migrations (database schema)
│   ├── scripts/
│   │   ├── 001_create_users_table.sql
│   │   ├── 002_create_calendars_table.sql
│   │   ├── 003_create_orders_table.sql
│   │   └── ...
│   └── environments/
│       ├── development.properties
│       └── production.properties
├── infrastructure/                       # Infrastructure as Code
│   ├── terraform/                       # Terraform configurations
│   │   ├── cloudflare/                  # Cloudflare resources (DNS, R2, tunnels)
│   │   └── aws/                         # AWS resources (SES, future)
│   ├── ansible/                         # Ansible playbooks
│   │   ├── deploy-k3s.yml              # Deploy to Kubernetes
│   │   └── setup-database.yml          # PostgreSQL setup
│   └── kubernetes/                      # Kubernetes manifests
│       ├── base/                        # Base configurations
│       │   ├── deployment.yaml
│       │   ├── service.yaml
│       │   └── hpa.yaml
│       ├── overlays/
│       │   ├── beta/                    # Beta environment
│       │   └── production/              # Production environment
│       └── secrets/                     # Secret templates (not committed)
│           └── secrets.example.yaml
├── docs/                                 # Documentation and design artifacts
│   ├── diagrams/                        # UML diagrams (PlantUML/Mermaid source)
│   │   ├── component_diagram.puml
│   │   ├── database_erd.puml
│   │   ├── deployment_diagram.puml
│   │   ├── sequence_oauth_login.puml
│   │   ├── sequence_order_placement.puml
│   │   └── sequence_pdf_generation.puml
│   ├── adr/                             # Architectural Decision Records (optional)
│   │   ├── 001-modular-monolith.md
│   │   ├── 002-graphql-primary-api.md
│   │   └── 003-async-job-pattern.md
│   └── guides/                          # Developer guides
│       ├── setup.md                     # Local development setup
│       ├── deployment.md                # Deployment guide
│       └── contributing.md              # Contribution guidelines
├── api/                                  # API specifications
│   ├── graphql-schema.graphql           # GraphQL schema definition
│   └── openapi-rest.yaml                # REST API spec (webhooks, downloads)
├── tests/                                # End-to-end tests
│   └── e2e/
│       └── cypress/                     # Cypress E2E tests (future)
├── .github/                              # GitHub Actions workflows
│   └── workflows/
│       ├── ci.yml                       # Continuous integration
│       └── deploy.yml                   # Continuous deployment
├── .codemachine/                         # Code generation artifacts
│   └── artifacts/
│       └── plan/                        # This project plan
├── pom.xml                               # Maven project configuration
├── Dockerfile                            # Docker image build
├── .dockerignore
├── .gitignore
├── README.md                             # Project overview
└── LICENSE
```

**Rationale for Key Structural Choices:**

1. **`src/main/java/.../` Domain-Based Packages**: Organize by technical layer (api, service, repository, model) rather than feature modules. Simpler for small team, easier navigation. Future refactoring into feature modules (e.g., `calendar/`, `commerce/`) if team grows.

2. **`frontend/` Separate Directory**: Clear frontend/backend separation. Quinoa plugin integrates Vue build into Quarkus at compile time (single deployment artifact).

3. **`migrations/` Root-Level**: MyBatis Migrations requires specific structure. Root-level placement makes it obvious where schema changes live.

4. **`infrastructure/` Centralized**: All IaC (Terraform, Ansible, Kubernetes) in one place. Clear separation from application code.

5. **`docs/diagrams/` PlantUML Source**: Version-control diagram source files (`.puml`), not just PNGs. Enable easy updates, diff tracking.

6. **`api/` Specifications**: Central location for API contracts (GraphQL schema, OpenAPI specs). Frontend auto-generates types from these.

7. **`tests/e2e/` Separate from Unit Tests**: E2E tests (Cypress, future) run against deployed environment, not part of Maven build. Separate directory clarifies scope.
```

### Context: architectural-style (from 02_Architecture_Overview.md)

```markdown
### 3.1. Architectural Style

**Selected Style:** **Modular Monolith with Asynchronous Job Processing**

**Rationale:**

The Village Calendar application is architected as a **modular monolith** deployed as a single Quarkus application with clear internal module boundaries. This architectural choice is driven by several key factors:

**Why Modular Monolith (vs. Microservices):**

1. **Team Size & Expertise**: Small development team with deep Quarkus/Java expertise benefits from simplified development, debugging, and deployment workflows inherent to monoliths. Microservices would introduce significant operational overhead (service discovery, inter-service communication, distributed debugging) without clear benefits at current scale.

2. **Transactional Consistency**: Core workflows (user creates calendar → saves to account → places order → generates PDF) require strong consistency. Managing distributed transactions across microservices would add complexity without improving user experience.

3. **Development Velocity**: Single codebase accelerates feature development. Shared code (astronomical calculations, PDF rendering, data models) can be easily reused across features without versioning/packaging complexities.

4. **Operational Simplicity**: Single deployment artifact simplifies CI/CD pipeline, monitoring, and troubleshooting. With existing Kubernetes infrastructure, scaling the monolith horizontally (adding pods) is straightforward and sufficient for projected load.

5. **Right-Sized for MVP**: Application domains (calendar editor, user management, e-commerce, admin) are tightly coupled around the core Calendar entity. Splitting into separate services would create chatty inter-service communication patterns.

**Modular Organization:**

While packaged as a single deployment, the codebase maintains clear module boundaries:

- **`calendar-core`**: Calendar domain logic (models, astronomical calculations, template system)
- **`calendar-pdf`**: PDF generation engine (SVG rendering, watermarking, Batik integration)
- **`user-management`**: Authentication, authorization, account management (OAuth integration)
- **`commerce`**: Order management, Stripe integration, payment processing
- **`jobs`**: DelayedJob framework, job definitions (PDF generation, email sending)
- **`admin`**: Administrative interfaces and analytics
- **`web-api`**: GraphQL schema, REST controllers, frontend integration (Quinoa/Vue)
```

### Context: technology-stack-summary (from 02_Architecture_Overview.md)

```markdown
### 3.2. Technology Stack Summary

Key Technology Decisions:

1. **GraphQL over REST**: Frontend complexity (calendar editor needs nested data: calendar → events → user → templates) benefits from GraphQL's flexible querying. Single request replaces multiple REST round-trips.

2. **Quarkus over Spring Boot**: Already mandated, but validated choice. Faster startup (important for Kubernetes autoscaling), lower memory usage, cloud-native design.

3. **PostgreSQL over NoSQL**: Calendar data is relational (users, calendars, events, orders). ACID transactions critical for e-commerce. JSONB provides flexibility for calendar metadata without schema rigidity.

4. **Stripe over Custom Payment**: PCI compliance is complex and risky. Stripe Checkout delegates payment form rendering, card data handling, and compliance to Stripe. 2.9% + $0.30 fee justified by reduced risk and development time.

5. **Cloudflare R2 over S3**: S3-compatible API (easy migration if needed), but lower egress costs. Tight integration with Cloudflare CDN. Single vendor for edge services simplifies billing and support.

6. **Pinia over Vuex**: Pinia is Vue 3 official recommendation. Simpler API, better TypeScript support, no mutations (actions only).

7. **Tailwind over Custom CSS**: Utility-first approach accelerates UI development. PurgeCSS removes unused classes (small bundle). PrimeVue handles complex components; Tailwind for layouts and spacing.
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `pom.xml` (Root)
    *   **Summary:** Maven configuration already exists with Quarkus 3.26.2 as specified. The project is already configured with most required dependencies including Quarkus extensions (Hibernate Panache, SmallRye GraphQL, OIDC, Health, Micrometer, OpenTelemetry), Stripe SDK, Quinoa plugin for Vue.js integration, AWS SDK for R2, Batik for PDF rendering, and testing frameworks.
    *   **Recommendation:** You MUST NOT recreate pom.xml from scratch. The file already has the correct structure and dependencies. Task acceptance criteria mention creating "target/village-calendar-1.0.0-runner.jar", but current artifact is "village-calendar-1.0-SNAPSHOT". You may need to update the version or accept that the acceptance criteria is slightly different from current configuration.
    *   **Warning:** The pom.xml already includes ALL required dependencies listed in the task description: quarkus-hibernate-orm-panache (line 97), quarkus-smallrye-graphql (line 58), quarkus-oidc (line 75), quarkus-smallrye-health (line 46), quarkus-micrometer-registry-prometheus (line 52), quarkus-opentelemetry (line 118), and io.quarkiverse.quinoa:quarkus-quinoa (line 131).

*   **File:** `src/main/resources/application.properties`
    *   **Summary:** Application configuration already exists with comprehensive settings for Quarkus (port 8030), Hibernate ORM, PostgreSQL datasource, Quinoa/Vue integration, GraphQL, OpenAPI, OAuth2 (Google/Facebook), JWT, Mailer, R2/S3, Stripe, and logging.
    *   **Recommendation:** The configuration is already complete. Quinoa is configured to integrate with Vue.js (dev server port 5176, build dir "dist", SPA routing enabled). You SHOULD verify that the configuration matches task requirements but DO NOT recreate from scratch.
    *   **Note:** The current package naming convention uses `villagecompute.calendar` (not `com.villagecompute.calendar` as shown in the planned directory structure). The codebase uses flat package structure (`villagecompute.calendar.data.models`, `villagecompute.calendar.services`, etc.) which is valid but differs slightly from the plan's `com.villagecompute.calendar` prefix.

*   **File:** `src/main/webui/package.json`
    *   **Summary:** Vue.js frontend already exists with package.json containing all required dependencies: Vue 3.5.13, PrimeVue 4.3.2, Pinia 3.0.3, Vue Router 4.5.0, TailwindCSS 4.0.15, TypeScript 5.8.2, Vite 6.2.0, Playwright for E2E testing, and various dev dependencies.
    *   **Recommendation:** The frontend is already initialized and configured. You MUST NOT recreate the frontend directory. The task asks for `frontend/` directory but the codebase uses `src/main/webui/` which is the standard Quinoa convention. This is acceptable and follows Quarkus best practices.
    *   **Tip:** The webui directory already has a complete Vue 3 + TypeScript + Vite setup with proper configuration files (vite.config.ts, tsconfig.json, tailwind.config.js) and a structured src directory with components, stores, views, router, services, etc.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** Example of existing JPA entity using Hibernate Panache. Extends `DefaultPanacheEntityWithTimestamps`, uses Jakarta Persistence annotations, follows active record pattern with static finder methods.
    *   **Recommendation:** This demonstrates the project's entity pattern. When task I1.T8 asks you to create entities, you SHOULD follow this existing pattern: extend `DefaultPanacheEntityWithTimestamps`, use public fields (Panache style), include validation annotations (@NotNull, @Email, @Size), add indexes via @Index annotation, implement static finder methods.
    *   **Note:** The codebase uses `CalendarUser` instead of `User`, `UserCalendar` instead of `Calendar`, `CalendarOrder` instead of `Order`. This naming appears to be domain-specific to avoid conflicts. Be aware of this convention when cross-referencing with the plan which uses simpler names.

*   **File:** `README.md` (Root)
    *   **Summary:** Comprehensive project documentation already exists describing architecture, features, tech stack, setup instructions, API overview, and project structure.
    *   **Recommendation:** The README is well-written and current. You SHOULD update it if you make significant changes to match the task deliverables, but DO NOT replace it entirely. The current README correctly documents that the app runs on port 8030, uses Quinoa for Vue integration, and has both GraphQL and REST endpoints.

*   **File:** `Dockerfile` and `Dockerfile.local`
    *   **Summary:** Docker build configurations already exist. `Dockerfile` appears to be for production builds, `Dockerfile.local` for local development.
    *   **Recommendation:** Verify that the Dockerfile successfully builds with `docker build -t village-calendar:latest .` as required by acceptance criteria. The existing Dockerfiles may already satisfy this requirement.

### Implementation Tips & Notes

*   **Tip:** The project structure is ALREADY ESTABLISHED and OPERATIONAL. The task I1.T1 description asks you to "create the foundational project structure," but based on my analysis, the structure already exists and is functional. Your primary job is to VERIFY and potentially UPDATE/COMPLETE the structure rather than create from scratch.

*   **Tip:** The task acceptance criteria states the app should serve at "http://localhost:8080" but the current application.properties configures port 8030 (`quarkus.http.port=8030`). You may need to either: (1) update application.properties to use port 8080, or (2) acknowledge that the acceptance criteria port differs from current configuration. I recommend keeping port 8030 as it's already configured and documented in README.

*   **Tip:** The planned directory structure shows `frontend/` as a root-level directory, but the actual implementation uses `src/main/webui/` which is the Quinoa standard location. This is NOT an error - Quinoa expects the frontend code in `src/main/webui/` by default. The Quinoa configuration in application.properties confirms this setup.

*   **Note:** The task targets specify creating files like `frontend/package.json`, `frontend/vite.config.ts`, etc., but these already exist in `src/main/webui/`. The task may be referring to the logical "frontend" concept rather than an exact directory path. The existing setup is correct.

*   **Tip:** MyBatis Migrations directory is already present at root level (`migrations/` directory exists with subdirectories). This will be populated in task I1.T7.

*   **Tip:** The codebase already has a `docs/` directory structure with diagrams and guides subdirectories. Task I1.T3, I1.T4, I1.T5 will create PlantUML diagrams in this location.

*   **Warning:** The Java package structure uses `villagecompute.calendar.*` (without the `com.` prefix that appears in the architectural plan). This is valid but be consistent. Do not introduce `com.villagecompute.calendar` packages as it would create package conflicts.

*   **Warning:** Some existing entity names differ from the plan: `CalendarUser` vs `User`, `UserCalendar` vs `Calendar`, `CalendarOrder` vs `Order`. This appears intentional to avoid naming conflicts. Future tasks should maintain this naming convention.

*   **Tip:** The project already has DelayedJob entities and job infrastructure (DelayedJob.java, DelayedJobQueue.java in data/models). This will be expanded in Iteration 4 (I4) for PDF generation and email jobs.

*   **Tip:** Testing infrastructure is already configured with Quarkus JUnit 5, Mockito, REST Assured, and H2 for test database. Playwright is configured in the webui for E2E tests.

### Recommended Verification Steps

Since most infrastructure already exists, your task should focus on:

1. **Verify Maven Compilation:** Run `./mvnw clean compile` to ensure backend compiles successfully
2. **Verify Frontend Build:** Run `cd src/main/webui && npm install && npm run build` to ensure Vue app builds
3. **Verify Integrated Build:** Run `./mvnw clean package` to verify Quinoa integration produces JAR with embedded frontend
4. **Verify Dev Mode:** Run `./mvnw quarkus:dev` and verify:
   - Quarkus starts on port 8030 (or update to 8080 if required)
   - Vue dev server starts and is proxied
   - Can access app at http://localhost:8030
   - GraphQL UI accessible at http://localhost:8030/graphql-ui
5. **Update/Verify README:** Ensure README accurately documents the current setup and local development instructions
6. **Verify Docker Build:** Run `docker build -t village-calendar:latest .` to ensure Dockerfile builds successfully
7. **Verify Gitignore:** Ensure `.gitignore` properly excludes `target/`, `node_modules/`, `.env` files, and other generated artifacts
8. **Create Placeholder Vue App:** The current Vue app may need a simple "Hello Village Calendar" placeholder page styled with PrimeVue to satisfy acceptance criteria

### Files That May Need Creation/Update

Based on my analysis, files that likely need work:

1. **README.md**: May need minor updates to match task requirements
2. **src/main/webui/src/views/Home.vue** or similar: Create/update to display PrimeVue-styled "Hello Village Calendar" placeholder
3. **.gitignore**: Verify it includes all necessary exclusions
4. **Dockerfile**: Verify it builds successfully

All other target files mentioned in the task already exist and are properly configured.
