# Project Plan: Village Calendar - Iteration 5

---

<!-- anchor: iteration-5-plan -->
## Iteration 5: Analytics, Observability & Admin Features

**Iteration ID:** `I5`

**Goal:** Implement business analytics tracking, integrate distributed tracing (Jaeger) and metrics (Prometheus), create admin analytics dashboard, and enhance admin capabilities for user/template management.

**Prerequisites:** `I2` (Calendar functionality), `I3` (E-commerce), `I4` (PDF generation)

**Duration Estimate:** 2-3 weeks

**Deliverables:**
- Page view tracking and analytics rollup jobs
- Jaeger distributed tracing integration
- Prometheus metrics export
- Admin analytics dashboard (revenue, conversion, popular templates)
- Admin user management interface
- Observability documentation

---

<!-- anchor: task-i5-t1 -->
### Task 5.1: Implement Page View Tracking

**Task ID:** `I5.T1`

**Description:**
Create AnalyticsService for tracking user interactions. Implement page view tracking: capture URL path, referrer, user agent, IP address, user_id (if authenticated), session_id (if guest). Create middleware in Quarkus (JAX-RS ContainerRequestFilter) to automatically log page views for all HTTP requests. Store page views in page_views table. Implement privacy controls: anonymize IP addresses (last octet), respect Do Not Track header. Add opt-out mechanism (cookie or user preference). Create analytics event tracking GraphQL mutations for frontend (trackEvent mutation for custom events like "calendar_created", "template_selected"). Write unit tests for analytics service.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Analytics requirements from Plan Section "Analytics & Conversion Tracking"
- PageView entity from I1.T8

**Input Files:**
- `src/main/java/com/villagecompute/calendar/model/PageView.java`
- `src/main/resources/application.properties`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/service/AnalyticsService.java`
- `src/main/java/com/villagecompute/calendar/repository/PageViewRepository.java`
- `src/main/java/com/villagecompute/calendar/api/rest/AnalyticsFilter.java` (request filter)
- `src/main/java/com/villagecompute/calendar/api/graphql/AnalyticsResolver.java`
- `src/test/java/com/villagecompute/calendar/service/AnalyticsServiceTest.java`

**Deliverables:**
- AnalyticsService with page view tracking
- Request filter for automatic page view logging
- Privacy controls (IP anonymization, Do Not Track)
- GraphQL trackEvent mutation for custom events
- Unit tests for analytics service

**Acceptance Criteria:**
- Every HTTP request automatically logged to page_views table
- Page view records include anonymized IP (last octet set to 0)
- Do Not Track header respected (requests with DNT=1 not tracked)
- GraphQL mutation `trackEvent(eventName: "calendar_created", metadata: {...})` creates PageView record
- Page views linked to user_id if authenticated, session_id if guest
- Unit tests verify IP anonymization, DNT handling

**Dependencies:** `I1.T8` (PageView entity)

**Parallelizable:** Yes (independent analytics implementation)

---

<!-- anchor: task-i5-t2 -->
### Task 5.2: Implement Analytics Rollup Job

**Task ID:** `I5.T2`

**Description:**
Create AnalyticsRollupJob implementing JobHandler interface for periodic analytics aggregation. Job workflow: (1) Query page_views table for previous day/week/month, (2) Calculate metrics: total page views, unique visitors (by user_id or session_id), popular pages (top 10 URLs), referrer sources (top 10 referrers), (3) Store aggregated data in analytics_rollups table with rollup_date and metric_name. Implement scheduled job (Quarkus @Scheduled) to run rollup daily at 2 AM UTC. Add business metrics: calendars created per day, orders placed per day, revenue per day, conversion rate (orders / calendars created), popular templates (template_id with calendar count). Write unit tests for rollup calculations.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- AnalyticsRollup entity from I1.T8
- JobHandler interface from I4.T1
- Business metrics requirements from Plan Section "Analytics & Conversion Tracking"

**Input Files:**
- `src/main/java/com/villagecompute/calendar/model/AnalyticsRollup.java`
- `src/main/java/com/villagecompute/calendar/jobs/JobHandler.java`
- `src/main/java/com/villagecompute/calendar/service/AnalyticsService.java`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/jobs/AnalyticsRollupJob.java`
- `src/main/java/com/villagecompute/calendar/repository/AnalyticsRollupRepository.java`
- `src/test/java/com/villagecompute/calendar/jobs/AnalyticsRollupJobTest.java`

