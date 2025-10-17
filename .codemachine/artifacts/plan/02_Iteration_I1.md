# Project Plan: Village Calendar - Iteration 1

---

<!-- anchor: iteration-1-plan -->
## Iteration 1: Project Foundation & Core Architecture Setup

**Iteration ID:** `I1`

**Goal:** Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding.

**Prerequisites:** None (initial setup iteration)

**Duration Estimate:** 2-3 weeks

**Deliverables:**
- Initialized Quarkus + Vue project structure
- PostgreSQL database schema with migrations
- Architectural diagrams (Component, ERD, Deployment, Sequence)
- GraphQL API schema definition
- Basic authentication framework (OAuth OIDC configuration)
- CI/CD pipeline configuration

---

<!-- anchor: task-i1-t1 -->
### Task 1.1: Initialize Project Structure and Build Configuration

**Task ID:** `I1.T1`

**Description:**
Create the foundational project structure for the Village Calendar application. Initialize Maven POM for Quarkus backend, configure Quinoa plugin for Vue.js integration, set up frontend package.json with Vite, and create directory structure as specified in Section 3 of the plan. Configure Quarkus extensions (Hibernate ORM with Panache, SmallRye GraphQL, OIDC, Health, Micrometer, OpenTelemetry). Initialize Vue 3 project with TypeScript, PrimeVue, Pinia, Vue Router, and TailwindCSS.

**Agent Type Hint:** `SetupAgent`

**Inputs:**
- Directory structure specification from Plan Section 3
- Technology stack requirements from Plan Section 2
- Reference prototype at `/Users/tea/dev/VillageCompute/code/VillageCMS/admin` (read-only inspection for patterns)

**Input Files:** []

**Target Files:**
- `pom.xml` (Quarkus Maven configuration)
- `src/main/resources/application.properties` (Quarkus config)
- `frontend/package.json` (Vue dependencies)
- `frontend/vite.config.ts` (Vite + Quinoa integration)
- `frontend/tsconfig.json` (TypeScript configuration)
- `frontend/tailwind.config.js` (TailwindCSS setup)
- `frontend/src/main.ts` (Vue app initialization)
- `README.md` (project overview, setup instructions)
- `.gitignore` (Java, Node, IDE files)
- `Dockerfile` (multi-stage build: Maven + Node)

**Deliverables:**
- Compilable Quarkus application (Maven `./mvnw compile` succeeds)
- Buildable Vue frontend (npm run build succeeds)
- Integrated build via Quinoa (single `./mvnw package` produces JAR with embedded frontend)
- Basic README with local development setup instructions

**Acceptance Criteria:**
- `./mvnw quarkus:dev` starts Quarkus in dev mode, serves Vue app at http://localhost:8080
- Vue app displays PrimeVue-styled "Hello Village Calendar" placeholder page
- Maven build produces executable JAR (`target/village-calendar-1.0.0-runner.jar`)
- Dockerfile builds successfully (`docker build -t village-calendar:latest .`)

**Dependencies:** None

**Parallelizable:** No (foundational task, all others depend on this)

---

<!-- anchor: task-i1-t2 -->
### Task 1.2: Configure PostgreSQL Connection and MyBatis Migrations

**Task ID:** `I1.T2`

**Description:**
Set up PostgreSQL database connection configuration in Quarkus (application.properties with JDBC URL, credentials from environment variables). Create MyBatis Migrations directory structure (`migrations/scripts/`, `migrations/environments/`). Configure development and production migration environments. Document database setup instructions (create database, install PostGIS extension). Test database connectivity with Quarkus datasource health check.

**Agent Type Hint:** `DatabaseAgent`

**Inputs:**
- Data model overview from Plan Section 2
- PostgreSQL 17+ with PostGIS requirement

**Input Files:**
- `src/main/resources/application.properties`

**Target Files:**
- `src/main/resources/application.properties` (add datasource config)
- `src/main/resources/application-dev.properties` (dev database URL)
- `src/main/resources/application-prod.properties` (prod database URL placeholder)
- `migrations/environments/development.properties`
- `migrations/environments/production.properties`
- `docs/guides/database-setup.md` (PostgreSQL installation, PostGIS setup, database creation)

**Deliverables:**
- Quarkus application connects to PostgreSQL on startup (local development database)
- Health check endpoint (`/q/health/ready`) reports datasource UP
- MyBatis Migrations directory structure ready for schema scripts
- Database setup guide with SQL commands to create database and enable PostGIS

