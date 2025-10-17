# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T2",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Set up PostgreSQL database connection configuration in Quarkus (application.properties with JDBC URL, credentials from environment variables). Create MyBatis Migrations directory structure (migrations/scripts/, migrations/environments/). Configure development and production migration environments. Document database setup instructions (create database, install PostGIS extension). Test database connectivity with Quarkus datasource health check.",
  "agent_type_hint": "DatabaseAgent",
  "inputs": "Data model overview from Plan Section 2, PostgreSQL 17+ with PostGIS requirement",
  "target_files": [
    "src/main/resources/application.properties",
    "src/main/resources/application-dev.properties",
    "src/main/resources/application-prod.properties",
    "migrations/environments/development.properties",
    "migrations/environments/production.properties",
    "docs/guides/database-setup.md"
  ],
  "input_files": [
    "src/main/resources/application.properties"
  ],
  "deliverables": "Quarkus application connects to PostgreSQL on startup (local development database), Health check endpoint (/q/health/ready) reports datasource UP, MyBatis Migrations directory structure ready for schema scripts, Database setup guide with SQL commands to create database and enable PostGIS",
  "acceptance_criteria": "./mvnw quarkus:dev connects to PostgreSQL without errors, curl http://localhost:8080/q/health/ready returns 200 with datasource check passing, Database setup guide tested on fresh PostgreSQL 17 installation, Environment variables DB_USERNAME, DB_PASSWORD, DB_URL override defaults",
  "dependencies": [
    "I1.T1"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: data-model-overview (from 01_Plan_Overview_and_Setup.md)

```markdown
<!-- anchor: data-model-overview -->
### Data Model Overview

**Core Entities:**

1. **User**: OAuth-authenticated accounts
   - Fields: `user_id` (PK), `oauth_provider`, `oauth_subject_id`, `email`, `display_name`, `profile_picture_url`, `role` (user/admin)
   - Relationships: 1:N with Calendars, Orders, CalendarTemplates

2. **CalendarSession**: Anonymous guest sessions (pre-authentication)
   - Fields: `session_id` (PK, UUID), `user_id` (nullable FK), `session_data` (JSONB), `expires_at`
   - Purpose: Persist guest calendar edits, convert to user account on login

3. **Calendar**: User's saved calendars
   - Fields: `calendar_id` (PK), `user_id` (FK), `template_id` (FK, nullable), `title`, `year`, `config` (JSONB), `preview_image_url`, `pdf_url`, `is_public`, `share_token` (UUID), `version` (optimistic locking)
   - Relationships: 1:N with Events, N:1 with User/Template

4. **Event**: Custom calendar events
   - Fields: `event_id` (PK), `calendar_id` (FK), `event_date`, `event_text`, `emoji`, `color`

5. **CalendarTemplate**: Admin-created templates
   - Fields: `template_id` (PK), `created_by_user_id` (FK), `name`, `description`, `thumbnail_url`, `config` (JSONB), `is_active`, `sort_order`

6. **Order**: E-commerce orders
   - Fields: `order_id` (PK), `user_id` (FK), `order_number`, `status` (enum: PENDING/PAID/IN_PRODUCTION/SHIPPED/DELIVERED/CANCELLED/REFUNDED), `subtotal`, `tax`, `shipping_cost`, `total`, `shipping_address` (JSONB), `tracking_number`
   - Relationships: 1:N with OrderItems, 1:1 with Payment

7. **OrderItem**: Line items in orders
   - Fields: `order_item_id` (PK), `order_id` (FK), `calendar_id` (FK), `product_type`, `quantity`, `unit_price`

8. **Payment**: Stripe payment records
   - Fields: `payment_id` (PK), `order_id` (FK), `stripe_payment_intent_id`, `stripe_checkout_session_id`, `amount`, `status`, `refund_amount`, `refund_reason`

9. **DelayedJob**: Asynchronous job queue
   - Fields: `job_id` (PK), `job_type`, `payload` (JSONB), `priority`, `attempts`, `max_attempts`, `run_at`, `locked_at`, `locked_by`, `failed_at`, `last_error`, `completed_at`

10. **PageView**: Analytics events
    - Fields: `page_view_id` (PK), `user_id` (FK, nullable), `session_id` (FK, nullable), `url_path`, `referrer`, `user_agent`, `ip_address`, `created_at`

11. **AnalyticsRollup**: Aggregated metrics
    - Fields: `rollup_id` (PK), `rollup_date`, `metric_name`, `metric_value`, `dimensions` (JSONB)

**Database Indexing Strategy:**
- Primary keys: `bigserial` auto-increment
- Foreign keys with indexes: `user_id`, `calendar_id`, `order_id`, `session_id`
- Composite indexes: `(user_id, created_at)`, `(order_id, status)`, `(run_at, priority)` on delayed_jobs
- Unique constraints: `users.email`, `orders.order_number`, `calendar.share_token`
- GIN indexes on JSONB: `calendar.config`, `analytics_rollups.dimensions`

**ERD Diagram**: Generated in Iteration 1 (PlantUML format, stored in `docs/diagrams/database_erd.puml`)
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

### Context: PostgreSQL and PostGIS Requirements (from 02_Architecture_Overview.md)

```markdown
**Key Technology Selections from Architecture Overview:**

| **Database** | PostgreSQL | 17+ | Mandated. PostGIS extension for geospatial queries (future: location-based features). JSONB for flexible calendar metadata. Robust, proven, open-source. |
| **Database Extensions** | PostGIS | Latest | Geospatial data types and functions (future: user location, shipping calculations). Required by existing infrastructure. |

**Architectural Rationale:**

3. **PostgreSQL over NoSQL**: Calendar data is relational (users, calendars, events, orders). ACID transactions critical for e-commerce. JSONB provides flexibility for calendar metadata without schema rigidity.

**Async Processing Pattern:**
- **Mechanism**: Vert.x EventBus for event-driven job triggering + PostgreSQL-backed job queue table with locking mechanism
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/resources/application.properties`
    *   **Summary:** This file contains the main Quarkus application configuration. It already has PostgreSQL datasource configuration but needs enhancement for environment variable support.
    *   **Current Database Config:**
        ```properties
        quarkus.datasource.db-kind=postgresql
        quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5532/calendar
        quarkus.datasource.username=calendar
        quarkus.datasource.password=calendar
        ```
    *   **Recommendation:** You MUST update these properties to support environment variable overrides using the `${ENV_VAR:default}` pattern as specified in the acceptance criteria. The current values are hardcoded and do not support the required DB_USERNAME, DB_PASSWORD, DB_URL override capability.

*   **File:** `migrations/README.md`
    *   **Summary:** This file documents the MyBatis Migrations structure and usage. The migrations framework is already set up.
    *   **Current Structure:** The migrations directory already exists with:
        - `src/main/resources/environments/` - environment property files
        - `src/main/resources/scripts/` - SQL migration scripts
        - Environments configured: `development.properties`, `beta.properties`, `testing.properties`, `production.properties`
    *   **Recommendation:** The directory structure is ALREADY COMPLETE. You do NOT need to create it. Focus on ensuring environment property files are properly configured for the task requirements.

*   **File:** `migrations/src/main/resources/environments/development.properties`
    *   **Summary:** Development environment configuration for MyBatis Migrations.
    *   **Current Config:**
        ```properties
        driver=org.postgresql.Driver
        url=jdbc:postgresql://localhost:5532/calendar
        username=calendar
        password=calendar
        changelog=schema_version
        send_full_script=true
        ```
    *   **Recommendation:** This file is already properly configured for local development. You may want to ensure consistency with the main application.properties.

*   **File:** `migrations/src/main/resources/scripts/001_initial_schema.sql`
    *   **Summary:** Initial database migration that creates the core tables. This migration already enables the UUID extension.
    *   **Key Features:**
        - Already includes `CREATE EXTENSION IF NOT EXISTS "uuid-ossp";`
        - Creates tables: calendar_users, calendar_templates, user_calendars, calendar_orders
        - Uses JSONB for flexible configuration
        - Includes proper indexes and foreign key constraints
    *   **Recommendation:** You SHOULD review this migration to understand the database structure. Note that it does NOT yet enable PostGIS extension - this is a key requirement for your task.

### Implementation Tips & Notes

*   **Tip - Environment Variables:** The task requires supporting environment variable overrides for database credentials. Use the Quarkus pattern `${ENV_VAR_NAME:default_value}` in application.properties. For example:
    ```properties
    quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://localhost:5532/calendar}
    quarkus.datasource.username=${DB_USERNAME:calendar}
    quarkus.datasource.password=${DB_PASSWORD:calendar}
    ```

*   **Tip - PostGIS Extension:** You MUST create documentation for enabling PostGIS. The initial migration (001_initial_schema.sql) already enables the uuid-ossp extension. You should follow the same pattern but note that PostGIS should be enabled in the database setup guide (docs/guides/database-setup.md) rather than in a migration script, since it's a one-time database-level setup that may require superuser privileges.

*   **Tip - Quarkus Health Checks:** Quarkus provides automatic health checks for datasources. Once the datasource is properly configured, the `/q/health/ready` endpoint will automatically report the database connection status. You do NOT need to implement custom health check code - just ensure the datasource configuration is correct.

*   **Note - MyBatis Migrations Structure:** The migrations/ directory is a separate Maven module (it has its own pom.xml). This is intentional and follows MyBatis Migrations best practices. The environment properties in migrations/src/main/resources/environments/ are used by the MyBatis Migrations Maven plugin, NOT by the Quarkus application.

*   **Note - Application Properties Files:** The task specifies creating `application-dev.properties` and `application-prod.properties`. These are Quarkus profile-specific property files. You SHOULD create these files to separate environment-specific configuration. Use the Quarkus profile syntax: `%dev.` prefix for dev-specific properties, `%prod.` prefix for production-specific properties. Alternatively, create separate files named `application-dev.properties` and `application-prod.properties`.

*   **Warning - Database Port:** The current configuration uses port `5532` (not the standard PostgreSQL port 5432). This appears to be intentional for the Docker Compose setup. Do NOT change this unless you verify the Docker configuration supports a different port.

*   **Warning - Health Check Port Mismatch:** The acceptance criteria mentions testing health check at `http://localhost:8080/q/health/ready`, but the current application is configured to run on port `8030` (see `quarkus.http.port=8030` in application.properties). You MUST either update the port to 8080 or acknowledge this discrepancy when verifying the health check works on the actual configured port.

*   **Recommendation - Documentation Structure:** Create the database setup guide at `docs/guides/database-setup.md`. This guide should include:
    1. How to install PostgreSQL 17+ on different platforms (macOS, Linux, Windows)
    2. How to create the `calendar` database
    3. How to enable PostGIS extension (requires superuser: `CREATE EXTENSION IF NOT EXISTS postgis;`)
    4. How to create the `calendar` database user with appropriate permissions
    5. How to verify the connection is working
    6. Environment variable configuration examples for different deployment scenarios
    7. Docker Compose setup instructions if applicable

*   **Recommendation - Environment Files:** For the migrations/environments/ directory, ensure that:
    - `development.properties` is already correct (verified)
    - `production.properties` should be updated if it needs different configuration for production deployment
    - Both files should reference the same database name (`calendar`) but with different connection details appropriate to each environment

*   **Tip - docs/guides Directory:** You will need to create the `docs/guides/` directory if it doesn't already exist before creating `database-setup.md`.