**Deliverables:**
- AnalyticsRollupJob with daily aggregation logic
- Scheduled task running daily at 2 AM UTC
- Business metrics calculation (revenue, conversion rate, popular templates)
- Unit tests for rollup calculations

**Acceptance Criteria:**
- AnalyticsRollupJob aggregates previous day's data at 2 AM UTC
- Rollups stored with rollup_date and metric_name (e.g., "page_views", "revenue", "conversion_rate")
- Business metrics calculated: revenue (sum of order totals for day), conversion rate (orders / calendars created)
- Popular templates metric identifies top 10 templates by calendar count
- Job runs reliably via Quarkus Scheduler (no missed runs)
- Unit tests verify metric calculations with sample data

**Dependencies:** `I5.T1` (AnalyticsService), `I4.T1` (JobHandler)

**Parallelizable:** Yes (can develop concurrently with page view tracking)

---

<!-- anchor: task-i5-t3 -->
### Task 5.3: Integrate Jaeger for Distributed Tracing

**Task ID:** `I5.T3`

**Description:**
Configure Quarkus OpenTelemetry extension for distributed tracing. Add quarkus-opentelemetry dependency to POM. Configure Jaeger exporter in application.properties (Jaeger endpoint, service name "village-calendar-api"). Enable automatic instrumentation for: HTTP requests (JAX-RS, GraphQL), database queries (JDBC), external HTTP calls (Stripe, OAuth, R2). Add custom spans for critical operations: PDF generation (@WithSpan annotation), astronomical calculations, order placement. Configure trace sampling (100% in dev, 10% in production for performance). Deploy Jaeger all-in-one container to Kubernetes (or use existing Jaeger instance). Test trace visualization in Jaeger UI. Document tracing setup in docs/guides/observability-setup.md.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Observability requirements from Plan Section "Logging & Monitoring"
- Quarkus OpenTelemetry documentation
- Jaeger deployment requirements

**Input Files:**
- `pom.xml`
- `src/main/resources/application.properties`

**Target Files:**
- `pom.xml` (add quarkus-opentelemetry dependency)
- `src/main/resources/application.properties` (add Jaeger config)
- `infrastructure/kubernetes/base/jaeger-deployment.yaml` (Jaeger Kubernetes manifest)
- `docs/guides/observability-setup.md`

**Deliverables:**
- OpenTelemetry extension configured
- Automatic instrumentation enabled for HTTP, JDBC, external calls
- Custom spans for critical operations
- Jaeger deployed to Kubernetes
- Trace visualization tested in Jaeger UI
- Observability setup guide

**Acceptance Criteria:**
- Quarkus sends traces to Jaeger endpoint successfully
- HTTP request traces appear in Jaeger UI with request details
- Database query spans show SQL statements and execution time
- Custom @WithSpan annotations create spans (e.g., "generateCalendarPdf")
- Trace sampling configured (100% dev, 10% prod)
- Jaeger UI accessible at http://jaeger.internal.villagecompute.com (via Kubernetes service)
- Observability guide tested with fresh Jaeger deployment

**Dependencies:** None (independent integration)

**Parallelizable:** Yes (can develop concurrently with analytics)

---

<!-- anchor: task-i5-t4 -->
### Task 5.4: Configure Prometheus Metrics Export

**Task ID:** `I5.T4`

