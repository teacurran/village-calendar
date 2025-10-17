# Project Plan: Village Calendar - Iteration 6

---

<!-- anchor: iteration-6-plan -->
## Iteration 6: Deployment, Security Hardening & Launch Preparation

**Iteration ID:** `I6`

**Goal:** Finalize production deployment infrastructure, conduct security hardening and audit, perform comprehensive testing (load/security/E2E), create user documentation, and prepare for MVP launch.

**Prerequisites:** `I1-I5` (All core functionality, e-commerce, PDF generation, analytics complete)

**Duration Estimate:** 2-3 weeks

**Deliverables:**
- Complete Kubernetes manifests (production-ready)
- Terraform configurations for Cloudflare resources
- Security audit and fixes
- Load testing and performance validation
- End-to-end testing suite
- User documentation and help center
- Launch checklist and rollback plan

---

<!-- anchor: task-i6-t1 -->
### Task 6.1: Create Production Kubernetes Manifests

**Task ID:** `I6.T1`

**Description:**
Create complete Kubernetes manifests for production deployment. Define manifests for: Deployment (calendar-api with replicas, resource limits, env variables), Deployment (calendar-worker for job processing), Service (ClusterIP for internal access), HorizontalPodAutoscaler (HPA for API and worker pods), ConfigMap (non-sensitive config), Secret (sensitive config: database credentials, Stripe API keys, OAuth client secrets), NetworkPolicy (restrict pod-to-pod communication). Use Kustomize overlays for environment-specific config (beta vs production). Configure rolling update strategy (maxSurge: 1, maxUnavailable: 0). Add pod disruption budget (ensure min 1 pod always running). Document deployment process in docs/guides/deployment.md.

**Agent Type Hint:** `SetupAgent`

**Inputs:**
- Kubernetes deployment requirements from Plan Section "Deployment View"
- Existing deployment diagram from I1.T5

**Input Files:**
- `infrastructure/kubernetes/base/deployment.yaml` (if exists from I1)
- `docs/diagrams/deployment_diagram.puml`

**Target Files:**
- `infrastructure/kubernetes/base/deployment.yaml` (calendar-api)
- `infrastructure/kubernetes/base/worker-deployment.yaml` (calendar-worker)
- `infrastructure/kubernetes/base/service.yaml`
- `infrastructure/kubernetes/base/hpa.yaml`
- `infrastructure/kubernetes/base/configmap.yaml`
- `infrastructure/kubernetes/base/secret.example.yaml` (template, not actual secrets)
- `infrastructure/kubernetes/base/networkpolicy.yaml`
- `infrastructure/kubernetes/base/poddisruptionbudget.yaml`
- `infrastructure/kubernetes/overlays/beta/kustomization.yaml`
- `infrastructure/kubernetes/overlays/production/kustomization.yaml`
- `docs/guides/deployment.md`

**Deliverables:**
- Production-ready Kubernetes manifests
- Kustomize overlays for beta and production environments
- Secret management documented (use Kubernetes secrets, not committed to Git)
- Deployment guide with kubectl commands

**Acceptance Criteria:**
- Deployment manifest defines API pods with resource requests (512Mi memory, 500m CPU) and limits (2Gi, 2000m)
- Worker deployment has higher memory limit (4Gi for PDF rendering)
- HPA configured: API scales 2-10 pods based on CPU >70%, Worker scales 1-5 based on custom metric (queue depth)
- ConfigMap includes non-sensitive settings (database host, R2 bucket name)
- Secret template includes placeholders for all sensitive values
- NetworkPolicy restricts egress to database, R2, external APIs only
- Kustomize overlays apply environment-specific changes (namespace, image tags)
- Deployment guide tested on fresh k3s cluster

**Dependencies:** `I1.T1` (project structure)

**Parallelizable:** Yes (independent infrastructure work)

---

<!-- anchor: task-i6-t2 -->
### Task 6.2: Create Terraform Configurations for Cloudflare

**Task ID:** `I6.T2`

**Description:**
Write Terraform configurations for all Cloudflare resources. Define Terraform modules for: DNS records (calendar.villagecompute.com, calendar-beta.villagecompute.com, A records pointing to Cloudflare Tunnel), Cloudflare Tunnel (tunnel configuration, ingress rules to k3s cluster), R2 buckets (village-calendar-pdfs bucket with CORS config, lifecycle rules), CDN cache settings (page rules for static assets, cache TTL), WAF rules (block common attack patterns, rate limiting). Configure Terraform backend (S3 for state, DynamoDB for locking). Add Terraform provider versions (Cloudflare provider 4.0+, AWS provider for S3 state). Document Terraform usage in docs/guides/infrastructure.md.