**Acceptance Criteria:**
- `./mvnw quarkus:dev` connects to PostgreSQL without errors
- `curl http://localhost:8080/q/health/ready` returns 200 with datasource check passing
- Database setup guide tested on fresh PostgreSQL 17 installation
- Environment variables `DB_USERNAME`, `DB_PASSWORD`, `DB_URL` override defaults

**Dependencies:** `I1.T1` (requires project structure)

**Parallelizable:** No (database required for subsequent tasks)

---

<!-- anchor: task-i1-t3 -->
### Task 1.3: Generate Component Diagram (PlantUML)

**Task ID:** `I1.T3`

**Description:**
Create PlantUML component diagram showing the internal architecture of the Quarkus API application. Diagram must include: GraphQL API layer (resolvers), Service layer (CalendarService, UserService, OrderService, PdfService, EmailService), Repository layer (Panache repositories), Job Manager, Integration components (OAuth Client, Stripe Client, Email Sender, R2 Client), Security Filter. Show dependencies between components (e.g., GraphQL resolvers call services, services call repositories, services call integration clients). Use C4 Component diagram notation for clarity.

**Agent Type Hint:** `DiagrammingAgent`

**Inputs:**
- Component descriptions from Plan Section 2.3 "Key Components/Services"
- Technology stack (Quarkus, Panache, SmallRye GraphQL, Vert.x EventBus)

**Input Files:** []

**Target Files:**
- `docs/diagrams/component_diagram.puml`

**Deliverables:**
- PlantUML diagram file rendering correctly without syntax errors
- Diagram accurately reflects components and dependencies described in architecture plan
- PNG export of diagram for documentation (`docs/diagrams/component_diagram.png`)

**Acceptance Criteria:**
- PlantUML file validates and renders in PlantUML viewer/IDE plugin
- All components from architecture section included with correct relationships
- Diagram follows C4 Component diagram conventions (containers, components, relationships)
- Visual clarity: components grouped by layer, clear dependency arrows

**Dependencies:** `I1.T1` (requires docs directory structure)

**Parallelizable:** Yes (can run concurrently with other diagram/artifact tasks)

---

<!-- anchor: task-i1-t4 -->
### Task 1.4: Create Database ERD (PlantUML)

**Task ID:** `I1.T4`

**Description:**
Generate PlantUML entity-relationship diagram for the complete database schema. Include all entities: User, CalendarSession, Calendar, Event, CalendarTemplate, Order, OrderItem, Payment, DelayedJob, PageView, AnalyticsRollup. Show primary keys, foreign keys, key fields (not all columns), cardinalities (1:1, 1:N, N:M). Use PlantUML entity syntax with proper relationship notation. Ensure diagram matches data model overview from Plan Section 2.

**Agent Type Hint:** `DiagrammingAgent`

**Inputs:**
- Data model overview from Plan Section 2 "Data Model Overview"
- Entity descriptions with fields, relationships, and constraints

**Input Files:** []

**Target Files:**
- `docs/diagrams/database_erd.puml`

**Deliverables:**
- PlantUML ERD file rendering correctly
- Diagram shows all 11 entities with relationships
- PNG export of diagram (`docs/diagrams/database_erd.png`)

**Acceptance Criteria:**
- PlantUML file validates and renders without errors
- All entities from data model section included
- Primary keys marked with `<<PK>>`, foreign keys with `<<FK>>`
- Relationship cardinalities correctly shown (1:N, 1:1)
- Indexes and unique constraints annotated in diagram or comments

**Dependencies:** `I1.T1` (requires docs directory structure)

**Parallelizable:** Yes (can run concurrently with I1.T3)

---

<!-- anchor: task-i1-t5 -->
### Task 1.5: Create Deployment Diagram (PlantUML)

**Task ID:** `I1.T5`

**Description:**
Generate PlantUML deployment diagram showing production infrastructure topology. Diagram must include: Cloudflare Edge Network (CDN, Tunnel, R2 storage), Kubernetes Cluster (k3s on Proxmox) with namespaces, Deployment nodes (calendar-api deployment with 2-10 replicas, calendar-worker deployment with 1-5 replicas), Kubernetes Service (load balancer), Observability pods (Jaeger, Prometheus), Database VM (PostgreSQL on separate Proxmox VM), External systems (Stripe API, OAuth Providers, Email Service), User devices (browser). Show network relationships and protocols (HTTPS, JDBC, S3 API, SMTP).