**Description:**
Configure Quarkus Micrometer extension for Prometheus metrics export. Add quarkus-micrometer-registry-prometheus dependency to POM. Configure Micrometer in application.properties (enable Prometheus endpoint at /q/metrics). Enable automatic metrics: HTTP requests (request count, duration histogram), database (connection pool size, active connections, query duration), JVM (memory usage, GC, threads). Add custom business metrics: calendars_created_total (counter), orders_placed_total (counter), revenue_total (counter in cents), pdf_downloads_total (counter), delayed_jobs_queue_depth (gauge). Deploy Prometheus server to Kubernetes with scrape config for Village Calendar API pods. Test metrics visualization in Prometheus UI. Create example PromQL queries in documentation.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Metrics requirements from Plan Section "Logging & Monitoring"
- Quarkus Micrometer documentation

**Input Files:**
- `pom.xml`
- `src/main/resources/application.properties`

**Target Files:**
- `pom.xml` (add quarkus-micrometer-registry-prometheus dependency)
- `src/main/resources/application.properties` (add Micrometer config)
- `src/main/java/com/villagecompute/calendar/util/metrics/BusinessMetrics.java` (custom metrics)
- `infrastructure/kubernetes/base/prometheus-deployment.yaml`
- `infrastructure/kubernetes/base/prometheus-config.yaml` (scrape config)
- `docs/guides/observability-setup.md` (add Prometheus section)

**Deliverables:**
- Micrometer extension configured
- Prometheus metrics endpoint exposed at /q/metrics
- Custom business metrics implemented
- Prometheus server deployed to Kubernetes
- Metrics visualization tested in Prometheus UI
- Example PromQL queries documented

**Acceptance Criteria:**
- `/q/metrics` endpoint returns Prometheus-formatted metrics
- HTTP request metrics include request_count, request_duration_seconds
- Database metrics include hikaricp_connections_active
- Custom business metrics increment correctly (calendars_created_total increases when calendar created)
- Prometheus scrapes Village Calendar API pods every 15 seconds
- Prometheus UI accessible at http://prometheus.internal.villagecompute.com
- Example PromQL queries: `rate(http_requests_total[5m])`, `delayed_jobs_queue_depth`

**Dependencies:** None (independent integration)

**Parallelizable:** Yes (can develop concurrently with Jaeger integration)

---

<!-- anchor: task-i5-t5 -->
### Task 5.5: Build Admin Analytics Dashboard (Vue)

**Task ID:** `I5.T5`

**Description:**
Create admin analytics dashboard displaying business metrics. AnalyticsDashboard.vue (main admin analytics view), RevenueChart.vue (line chart showing daily/weekly/monthly revenue), ConversionFunnel.vue (visualization of user journey: visitors → calendars → orders), PopularTemplates.vue (table showing template usage counts), MetricCards.vue (KPI cards for total users, calendars, orders, revenue). Use PrimeVue Chart.js integration for visualizations. Implement date range selector (last 7 days, 30 days, 90 days, custom range). Integrate with GraphQL API (analytics query returning aggregated data from analytics_rollups table). Add export functionality (download CSV of analytics data). Protect route with admin role check.

**Agent Type Hint:** `FrontendAgent`

**Inputs:**
- Analytics dashboard requirements from Plan Section "Business Metrics Dashboard"
- PrimeVue Chart documentation

**Input Files:**
- `frontend/src/views/AdminPanel.vue`
- `api/graphql-schema.graphql`

**Target Files:**
- `frontend/src/views/admin/AnalyticsDashboard.vue`
- `frontend/src/components/admin/RevenueChart.vue`
- `frontend/src/components/admin/ConversionFunnel.vue`
- `frontend/src/components/admin/PopularTemplates.vue`
- `frontend/src/components/admin/MetricCards.vue`
- `frontend/src/graphql/analytics-queries.ts`

**Deliverables:**
- Admin analytics dashboard with visualizations
- Revenue chart (line chart with date range selector)
- Conversion funnel visualization
- Popular templates table
- KPI metric cards (users, calendars, orders, revenue)
- GraphQL integration (analytics query)
- CSV export functionality