**Agent Type Hint:** `SetupAgent`

**Inputs:**
- Infrastructure requirements from Plan Section "Infrastructure as Code"
- Cloudflare Tunnel and R2 requirements

**Input Files:**
- `infrastructure/terraform/cloudflare/` (if exists)

**Target Files:**
- `infrastructure/terraform/cloudflare/main.tf`
- `infrastructure/terraform/cloudflare/dns.tf` (DNS records)
- `infrastructure/terraform/cloudflare/tunnel.tf` (Cloudflare Tunnel config)
- `infrastructure/terraform/cloudflare/r2.tf` (R2 bucket definitions)
- `infrastructure/terraform/cloudflare/waf.tf` (WAF rules)
- `infrastructure/terraform/cloudflare/variables.tf`
- `infrastructure/terraform/cloudflare/outputs.tf`
- `infrastructure/terraform/backend.tf` (S3 + DynamoDB backend)
- `docs/guides/infrastructure.md`

**Deliverables:**
- Complete Terraform configurations for Cloudflare resources
- Terraform backend configured (S3 state storage)
- Infrastructure documentation with terraform apply instructions

**Acceptance Criteria:**
- `terraform plan` shows all Cloudflare resources to be created (no errors)
- `terraform apply` creates DNS records, tunnel, R2 bucket successfully
- R2 bucket configured with CORS (allow GET from calendar.villagecompute.com)
- WAF rules include rate limiting (100 requests per minute per IP)
- Terraform state stored in S3 bucket with DynamoDB locking
- Infrastructure guide explains: terraform init, plan, apply, state management
- Terraform configurations tested on fresh Cloudflare account

**Dependencies:** None (independent infrastructure work)

**Parallelizable:** Yes (can develop concurrently with Kubernetes manifests)

---

<!-- anchor: task-i6-t3 -->
### Task 6.3: Conduct Security Audit and Hardening

**Task ID:** `I6.T3`

**Description:**
Perform comprehensive security audit of application. Use automated tools: OWASP ZAP (web application scanning), Snyk (dependency vulnerability scanning), SonarQube (code quality and security issues). Manual security review: check for common vulnerabilities (SQL injection, XSS, CSRF, insecure deserialization, authentication bypasses). Verify security controls from Plan Section 3.8.3: input validation, output encoding, HTTPS everywhere, rate limiting, secrets management. Fix identified vulnerabilities (prioritize high/critical severity). Implement additional security headers: X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy. Document security findings and remediation in docs/security-audit-report.md. Create security checklist for ongoing deployments.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Security requirements from Plan Section "Security Considerations"
- OWASP Top 10 vulnerabilities

**Input Files:**
- All source code from src/
- `pom.xml` (for dependency scanning)

**Target Files:**
- `docs/security-audit-report.md` (findings and fixes)
- `docs/security-checklist.md` (ongoing security verification)
- `src/main/java/com/villagecompute/calendar/security/SecurityHeadersFilter.java` (add security headers)
- (Various code files with security fixes)

**Deliverables:**
- Security audit report with findings and remediation
- All high/critical vulnerabilities fixed
- Security headers implemented
- Security checklist for deployments

**Acceptance Criteria:**
- OWASP ZAP scan shows no high/critical vulnerabilities
- Snyk dependency scan shows no critical vulnerabilities (or documented exceptions)
- SonarQube security rating: A (no security hotspots)
- Security headers present in all HTTP responses (X-Frame-Options: DENY, etc.)
- SQL injection test (malicious input in GraphQL queries) does not succeed
- XSS test (malicious script in calendar event text) does not execute in browser
- CSRF test (cross-origin mutation request) is blocked
- Secrets not committed to Git repository (verified with git-secrets tool)
- Security audit report documents all findings with severity and remediation

**Dependencies:** All previous iterations (requires complete codebase)

**Parallelizable:** No (requires full application review)

---

<!-- anchor: task-i6-t4 -->
### Task 6.4: Perform Load Testing and Performance Validation

**Task ID:** `I6.T4`