**Agent Type Hint:** `DiagrammingAgent`

**Inputs:**
- Deployment architecture from Plan Section 3.9.3 "Deployment Diagram"
- Infrastructure stack from Plan Section 2

**Input Files:** []

**Target Files:**
- `docs/diagrams/deployment_diagram.puml`

**Deliverables:**
- PlantUML deployment diagram file rendering correctly
- Diagram shows complete production topology with Cloudflare, k3s, external services
- PNG export of diagram (`docs/diagrams/deployment_diagram.png`)

**Acceptance Criteria:**
- PlantUML file validates and renders without errors
- Deployment nodes show replica counts and container details
- Network relationships include protocol annotations (HTTPS, JDBC/5432, S3 API)
- Cloudflare integration clearly depicted (CDN, Tunnel ingress, R2 storage)
- Diagram matches deployment description in architecture plan

**Dependencies:** `I1.T1` (requires docs directory structure)

**Parallelizable:** Yes (can run concurrently with I1.T3, I1.T4)

---

<!-- anchor: task-i1-t6 -->
### Task 1.6: Define GraphQL Schema Specification

**Task ID:** `I1.T6`

**Description:**
Create comprehensive GraphQL schema definition file (SDL format) for the Village Calendar API. Define root Query type with queries: `calendar(id)`, `calendars(userId, year)`, `templates(isActive)`, `order(orderId)`, `orders(userId, status)`, `currentUser`, `pdfJob(id)`. Define root Mutation type with mutations: `createCalendar(input)`, `updateCalendar(id, input)`, `deleteCalendar(id)`, `generatePdf(calendarId, watermark)`, `placeOrder(input)`, `cancelOrder(orderId, reason)`, `convertGuestSession(sessionId)`. Define all necessary types: Calendar, Event, CalendarConfig, CalendarTemplate, Order, OrderItem, Payment, User, PdfJob. Define input types for mutations. Define enums: OrderStatus, ProductType, OAuthProvider. Include field descriptions and deprecation annotations where applicable.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- API contract description from Plan Section 2 "API Contract Style"
- Data model entities from Plan Section 2
- Example schema excerpt from architecture blueprint

**Input Files:** []

**Target Files:**
- `api/graphql-schema.graphql`

**Deliverables:**
- GraphQL schema file in SDL format
- Schema includes all queries, mutations, types, inputs, enums described in plan
- Field-level documentation comments (GraphQL description strings)
- Schema validates against GraphQL specification (no syntax errors)

**Acceptance Criteria:**
- Schema file passes GraphQL schema validation (use online validator or GraphQL CLI)
- All entity types from data model represented in schema
- Input types properly defined for create/update operations
- Enums defined for status fields (OrderStatus: PENDING, PAID, SHIPPED, etc.)
- Schema generates valid TypeScript types when processed by code generator

**Dependencies:** `I1.T1` (requires api directory structure)

**Parallelizable:** Yes (can run concurrently with diagram tasks)

---

<!-- anchor: task-i1-t7 -->
### Task 1.7: Create Initial Database Migration Scripts

**Task ID:** `I1.T7`

**Description:**
Write SQL migration scripts for all database tables using MyBatis Migrations format. Create scripts in numbered order: 001_create_users_table.sql, 002_create_calendar_sessions_table.sql, 003_create_calendar_templates_table.sql, 004_create_calendars_table.sql, 005_create_events_table.sql, 006_create_orders_table.sql, 007_create_order_items_table.sql, 008_create_payments_table.sql, 009_create_delayed_jobs_table.sql, 010_create_page_views_table.sql, 011_create_analytics_rollups_table.sql. Each script must include: CREATE TABLE with all columns, primary key constraints, foreign key constraints with ON DELETE CASCADE/SET NULL, indexes (on foreign keys, frequently queried columns), unique constraints, comments on tables/columns. Follow PostgreSQL 17 syntax. Ensure PostGIS extension enabled in script 000_enable_postgis.sql.

**Agent Type Hint:** `DatabaseAgent`

**Inputs:**
- Database ERD from Task I1.T4 (`docs/diagrams/database_erd.puml`)
- Data model overview from Plan Section 2
- Indexing strategy from Plan Section 3.6

**Input Files:**
- `docs/diagrams/database_erd.puml`