**Acceptance Criteria:**
- Dashboard displays revenue line chart for selected date range
- Conversion funnel shows: total visitors → calendars created → orders placed (with percentages)
- Popular templates table shows top 10 templates with calendar count
- Metric cards show totals: total users, total calendars, total orders, total revenue
- Date range selector updates charts and tables
- CSV export downloads analytics data as spreadsheet
- Non-admin users redirected from /admin/analytics route

**Dependencies:** `I5.T2` (AnalyticsRollupJob for data source)

**Parallelizable:** Partially (can develop UI with mock data, integrate with API later)

---

<!-- anchor: task-i5-t6 -->
### Task 5.6: Implement Admin User Management Interface

**Task ID:** `I5.T6`

**Description:**
Create admin interface for user management. UserManagementDashboard.vue (admin view, lists all users with search/filtering), UserDetailsPanel.vue (detailed user view with profile, calendars, orders), UserRoleUpdater.vue (admin tool to promote user to admin or demote). Use PrimeVue DataTable with pagination, search by email, filtering by role. Implement admin actions: view user's calendars, view user's orders, change user role (user ↔ admin), disable user account (soft delete). Create GraphQL queries/mutations: users (admin query for all users), updateUserRole (admin mutation). Write integration tests for admin user management workflows.

**Agent Type Hint:** `BackendAgent` and `FrontendAgent`

**Inputs:**
- User management requirements from Plan Section "Admin"
- User entity from I1.T8

**Input Files:**
- `src/main/java/com/villagecompute/calendar/model/User.java`
- `frontend/src/views/AdminPanel.vue`
- `api/graphql-schema.graphql`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/api/graphql/AdminUserResolver.java`
- `frontend/src/views/admin/UserManagementDashboard.vue`
- `frontend/src/components/admin/UserDetailsPanel.vue`
- `frontend/src/components/admin/UserRoleUpdater.vue`
- `src/test/java/com/villagecompute/calendar/api/graphql/AdminUserResolverTest.java`

**Deliverables:**
- Backend: AdminUserResolver with users query and updateUserRole mutation
- Frontend: User management dashboard with search/filtering
- User details panel with calendars and orders
- Role updater (admin can promote/demote users)
- Integration tests for user management

**Acceptance Criteria:**
- User dashboard displays all users in DataTable (paginated)
- Search by email filters users in real-time
- Clicking user row opens UserDetailsPanel with full profile, calendars list, orders list
- Admin can change user role (user → admin), verify role updated in database
- GraphQL mutation updateUserRole requires admin role (throws error if non-admin)
- Integration test verifies role change workflow
- Non-admin users cannot access /admin/users route

**Dependencies:** `I1.T8` (User entity)

**Parallelizable:** Yes (backend and frontend can develop concurrently)

---

<!-- anchor: task-i5-t7 -->
### Task 5.7: Implement Health Check Custom Probes

**Task ID:** `I5.T7`

**Description:**
Implement custom health checks for Kubernetes liveness and readiness probes. Create custom HealthCheck implementations: DatabaseHealthCheck (verify database connection pool healthy), JobQueueHealthCheck (verify delayed_jobs table accessible, queue not critically backlogged >1000 jobs), R2HealthCheck (verify R2 storage reachable). Configure SmallRye Health endpoints: /q/health/live (liveness - app is running, not deadlocked), /q/health/ready (readiness - app ready to serve traffic, database and dependencies healthy). Add startup probe (for slow initial startup, e.g., database migration). Test health checks in Kubernetes (simulate database failure, verify pod restart). Document health check behavior in docs/guides/observability-setup.md.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Health check requirements from Plan Section "Logging & Monitoring"
- SmallRye Health documentation

**Input Files:**
- `src/main/resources/application.properties`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/health/DatabaseHealthCheck.java`
- `src/main/java/com/villagecompute/calendar/health/JobQueueHealthCheck.java`
- `src/main/java/com/villagecompute/calendar/health/R2HealthCheck.java`
- `infrastructure/kubernetes/base/deployment.yaml` (update with health probe config)
- `docs/guides/observability-setup.md` (add health check section)