**Description:**
Conduct load testing to validate performance under expected traffic. Use JMeter or Gatling for load simulation. Test scenarios: (1) Baseline load - 100 concurrent users, calendar CRUD operations, verify p95 latency <500ms; (2) Sustained load - 500 concurrent users for 1 hour, verify no errors, no memory leaks; (3) Peak load (holiday season simulation) - ramp up to 2000 concurrent users over 10 minutes, verify HPA scales pods, latency remains acceptable; (4) PDF generation load - enqueue 100 PDF jobs simultaneously, verify all complete within 10 minutes, no worker crashes. Monitor system during tests: CPU, memory, database connections, job queue depth. Identify and fix performance bottlenecks. Document load test results in docs/load-test-report.md with graphs and metrics.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Performance requirements from Plan Section "Scalability & Performance"
- Load testing scenarios from Plan Section "Load Testing"

**Input Files:**
- All deployed application components

**Target Files:**
- `tests/load/jmeter-calendar-crud.jmx` (JMeter test plan)
- `tests/load/jmeter-pdf-generation.jmx`
- `docs/load-test-report.md` (results and analysis)

**Deliverables:**
- Load test scripts (JMeter or Gatling)
- Load test execution on beta environment
- Performance bottlenecks identified and fixed
- Load test report with metrics and graphs

**Acceptance Criteria:**
- Baseline test: p95 latency <500ms for GraphQL queries, p99 <1s
- Sustained load: zero errors over 1 hour, memory usage stable (no leaks)
- Peak load: HPA scales API pods from 2 to 8, latency p95 <2s under 2000 users
- PDF generation load: 100 jobs complete in <8 minutes average
- Database connection pool never exhausted (max connections not hit)
- Load test report includes: throughput (req/sec), latency percentiles (p50, p95, p99), error rate, resource utilization graphs
- Performance bottlenecks documented with fixes (e.g., added index on frequently queried column)

**Dependencies:** `I6.T1` (requires deployed application)

**Parallelizable:** No (requires deployed environment)

---

<!-- anchor: task-i6-t5 -->
### Task 6.5: Create End-to-End Testing Suite

**Task ID:** `I6.T5`

**Description:**
Build end-to-end (E2E) testing suite using Cypress or Playwright. Test critical user flows: (1) Guest user creates calendar - visit home, start from template, add events, see preview; (2) User signup and login - authenticate via Google OAuth (test mode), verify redirect and JWT token; (3) Checkout flow - add calendar to cart, fill shipping form, redirect to Stripe Checkout, simulate payment success (use Stripe test mode), verify order confirmation; (4) Admin workflow - login as admin, view orders dashboard, update order status, view analytics dashboard. Configure E2E tests to run against beta environment (not production). Add E2E tests to CI/CD pipeline (run after deployment to beta). Document E2E test setup in docs/guides/testing.md.

**Agent Type Hint:** `FrontendAgent`

**Inputs:**
- User workflows from Plan Sections "Calendars", "Login", "Checkout"
- Cypress or Playwright documentation

**Input Files:**
- `frontend/src/` (all Vue components)

**Target Files:**
- `tests/e2e/cypress/support/commands.js` (custom Cypress commands)
- `tests/e2e/cypress/integration/guest-calendar-creation.spec.js`
- `tests/e2e/cypress/integration/user-auth.spec.js`
- `tests/e2e/cypress/integration/checkout-flow.spec.js`
- `tests/e2e/cypress/integration/admin-workflow.spec.js`
- `tests/e2e/cypress.json` (Cypress config)
- `.github/workflows/e2e-tests.yml` (CI/CD E2E workflow)
- `docs/guides/testing.md` (E2E test setup and execution)

**Deliverables:**
- E2E test suite with Cypress or Playwright
- Tests for all critical user flows
- E2E tests integrated into CI/CD pipeline
- Testing documentation

**Acceptance Criteria:**
- Guest calendar creation test navigates to editor, adds event, saves to session
- User auth test completes OAuth flow (mocked or test credentials), verifies JWT in localStorage
- Checkout test fills form, redirects to Stripe, simulates payment, verifies order in database
- Admin test logs in as admin, views orders, updates status, views analytics
- All E2E tests pass against beta environment
- E2E tests run in CI/CD after beta deployment (fail deployment if tests fail)
- Testing guide explains: running tests locally, updating test data, debugging failures

**Dependencies:** `I6.T1` (requires deployed beta environment)

**Parallelizable:** Partially (can write tests, but execution requires deployment)

---

