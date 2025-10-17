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
    *   **Summary:** This file contains the main Quarkus application configuration. Database connection parameters are **ALREADY CONFIGURED** with environment variable support using the `${ENV_VAR:default_value}` pattern.
    *   **Current Database Config (ALREADY CORRECT):**
        ```properties
        quarkus.datasource.db-kind=postgresql
        quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://localhost:5532/calendar}
        quarkus.datasource.username=${DB_USERNAME:calendar}
        quarkus.datasource.password=${DB_PASSWORD:calendar}
        ```
    *   **Recommendation:** The database configuration is **ALREADY COMPLETE** and supports environment variable overrides exactly as required by the task. You do NOT need to modify these properties.
    *   **Important:** The application runs on port **8030** (not 8080). The health check will be at `http://localhost:8030/q/health/ready`.

*   **File:** `pom.xml`
    *   **Summary:** Maven POM configured with Quarkus 3.26.2, PostgreSQL JDBC driver, Hibernate ORM with Panache, and SmallRye Health for health checks.
    *   **Recommendation:** All required dependencies are already present. SmallRye Health (lines 44-47) provides automatic datasource health checks - no custom implementation needed.

*   **File:** `migrations/src/main/resources/environments/development.properties`
    *   **Summary:** MyBatis Migrations development environment configuration.
    *   **Current Config (ALREADY CORRECT):**
        ```properties
        driver=org.postgresql.Driver
        url=jdbc:postgresql://localhost:5532/calendar
        username=calendar
        password=calendar
        changelog=schema_version
        send_full_script=true
        ```
    *   **Recommendation:** This file is already properly configured. The MyBatis Migrations directory structure (`migrations/src/main/resources/environments/` and `migrations/src/main/resources/scripts/`) is **ALREADY COMPLETE**. No changes needed.

*   **File:** `migrations/src/main/resources/environments/beta.properties`, `production.properties`, `testing.properties`
    *   **Summary:** Additional environment configurations for MyBatis Migrations already exist for beta, production, and testing environments.
    *   **Recommendation:** The complete set of environment files is **ALREADY PRESENT**. The task target `migrations/environments/production.properties` appears to reference the wrong path - the actual file is at `migrations/src/main/resources/environments/production.properties` (note the `src/main/resources` prefix).

*   **File:** `docs/guides/database-setup.md`
    *   **Summary:** Comprehensive database setup guide that **ALREADY EXISTS** with 592 lines of detailed documentation.
    *   **Content Includes:**
        - PostgreSQL 17+ installation instructions for macOS, Linux, Windows
        - Database creation SQL commands
        - PostGIS extension setup (including `CREATE EXTENSION IF NOT EXISTS postgis;`)
        - User permissions configuration
        - Connection verification steps
        - Environment variable configuration examples
        - Docker Compose setup instructions
        - MyBatis Migrations usage
        - Extensive troubleshooting section
    *   **Recommendation:** This guide is **COMPREHENSIVE AND COMPLETE**. It already covers all task requirements. You SHOULD review it to ensure accuracy but DO NOT recreate it.

### Implementation Tips & Notes

*   **CRITICAL - Task is Mostly Complete:** Based on my analysis, **the vast majority of this task's requirements are already implemented**. Your primary focus should be on **VERIFICATION and TESTING** rather than new implementation.

*   **CRITICAL - Port Mismatch:** The acceptance criteria mentions `http://localhost:8080/q/health/ready`, but the application actually runs on port **8030**. When testing the health check, use: `curl http://localhost:8030/q/health/ready`

*   **CRITICAL - File Path Discrepancy:** The task specifies `migrations/environments/production.properties`, but the actual location is `migrations/src/main/resources/environments/production.properties`. This is already correctly configured - no action needed.