**Deliverables:**
- Custom health check implementations
- Liveness and readiness probes configured
- Startup probe for database migrations
- Kubernetes deployment updated with probe config
- Health check behavior documented

**Acceptance Criteria:**
- `/q/health/live` returns UP if app is running (no deadlocks)
- `/q/health/ready` returns UP only if database, R2, and job queue healthy
- DatabaseHealthCheck fails if database connection fails (readiness probe fails)
- JobQueueHealthCheck fails if queue depth >1000 (readiness probe fails, pod removed from load balancer)
- Kubernetes restarts pod if liveness probe fails 3 times consecutively
- Startup probe allows 60 seconds for initial database migration
- Health check documentation explains liveness vs readiness

**Dependencies:** `I4.T4` (R2StorageService for R2 health check)

**Parallelizable:** Yes (can develop concurrently with metrics integration)

---

<!-- anchor: task-i5-t8 -->
### Task 5.8: Configure Logging with Structured JSON Format

**Task ID:** `I5.T8`

**Description:**
Configure Quarkus logging to output structured JSON logs for production. Update application-prod.properties with JSON log format (use quarkus-logging-json extension). Configure log levels: ERROR for application errors, WARN for degraded operation, INFO for significant events, DEBUG disabled in production. Add contextual logging: include trace_id and span_id from OpenTelemetry in all log entries (enables correlation with Jaeger traces), include user_id if authenticated. Configure log output: stdout for containerized environments (Kubernetes collects logs). Create log aggregation setup guide (using fluentd/fluent-bit to forward logs to Loki or ELK stack - future integration). Test JSON log format in production environment.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Logging requirements from Plan Section "Logging & Monitoring"
- Quarkus logging documentation

**Input Files:**
- `src/main/resources/application-prod.properties`
- `pom.xml`

**Target Files:**
- `pom.xml` (add quarkus-logging-json dependency)
- `src/main/resources/application-prod.properties` (configure JSON logging)
- `docs/guides/observability-setup.md` (add logging section)

**Deliverables:**
- JSON structured logging configured for production
- Log levels set appropriately (ERROR, WARN, INFO)
- Contextual logging (trace_id, user_id included)
- Log aggregation setup guide

**Acceptance Criteria:**
- Production logs output in JSON format (one JSON object per line)
- Log entries include: timestamp, level, logger, message, trace_id, span_id
- Authenticated request logs include user_id field
- Log levels: application errors logged at ERROR, slow queries at WARN
- Log output to stdout (Kubernetes captures and forwards)
- Log aggregation guide documents fluentd/fluent-bit setup
- JSON log format validated with online JSON validator

**Dependencies:** `I5.T3` (Jaeger integration for trace_id/span_id)

**Parallelizable:** Yes (configuration task)

---

<!-- anchor: task-i5-t9 -->
### Task 5.9: Create Observability Documentation and Runbooks

**Task ID:** `I5.T9`

**Description:**
Write comprehensive observability documentation. Update docs/guides/observability-setup.md with complete setup instructions for: Jaeger deployment, Prometheus deployment, log aggregation (future), health checks, custom metrics. Create runbooks in docs/runbooks/ for common operational scenarios: investigating slow requests (using Jaeger traces), debugging failed jobs (using Jaeger and job queue dashboard), monitoring queue backlog (Prometheus alerts), troubleshooting database connection issues (health checks, logs). Write alert runbook explaining what each Prometheus alert means and how to respond. Create dashboard screenshot gallery showing Jaeger UI, Prometheus UI, admin analytics dashboard.

**Agent Type Hint:** `DocumentationAgent`

**Inputs:**
- All observability implementations from I5 tasks
- Operational scenarios from Plan Section "Monitoring & Alerting"

**Input Files:**
- `docs/guides/observability-setup.md`
- All observability-related code from I5 tasks

**Target Files:**
- `docs/guides/observability-setup.md` (updated)
- `docs/runbooks/investigating-slow-requests.md`
- `docs/runbooks/debugging-failed-jobs.md`
- `docs/runbooks/monitoring-queue-backlog.md`
- `docs/runbooks/database-connection-issues.md`
- `docs/runbooks/prometheus-alerts.md`
- `docs/screenshots/` (Jaeger, Prometheus, analytics dashboard screenshots)