<!-- anchor: task-i6-t6 -->
### Task 6.6: Create User Documentation and Help Center

**Task ID:** `I6.T6`

**Description:**
Write comprehensive user-facing documentation for Village Calendar. Create help center content: (1) Getting Started - how to create first calendar, apply templates, add events; (2) Calendar Editor Guide - detailed instructions for all editor features (events, emojis, holidays, astronomy); (3) Ordering Calendars - checkout process, payment methods, shipping options, tracking; (4) Account Management - login, profile settings, saved calendars, order history; (5) FAQ - common questions (pricing, shipping times, refunds, PDF quality). Add in-app help tooltips (PrimeVue Tooltip on key UI elements). Create video tutorials for complex workflows (calendar creation, checkout). Write Terms of Service and Privacy Policy (legal compliance). Publish help center as static site (e.g., help.villagecompute.com) or integrate into main app.

**Agent Type Hint:** `DocumentationAgent`

**Inputs:**
- User features from Plan Sections "Calendars", "Checkout", "Account Management"
- Legal requirements from Plan Section "Legal & Compliance"

**Input Files:**
- All frontend Vue components (to understand UI)

**Target Files:**
- `docs/help-center/getting-started.md`
- `docs/help-center/calendar-editor-guide.md`
- `docs/help-center/ordering-calendars.md`
- `docs/help-center/account-management.md`
- `docs/help-center/faq.md`
- `docs/legal/terms-of-service.md`
- `docs/legal/privacy-policy.md`
- `frontend/src/components/common/HelpTooltip.vue` (reusable tooltip component)

**Deliverables:**
- Help center documentation (Markdown)
- Terms of Service and Privacy Policy
- In-app help tooltips on key features
- (Optional) Video tutorial scripts

**Acceptance Criteria:**
- Getting started guide walks through creating first calendar with screenshots
- Calendar editor guide explains all features with examples (events, holidays, astronomy)
- Ordering guide covers entire checkout flow with Stripe payment steps
- FAQ answers at least 20 common questions
- Terms of Service covers user responsibilities, prohibited content, refund policy
- Privacy Policy discloses data collection (emails, addresses, OAuth providers), GDPR/CCPA compliance
- Help tooltips added to calendar editor (emoji picker, holiday selector, astronomy toggle)
- Documentation reviewed for clarity and accuracy

**Dependencies:** All previous iterations (requires complete feature set)

**Parallelizable:** Yes (documentation can be written concurrently with testing)

---

<!-- anchor: task-i6-t7 -->
### Task 6.7: Configure Production Monitoring and Alerting

**Task ID:** `I6.T7`

**Description:**
Set up production monitoring and alerting infrastructure. Configure Prometheus alert rules for critical conditions: high error rate (>1% for 5 minutes), API latency spike (p95 >2s for 10 minutes), database down (connection failures), job queue backlog (pending jobs >500 for 15 minutes), pod crash loop (restart >5 in 10 minutes), disk space low (>85% usage). Configure Alertmanager for alert routing: critical alerts to PagerDuty (or email/SMS), high priority to Slack #ops-alerts channel, medium priority to email. Create alert runbook linking alerts to troubleshooting docs. Set up uptime monitoring (Pingdom or UptimeRobot) for external availability checks. Test alerting by simulating failures (stop database, flood job queue).

**Agent Type Hint:** `SetupAgent`

**Inputs:**
- Alerting requirements from Plan Section "Monitoring & Alerting"
- Prometheus from I5.T4

**Input Files:**
- `infrastructure/kubernetes/base/prometheus-config.yaml`

**Target Files:**
- `infrastructure/kubernetes/base/prometheus-alerts.yaml` (Prometheus alert rules)
- `infrastructure/kubernetes/base/alertmanager-config.yaml` (Alertmanager routing config)
- `infrastructure/kubernetes/base/alertmanager-deployment.yaml`
- `docs/runbooks/prometheus-alerts.md` (updated with all alerts)

**Deliverables:**
- Prometheus alert rules configured
- Alertmanager deployed and configured
- Uptime monitoring configured (external service)
- Alert testing completed
- Alert runbook updated

**Acceptance Criteria:**
- Prometheus alert rules defined for all critical conditions (8+ rules)
- Alertmanager routes critical alerts to PagerDuty/email, high to Slack
- Simulated database failure triggers "database_down" alert within 1 minute
- Simulated job queue flood triggers "job_queue_backlog" alert
- Alert runbook documents each alert: what it means, severity, how to respond
- Uptime monitoring pings calendar.villagecompute.com every 5 minutes
- Alert testing verified all channels (PagerDuty, Slack, email) receive alerts