**Target Files:**
- `migrations/scripts/000_enable_postgis.sql`
- `migrations/scripts/001_create_users_table.sql`
- `migrations/scripts/002_create_calendar_sessions_table.sql`
- `migrations/scripts/003_create_calendar_templates_table.sql`
- `migrations/scripts/004_create_calendars_table.sql`
- `migrations/scripts/005_create_events_table.sql`
- `migrations/scripts/006_create_orders_table.sql`
- `migrations/scripts/007_create_order_items_table.sql`
- `migrations/scripts/008_create_payments_table.sql`
- `migrations/scripts/009_create_delayed_jobs_table.sql`
- `migrations/scripts/010_create_page_views_table.sql`
- `migrations/scripts/011_create_analytics_rollups_table.sql`

**Deliverables:**
- All 12 migration scripts (000-011) created
- Scripts execute successfully on fresh PostgreSQL 17 database
- MyBatis Migrations CLI can apply scripts (`migrate up`)
- Database schema matches ERD diagram

**Acceptance Criteria:**
- All migration scripts run without SQL errors on PostgreSQL 17
- Foreign key constraints properly defined (correct ON DELETE behavior)
- Indexes created on all foreign key columns and frequently queried fields
- JSONB columns used for calendar.config, session_data, shipping_address, job payload
- BIGSERIAL primary keys, UUID for session_id and share_token
- Script execution order produces schema matching ERD

**Dependencies:** `I1.T2` (requires database connection), `I1.T4` (ERD as reference)

**Parallelizable:** No (sequential script creation ensures referential integrity)

---

<!-- anchor: task-i1-t8 -->
### Task 1.8: Implement JPA Entity Models (Hibernate ORM with Panache)

**Task ID:** `I1.T8`

**Description:**
Create Java JPA entity classes for all database tables using Hibernate ORM with Panache active record pattern. Implement entities: User, CalendarSession, Calendar, Event, CalendarTemplate, Order, OrderItem, Payment, DelayedJob, PageView, AnalyticsRollup. Each entity must extend `PanacheEntity` (or `PanacheEntityBase` if custom ID type), include JPA annotations (@Entity, @Table, @Column, @Id, @GeneratedValue, @ManyToOne, @OneToMany, @Enumerated), use proper column types (JSONB mapped to String or custom type), define relationships with correct fetch strategies (LAZY for collections), add validation annotations (@NotNull, @Size, @Email). Create enum classes: OrderStatus, ProductType, OAuthProvider, UserRole. Ensure entity field names match GraphQL schema for consistency.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Database migration scripts from Task I1.T7
- GraphQL schema from Task I1.T6
- Data model overview from Plan Section 2