*   **Tip - About application-dev.properties and application-prod.properties:** The task lists these as target files, but the current setup uses Quarkus's recommended approach with environment variables in a single `application.properties` file. You COULD create separate profile files, but it's NOT necessary since the current setup already meets all requirements. If you do create them, use Quarkus profile-specific properties with the `%dev.` and `%prod.` prefixes within the main application.properties file.

*   **Tip - Quarkus Health Checks:** Health checks are **AUTOMATIC** in Quarkus when SmallRye Health is included (which it is). The datasource readiness check is automatically registered. You do NOT need to implement any custom health check code.

*   **Tip - Testing Environment Variables:** To verify environment variable overrides work correctly:
    ```bash
    # Test with custom database URL
    export DB_URL="jdbc:postgresql://localhost:5532/calendar_test"
    export DB_USERNAME="test_user"
    export DB_PASSWORD="test_password"
    ./mvnw quarkus:dev
    # Verify the application uses the overridden values
    ```

*   **Tip - PostGIS Extension:** PostGIS installation requires database superuser privileges. The setup guide already documents this correctly. When testing, ensure you connect as the postgres superuser to enable the extension:
    ```sql
    psql -U postgres -d calendar
    CREATE EXTENSION IF NOT EXISTS postgis;
    ```

*   **Warning - Database Port:** The configuration uses port **5532** (not the standard 5432) because it's designed to work with Docker Compose port mapping (host:5532 → container:5432). This is intentional and documented in the setup guide.

*   **Note - MyBatis Migrations:** The migrations directory structure is **ALREADY COMPLETE** with all required environment files. The migrations module is a separate Maven project with its own pom.xml - this is by design and follows MyBatis Migrations best practices.

### VERIFICATION CHECKLIST

Before marking this task complete, you MUST verify the following:

**Required Verifications:**
1. ✅ **Environment Variables Work**: Set DB_URL, DB_USERNAME, DB_PASSWORD environment variables and verify the application uses them instead of defaults
2. ✅ **Health Check Works**: Run `./mvnw quarkus:dev` and test `curl http://localhost:8030/q/health/ready` - verify it returns 200 with datasource status UP
3. ✅ **PostgreSQL Connection**: Verify the application successfully connects to PostgreSQL on startup (check console output for connection confirmation)
4. ✅ **MyBatis Migrations Structure**: Confirm all environment files exist:
   - `migrations/src/main/resources/environments/development.properties` ✅ (verified)
   - `migrations/src/main/resources/environments/production.properties` ✅ (verified)
   - `migrations/src/main/resources/environments/beta.properties` ✅ (verified)
   - `migrations/src/main/resources/environments/testing.properties` ✅ (verified)
5. ✅ **Documentation Accuracy**: Review `docs/guides/database-setup.md` to ensure all instructions are accurate and up-to-date

**Optional Enhancements (only if necessary):**
- ⚠️ Create `application-dev.properties` and `application-prod.properties` IF you decide profile-specific files are needed (currently NOT required)
- ⚠️ Update port references in acceptance criteria documentation to reflect actual port 8030

### IMPORTANT: What Already Exists and Works

**FULLY IMPLEMENTED:**
- ✅ PostgreSQL datasource configuration with environment variable support
- ✅ SmallRye Health dependency for automatic health checks
- ✅ Complete MyBatis Migrations directory structure (scripts/ and environments/)
- ✅ All environment configuration files (development, beta, testing, production)
- ✅ Comprehensive database setup guide (592 lines, covers all platforms and scenarios)
- ✅ Docker Compose PostgreSQL configuration
- ✅ PostGIS extension documentation

**WHAT YOU NEED TO DO:**
1. **Test** that the application connects to PostgreSQL successfully
2. **Verify** health checks work at the correct port (8030)
3. **Confirm** environment variable overrides function correctly
4. **Review** the database setup guide for any inaccuracies or needed updates
5. **Document** any findings or minor adjustments needed

**This task is primarily about VERIFICATION, not implementation.** Most components are already in place and working.