**Dependencies:** `I5.T4` (Prometheus metrics), `I6.T1` (deployed environment)

**Parallelizable:** Partially (can configure, but testing requires deployment)

---

<!-- anchor: task-i6-t8 -->
### Task 6.8: Create Deployment and Rollback Procedures

**Task ID:** `I6.T8`

**Description:**
Document detailed deployment and rollback procedures for production releases. Write deployment runbook (docs/runbooks/deployment.md) covering: pre-deployment checklist (database migrations tested, CI/CD passed, changelog prepared), deployment steps (kubectl apply, watch rollout, verify health checks), smoke testing (health check, sample GraphQL query, order placement), rollback procedure (kubectl rollout undo, database migration rollback if needed). Create rollback decision criteria (when to rollback: error rate >5%, critical functionality broken, database corruption). Document database migration safety (backward-compatible schema changes, test migrations on staging). Add blue-green deployment strategy (future enhancement). Test deployment and rollback on beta environment.

**Agent Type Hint:** `DocumentationAgent`

**Inputs:**
- Deployment strategy from Plan Section "Deployment Strategy"
- Database migrations from I1.T7

**Input Files:**
- `infrastructure/kubernetes/` (all manifests)
- `migrations/scripts/` (all migration files)

**Target Files:**
- `docs/runbooks/deployment.md`
- `docs/runbooks/rollback.md`
- `docs/guides/database-migrations.md` (migration safety guide)

**Deliverables:**
- Deployment runbook with step-by-step instructions
- Rollback runbook with decision criteria
- Database migration safety guide
- Deployment tested on beta environment

**Acceptance Criteria:**
- Deployment runbook includes: pre-deployment checklist (10+ items), kubectl commands, smoke test steps
- Rollback runbook explains when to rollback, how to execute rollback (kubectl rollout undo)
- Database migration guide explains backward compatibility (add column with default, deploy, then remove old column in next release)
- Runbooks tested by deploying to beta, verifying health, intentionally introducing error, rolling back
- Rollback successfully reverts to previous version within 5 minutes
- Smoke tests verify: health check returns 200, GraphQL query succeeds, order placement works

**Dependencies:** `I6.T1` (Kubernetes manifests), `I1.T7` (database migrations)

**Parallelizable:** Yes (documentation task)

---

<!-- anchor: task-i6-t9 -->
### Task 6.9: Finalize CI/CD Pipeline and Automation

**Task ID:** `I6.T9`

**Description:**
Refine and finalize CI/CD pipeline for production readiness. Update GitHub Actions workflows: (1) CI workflow - add security scanning (Snyk, OWASP dependency check), enforce code coverage thresholds (70%+ for services/API), run E2E tests on beta after deployment; (2) Beta deployment workflow - auto-deploy on merge to `beta` branch, run smoke tests, notify Slack on success/failure; (3) Production deployment workflow - require manual approval, run pre-deployment checks (migrations backward compatible?), deploy with canary strategy (1 pod → 50% → 100%), run extensive smoke tests, notify team on completion. Add deployment notifications (Slack integration). Document CI/CD pipeline in docs/guides/cicd.md with workflow diagrams.

**Agent Type Hint:** `SetupAgent`

**Inputs:**
- CI/CD requirements from Plan Section "Deployment Strategy"
- Existing CI/CD from I1.T12

**Input Files:**
- `.github/workflows/ci.yml`
- `.github/workflows/deploy-beta.yml`
- `.github/workflows/deploy-production.yml`

**Target Files:**
- `.github/workflows/ci.yml` (updated)
- `.github/workflows/deploy-beta.yml` (updated)
- `.github/workflows/deploy-production.yml` (updated)
- `.github/workflows/security-scan.yml` (new)
- `docs/guides/cicd.md`

**Deliverables:**
- Updated CI/CD workflows with security scanning and E2E tests
- Production deployment workflow with manual approval and canary strategy
- Slack notifications for deployments
- CI/CD documentation