**Deliverables:**
- Complete observability setup guide
- Operational runbooks for common scenarios
- Alert runbook with response procedures
- Screenshot gallery

**Acceptance Criteria:**
- Observability setup guide covers all monitoring components
- Runbooks provide step-by-step troubleshooting instructions
- Slow request runbook demonstrates using Jaeger to identify bottleneck
- Failed job runbook shows how to view job error logs and retry
- Alert runbook explains each Prometheus alert with severity and response
- Screenshots show actual system UIs (not mock data)
- Documentation tested by following runbooks in live environment

**Dependencies:** All I5 tasks (requires complete observability implementation)

**Parallelizable:** Yes (can write documentation concurrently with implementations)

---

<!-- anchor: task-i5-t10 -->
### Task 5.10: Write Integration Tests for Analytics and Observability

**Task ID:** `I5.T10`

**Description:**
Create integration tests for analytics and observability features. Test scenarios: (1) Page view tracking - make HTTP request, verify page_view record created with correct data; (2) Analytics rollup - insert sample page_views, run rollup job, verify analytics_rollups populated with correct metrics; (3) Business metrics - create calendar, place order, verify calendars_created_total and orders_placed_total metrics incremented; (4) Jaeger tracing - make GraphQL request, verify trace appears in Jaeger (query Jaeger API); (5) Health checks - simulate database failure (stop database container), verify /q/health/ready returns DOWN. Use Testcontainers for PostgreSQL and Jaeger. Achieve >70% coverage for analytics and observability code.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- All analytics and observability implementations from I5 tasks

**Input Files:**
- All service and resolver classes from I5 tasks
- `api/graphql-schema.graphql`

**Target Files:**
- `src/test/java/com/villagecompute/calendar/integration/AnalyticsTrackingTest.java`
- `src/test/java/com/villagecompute/calendar/integration/AnalyticsRollupTest.java`
- `src/test/java/com/villagecompute/calendar/integration/ObservabilityTest.java`

**Deliverables:**
- Integration tests for analytics tracking and rollup
- Integration tests for business metrics
- Integration tests for Jaeger tracing
- Integration tests for health checks
- Tests achieve >70% coverage for analytics/observability code

**Acceptance Criteria:**
- Page view test makes HTTP GET, verifies page_views table has new record
- Rollup test runs AnalyticsRollupJob, verifies rollups calculated correctly
- Business metrics test verifies Prometheus metrics increment (query /q/metrics endpoint)
- Jaeger test queries Jaeger API, verifies trace with correct service name and spans
- Health check test stops database container, verifies /q/health/ready returns 503
- All integration tests pass with `./mvnw verify`
- Tests run in <90 seconds

**Dependencies:** All I5 tasks (requires complete analytics and observability)

**Parallelizable:** No (final integration testing task)

---

<!-- anchor: iteration-5-summary -->
### Iteration 5 Summary

**Total Tasks:** 10

**Completion Criteria:**
- All tasks marked as completed
- Analytics tracking functional (page views, rollups, business metrics)
- Jaeger distributed tracing integrated and working
- Prometheus metrics exported and scrapeable
- Admin analytics dashboard displaying visualizations
- Admin user management interface functional
- Health checks configured for Kubernetes
- Structured JSON logging configured
- Observability documentation and runbooks complete
- Integration tests passing with adequate coverage

**Risk Mitigation:**
- If Jaeger deployment proves complex in k3s, use Jaeger all-in-one container initially
- If Prometheus scraping has issues, verify network policies allow access to /q/metrics
- If analytics rollup performance is slow, add database indexes on page_views.created_at

**Next Iteration Preview:**
Iteration 6 (final) will focus on deployment, security hardening, testing, and launch preparation: Kubernetes manifests, CI/CD refinement, security audit, load testing, and documentation finalization.