**Input Files:**
- `migrations/scripts/*.sql` (all migration files)
- `api/graphql-schema.graphql`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/model/User.java`
- `src/main/java/com/villagecompute/calendar/model/CalendarSession.java`
- `src/main/java/com/villagecompute/calendar/model/Calendar.java`
- `src/main/java/com/villagecompute/calendar/model/Event.java`
- `src/main/java/com/villagecompute/calendar/model/CalendarTemplate.java`
- `src/main/java/com/villagecompute/calendar/model/Order.java`
- `src/main/java/com/villagecompute/calendar/model/OrderItem.java`
- `src/main/java/com/villagecompute/calendar/model/Payment.java`
- `src/main/java/com/villagecompute/calendar/model/DelayedJob.java`
- `src/main/java/com/villagecompute/calendar/model/PageView.java`
- `src/main/java/com/villagecompute/calendar/model/AnalyticsRollup.java`
- `src/main/java/com/villagecompute/calendar/model/enums/OrderStatus.java`
- `src/main/java/com/villagecompute/calendar/model/enums/ProductType.java`
- `src/main/java/com/villagecompute/calendar/model/enums/OAuthProvider.java`
- `src/main/java/com/villagecompute/calendar/model/enums/UserRole.java`

**Deliverables:**
- All 11 entity classes and 4 enum classes created
- Entities compile without errors
- Quarkus dev mode starts without Hibernate validation errors
- Entities match database schema (field types, constraints, relationships)

**Acceptance Criteria:**
- `./mvnw compile` succeeds with no compilation errors
- `./mvnw quarkus:dev` starts and Hibernate schema validation passes
- Relationships correctly mapped (@ManyToOne, @OneToMany with proper cascade/fetch)
- JSONB columns handled (e.g., calendar.config as String with JSON serialization)
- Enums defined for all status/type fields (OrderStatus, ProductType, etc.)
- Entity classes include basic constructors, getters/setters (or Lombok if preferred)

**Dependencies:** `I1.T7` (requires migration scripts as reference)

**Parallelizable:** No (complex task requiring sequential attention to relationships)

---

<!-- anchor: task-i1-t9 -->
### Task 1.9: Configure Quarkus OIDC for OAuth Authentication

**Task ID:** `I1.T9`

**Description:**
Set up Quarkus OIDC extension for OAuth 2.0 / OpenID Connect authentication with Google, Facebook, and Apple providers. Configure application.properties with OIDC tenant configuration for each provider (Google: use Google Cloud Console credentials, Facebook: App ID/Secret, Apple: Service ID/Key). Implement OAuthCallbackResource REST controller to handle OAuth redirects and JWT token generation. Create UserService method for user lookup/creation based on OAuth subject ID. Configure JWT token generation with custom claims (user_id, role, email). Set token expiration to 24 hours. Test OAuth flow in development environment (requires ngrok or localhost redirect URLs configured in provider consoles).

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Authentication architecture from Plan Section 3.8.1
- OAuth provider configuration requirements (Google, Facebook, Apple)

**Input Files:**
- `src/main/resources/application.properties`
- `src/main/java/com/villagecompute/calendar/model/User.java`

**Target Files:**
- `src/main/resources/application.properties` (add quarkus.oidc.* config)
- `src/main/java/com/villagecompute/calendar/api/rest/OAuthCallbackResource.java`
- `src/main/java/com/villagecompute/calendar/service/UserService.java` (initial version)
- `src/main/java/com/villagecompute/calendar/repository/UserRepository.java`
- `docs/guides/oauth-setup.md` (guide for obtaining OAuth credentials from providers)

**Deliverables:**
- Quarkus OIDC configured for Google, Facebook, Apple providers
- OAuth callback endpoint (`/oauth/login?provider=google`) functional
- UserService creates or retrieves user based on OAuth profile
- JWT token generated and returned to client on successful authentication
- OAuth setup guide with provider configuration instructions

**Acceptance Criteria:**
- `GET /oauth/login?provider=google` redirects to Google OAuth consent screen
- After approval, callback URL creates/retrieves user in database
- Response includes JWT token with claims: `sub` (user_id), `role`, `email`, `exp`
- JWT token validates with Quarkus SmallRye JWT (can be decoded and verified)
- OAuth setup guide tested with fresh Google Cloud project
- Configuration supports environment variable overrides for client secrets

**Dependencies:** `I1.T8` (requires User entity), `I1.T2` (requires database)

**Parallelizable:** No (depends on entity models)

---

<!-- anchor: task-i1-t10 -->
### Task 1.10: Create Basic GraphQL Resolver Stubs

**Task ID:** `I1.T10`

**Description:**
Implement skeleton GraphQL resolvers for all queries and mutations defined in the GraphQL schema. Create resolver classes: CalendarResolver (queries: calendar, calendars; mutations: createCalendar, updateCalendar, deleteCalendar), OrderResolver (queries: order, orders; mutations: placeOrder, cancelOrder), UserResolver (query: currentUser; mutation: convertGuestSession), PdfResolver (query: pdfJob; mutation: generatePdf). Each resolver method should return stub data or throw NotImplementedException with TODO comments. Use SmallRye GraphQL annotations (@GraphQLApi, @Query, @Mutation). Configure authentication context injection (@Context SecurityIdentity). Ensure GraphQL endpoint (`/graphql`) is accessible and GraphQL UI loads at `/graphql-ui`.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- GraphQL schema from Task I1.T6
- SmallRye GraphQL documentation

**Input Files:**
- `api/graphql-schema.graphql`
- `src/main/java/com/villagecompute/calendar/model/*.java`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/api/graphql/CalendarResolver.java`
- `src/main/java/com/villagecompute/calendar/api/graphql/OrderResolver.java`
- `src/main/java/com/villagecompute/calendar/api/graphql/UserResolver.java`
- `src/main/java/com/villagecompute/calendar/api/graphql/PdfResolver.java`
- `src/main/java/com/villagecompute/calendar/api/graphql/TemplateResolver.java`

**Deliverables:**
- All resolver classes created with stub methods
- GraphQL endpoint accessible at `/graphql`
- GraphQL UI (Playground) accessible at `/graphql-ui`
- Stub queries return placeholder data (e.g., empty lists, mock objects)
- Mutations throw NotImplementedException or return null

**Acceptance Criteria:**
- `curl -X POST http://localhost:8080/graphql -H "Content-Type: application/json" -d '{"query": "{ calendars(userId: 1) { id title } }"}'` returns 200 with stub response
- GraphQL UI loads in browser and displays schema documentation
- All query/mutation methods from schema represented in resolver classes
- SecurityIdentity context injection works (user ID accessible in resolver methods)
- No runtime errors when querying any defined schema field

**Dependencies:** `I1.T6` (requires GraphQL schema), `I1.T8` (requires entity models)

**Parallelizable:** No (depends on schema and entities)

---

<!-- anchor: task-i1-t11 -->
### Task 1.11: Set Up Frontend Routing and Layout Components

**Task ID:** `I1.T11`

**Description:**
Configure Vue Router with routes for main application pages: Home (`/`), Calendar Editor (`/editor/:id?`), Dashboard (`/dashboard`), Checkout (`/checkout`), Admin Panel (`/admin/*`), Login Callback (`/auth/callback`). Create root App.vue component with PrimeVue layout (header with navigation, main content area, footer). Implement common components: AppHeader.vue (navigation menu, user profile dropdown), AppFooter.vue (copyright, links). Create placeholder view components for each route (Home.vue, CalendarEditor.vue, Dashboard.vue, Checkout.vue, AdminPanel.vue). Configure route guards for authentication (redirect to login if not authenticated for protected routes). Set up Pinia store for user state (currentUser, isAuthenticated).

**Agent Type Hint:** `FrontendAgent`

**Inputs:**
- Frontend structure from Plan Section 3
- Vue Router and Pinia requirements
- PrimeVue documentation for layout components

**Input Files:**
- `frontend/src/main.ts`
- `frontend/package.json`

**Target Files:**
- `frontend/src/router/index.ts`
- `frontend/src/App.vue`
- `frontend/src/components/common/AppHeader.vue`
- `frontend/src/components/common/AppFooter.vue`
- `frontend/src/views/Home.vue`
- `frontend/src/views/CalendarEditor.vue`
- `frontend/src/views/Dashboard.vue`
- `frontend/src/views/Checkout.vue`
- `frontend/src/views/AdminPanel.vue`
- `frontend/src/views/AuthCallback.vue`
- `frontend/src/stores/user.ts`

**Deliverables:**
- Vue Router configured with all main routes
- Root App.vue with PrimeVue layout (Menubar, Toolbar, or custom header/footer)
- All placeholder view components render without errors
- Route guards redirect unauthenticated users to login
- User Pinia store manages authentication state

**Acceptance Criteria:**
- Navigating to `http://localhost:8080/` displays Home view
- Clicking navigation links in AppHeader routes to correct views
- Protected routes (Dashboard, Admin) redirect to login if not authenticated
- User store persists authentication state in localStorage
- PrimeVue components styled correctly with Aura theme

**Dependencies:** `I1.T1` (requires frontend structure)

**Parallelizable:** Yes (can develop concurrently with backend tasks)

---

<!-- anchor: task-i1-t12 -->
### Task 1.12: Configure CI/CD Pipeline (GitHub Actions)

**Task ID:** `I1.T12`

**Description:**
Create GitHub Actions workflow for continuous integration and deployment. Workflow should trigger on push to `main` branch and pull requests. CI jobs: (1) Backend - Maven compile, run unit tests, build JAR; (2) Frontend - npm install, run linting (ESLint), build production bundle; (3) Docker - build Docker image, push to Docker Hub (villagecompute/calendar-api:${GIT_SHA} and :latest tags). CD job (production deployment): Connect to k3s cluster via WireGuard VPN, apply Kubernetes manifests (update image tag), wait for rollout completion, run smoke tests (health check, sample GraphQL query). Configure secrets: DOCKER_HUB_TOKEN, K3S_KUBECONFIG, WIREGUARD_PRIVATE_KEY. Create separate workflows for beta and production environments.

**Agent Type Hint:** `SetupAgent`

**Inputs:**
- CI/CD requirements from Plan Section 3.9.2
- GitHub Actions documentation
- Existing WireGuard VPN setup to k3s cluster

**Input Files:**
- `pom.xml`
- `frontend/package.json`
- `Dockerfile`

**Target Files:**
- `.github/workflows/ci.yml` (continuous integration)
- `.github/workflows/deploy-beta.yml` (beta environment deployment)
- `.github/workflows/deploy-production.yml` (production deployment with approval)
- `docs/guides/cicd-setup.md` (guide for configuring GitHub secrets)

**Deliverables:**
- GitHub Actions CI workflow runs on every push/PR
- CI workflow compiles backend, builds frontend, creates Docker image
- Beta deployment workflow deploys to `calendar-beta` namespace on k3s
- Production deployment workflow requires manual approval, deploys to `calendar-prod` namespace
- CICD setup guide with secret configuration instructions

**Acceptance Criteria:**
- Push to `main` branch triggers CI workflow, all jobs pass
- Docker image pushed to Docker Hub with correct tags
- Beta deployment workflow successfully updates k3s deployment
- Production workflow shows approval gate in GitHub Actions UI
- Smoke tests pass after deployment (health check returns 200)
- CICD guide tested with fresh GitHub repository setup

**Dependencies:** `I1.T1` (requires Dockerfile), `I1.T10` (requires working backend for smoke tests)

**Parallelizable:** Partially (CI workflow can be developed concurrently, CD requires working app)

---

<!-- anchor: task-i1-t13 -->
### Task 1.13: Write Unit Tests for Entity Models and Repositories

**Task ID:** `I1.T13`

**Description:**
Create JUnit 5 unit tests for all JPA entity models and Panache repositories. Test entity validation constraints (@NotNull, @Email, etc.), relationship mappings (cascade, fetch strategies), JSONB serialization/deserialization. Test repository methods: findById, listAll, persist, delete, custom queries (e.g., findByEmail, findByUserId). Use Quarkus test framework with @QuarkusTest annotation and in-memory H2 database for tests. Achieve >70% code coverage for model and repository packages. Configure JaCoCo Maven plugin for coverage reporting.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Entity models from Task I1.T8
- Quarkus testing documentation

**Input Files:**
- `src/main/java/com/villagecompute/calendar/model/*.java`
- `src/main/java/com/villagecompute/calendar/repository/*.java`

**Target Files:**
- `src/test/java/com/villagecompute/calendar/model/UserTest.java`
- `src/test/java/com/villagecompute/calendar/model/CalendarTest.java`
- `src/test/java/com/villagecompute/calendar/model/OrderTest.java`
- (... tests for all entities ...)
- `src/test/java/com/villagecompute/calendar/repository/UserRepositoryTest.java`
- `src/test/java/com/villagecompute/calendar/repository/CalendarRepositoryTest.java`
- (... tests for all repositories ...)
- `pom.xml` (add JaCoCo plugin configuration)

**Deliverables:**
- Unit tests for all 11 entity classes
- Unit tests for all repository classes
- Tests run successfully with `./mvnw test`
- JaCoCo coverage report generated (`target/site/jacoco/index.html`)
- Coverage >70% for model and repository packages

**Acceptance Criteria:**
- `./mvnw test` runs all tests without failures
- Entity validation tests verify @NotNull, @Email, @Size constraints
- Relationship tests confirm cascade and fetch behavior
- Repository tests verify CRUD operations and custom queries
- JaCoCo report shows line/branch coverage percentages
- No test depends on external services (all use in-memory database)

**Dependencies:** `I1.T8` (requires entity models)

**Parallelizable:** Yes (can develop tests concurrently with resolver stubs)

---

<!-- anchor: iteration-1-summary -->
### Iteration 1 Summary

**Total Tasks:** 13

**Completion Criteria:**
- All tasks marked as completed
- Quarkus application builds and runs in dev mode without errors
- Vue.js frontend builds and integrates via Quinoa
- Database schema created via migrations, entities persist successfully
- OAuth authentication flow functional (at least Google provider)
- GraphQL API accessible with stub resolvers
- CI/CD pipeline configured and tested
- All unit tests passing with adequate coverage
- Architectural diagrams generated and documentation complete

**Risk Mitigation:**
- If OAuth provider configuration blocks development, implement mock authentication service temporarily
- If MyBatis Migrations proves difficult, fall back to Flyway (Quarkus extension available)
- If PlantUML diagram generation is slow, prioritize ERD (most critical) and defer others to Iteration 2

**Next Iteration Preview:**
Iteration 2 will focus on implementing core calendar functionality: calendar CRUD operations, event management, template system, and astronomical calculation integration. Sequence diagrams will be created to document key workflows.