**Acceptance Criteria:**
- CI workflow runs security scans (Snyk, OWASP), fails if critical vulnerabilities found
- CI workflow enforces code coverage >70% for service layer (JaCoCo report check)
- Beta deployment workflow deploys automatically on merge to `beta` branch
- Production deployment workflow requires manual approval (GitHub environment protection)
- Production deployment uses canary strategy (deploy 1 pod, wait 5 min, scale to 50%, wait 10 min, scale to 100%)
- Smoke tests run after each deployment stage (health check, GraphQL query, order placement)
- Slack notifications sent on deployment success/failure
- CI/CD guide explains workflow triggers, manual approval process, canary deployment

**Dependencies:** `I6.T5` (E2E tests to integrate into pipeline)

**Parallelizable:** No (requires E2E tests and deployment procedures)

---

<!-- anchor: task-i6-t10 -->
### Task 6.10: Create Launch Checklist and Go-Live Plan

**Task ID:** `I6.T10`

**Description:**
Prepare comprehensive launch checklist for MVP go-live. Create checklist covering: (1) Infrastructure - Kubernetes manifests deployed, Terraform resources created, DNS configured, SSL certificates valid; (2) Application - all features tested, security audit passed, load testing passed, no critical bugs; (3) Integrations - Stripe production mode configured, OAuth production credentials configured, email SMTP working; (4) Monitoring - Jaeger/Prometheus running, alerts configured, health checks working, uptime monitoring active; (5) Documentation - user help center published, admin guides complete, legal pages (Terms, Privacy) accessible; (6) Communication - status page configured (status.villagecompute.com), launch announcement prepared, support email (support@villagecalendar.com) configured. Write go-live plan with timeline, rollback criteria, post-launch monitoring plan. Conduct go-live dry run on beta environment.

**Agent Type Hint:** `DocumentationAgent`

**Inputs:**
- Launch requirements from Plan Section "Launch Checklist"
- All previous iteration deliverables

**Input Files:**
- All documentation from docs/

**Target Files:**
- `docs/launch-checklist.md`
- `docs/go-live-plan.md`
- `docs/post-launch-monitoring-plan.md`

**Deliverables:**
- Launch checklist (100+ items organized by category)
- Go-live plan with timeline and rollback criteria
- Post-launch monitoring plan (first 24 hours, first week)
- Go-live dry run completed

**Acceptance Criteria:**
- Launch checklist covers all areas: infrastructure, application, integrations, monitoring, documentation, communication
- Each checklist item has: description, verification method, owner, status (not started/in progress/done)
- Go-live plan includes: launch timeline (T-24h, T-1h, T=0, T+1h, T+24h), rollback criteria (when to abort launch), communication plan
- Post-launch monitoring plan specifies: metrics to watch (error rate, latency, orders), alert response times, incident escalation
- Dry run completed on beta: all checklist items verified, simulated go-live, no issues found
- Status page configured (e.g., using Statuspage.io or custom page)
- Support email configured (support@villagecalendar.com with ticket system or Google Workspace)

**Dependencies:** All previous tasks (requires complete system)

**Parallelizable:** No (final launch preparation)

---

<!-- anchor: iteration-6-summary -->
### Iteration 6 Summary

**Total Tasks:** 10

**Completion Criteria:**
- All tasks marked as completed
- Production Kubernetes manifests complete and tested
- Terraform configurations create all Cloudflare resources
- Security audit passed (no critical vulnerabilities)
- Load testing validated performance targets
- E2E test suite passing
- User documentation and help center complete
- Production monitoring and alerting configured
- Deployment and rollback procedures documented and tested
- Launch checklist complete, go-live plan ready

**Risk Mitigation:**
- If security audit uncovers critical issues, delay launch until fixed (prioritize security)
- If load testing reveals performance issues, scale infrastructure or optimize code before launch
- If E2E tests are flaky, investigate root cause (timing issues, test data) before trusting suite
- If go-live dry run fails, identify gaps in checklist and re-run

**Launch Readiness:**
After Iteration 6 completion, the Village Calendar MVP is ready for production launch. The system has been thoroughly tested (unit, integration, E2E, load, security), documented (user guides, admin runbooks, developer docs), and deployed to production infrastructure with monitoring and alerting. The team can execute the go-live plan with confidence, supported by comprehensive rollback procedures and post-launch monitoring.

**Post-Launch Iteration Preview (Future):**
Post-MVP iterations will focus on Phase 2 features (Plan Section 5.1.1): advanced collaboration, mobile apps, public gallery, referral program, shipping provider integration. Continuous iteration based on user feedback and analytics insights.
