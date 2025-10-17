# System Architecture Blueprint: Village Calendar

**Version:** 1.0
**Date:** 2025-10-16

---

<!-- anchor: operational-architecture -->
## 3. Proposed Architecture (Continued)

<!-- anchor: cross-cutting-concerns -->
### 3.8. Cross-Cutting Concerns

<!-- anchor: authentication-authorization -->
#### 3.8.1. Authentication & Authorization

**Authentication Strategy:**

The application implements **OAuth 2.0 / OpenID Connect (OIDC)** for user authentication, delegating identity management to established providers. This approach balances security, user convenience, and development velocity.

**Supported Providers:**

1. **Google OAuth 2.0**: Primary provider (largest user base, high trust)
2. **Facebook Login**: Secondary provider (social network integration potential)
3. **Apple Sign-In**: Required for iOS app (future), privacy-focused users

**Authentication Flow:**

1. User clicks "Sign in with [Provider]" button in Vue SPA
2. Frontend redirects to Quarkus OIDC endpoint (`/oauth/login?provider=google`)
3. Quarkus OIDC extension redirects to provider's authorization URL
4. User authenticates with provider, grants consent
5. Provider redirects back to Quarkus callback URL with authorization code
6. Quarkus exchanges code for ID token and access token (server-to-server)
7. Quarkus validates ID token signature (JWKS), extracts user claims (sub, email, name, picture)
8. Quarkus queries database for existing user by `oauth_provider + oauth_subject_id`
9. If new user: Create user record, link any guest session calendars
10. Quarkus generates JWT token (application-specific, includes user_id, role, expiration)
11. Quarkus returns JWT to frontend (in response body, not cookie, for SPA compatibility)
12. Frontend stores JWT in `localStorage`, includes in `Authorization: Bearer {token}` header for all API requests

**JWT Token Structure:**

```json
{
  "sub": "user_12345",
  "role": "user",
  "email": "user@example.com",
  "iat": 1697500000,
  "exp": 1697586400,
  "iss": "https://calendar.villagecompute.com"
}
```

**Token Lifetime:**
- **Access Token**: 24 hours (balance between security and UX)
- **Refresh Token**: Not implemented in MVP (user re-authenticates after 24h; acceptable for low-security SaaS)
- **Future**: Implement refresh tokens for longer sessions (7 days access, 30 days refresh)

**Authorization Model: Role-Based Access Control (RBAC)**

**Roles:**
- `user`: Standard authenticated user (can create/edit own calendars, place orders)
- `admin`: Administrative user (can create templates, view all orders, access analytics)
- `guest`: Implicit role for unauthenticated users (can create calendars in session, read-only templates)

**Access Control Rules:**

| Resource | Guest | User | Admin |
|----------|-------|------|-------|
| Create calendar (session-based) | ✅ | ✅ | ✅ |
| Save calendar to account | ❌ | ✅ (own) | ✅ (any) |
| Edit calendar | ❌ | ✅ (own) | ✅ (any) |
| Delete calendar | ❌ | ✅ (own) | ✅ (any) |
| View templates | ✅ | ✅ | ✅ |
| Create templates | ❌ | ❌ | ✅ |
| Place order | ❌ | ✅ (own calendars) | ✅ |
| View own orders | ❌ | ✅ | ✅ |
| View all orders | ❌ | ❌ | ✅ |
| Access analytics | ❌ | ❌ | ✅ |

**Enforcement Mechanisms:**

1. **GraphQL Field-Level Security**: SmallRye GraphQL `@RolesAllowed` annotation on queries/mutations
   ```java
   @Query
   @RolesAllowed("admin")
   public List<Order> allOrders() { ... }
   ```

2. **Service Layer Checks**: Verify resource ownership before operations
   ```java
   public void deleteCalendar(Long calendarId, User currentUser) {
       Calendar calendar = calendarRepo.findById(calendarId);
       if (!calendar.userId.equals(currentUser.id) && !currentUser.isAdmin()) {
           throw new UnauthorizedException("Cannot delete calendar owned by another user");
       }
       calendarRepo.delete(calendar);
   }
   ```

3. **Database Row-Level Security (Future)**: PostgreSQL policies to enforce ownership at database level

**Session Management:**

- **Authenticated Users**: Stateless JWT tokens (no server-side session storage required)
- **Guest Users**: Session data stored in `calendar_sessions` table, keyed by UUID in browser `localStorage`
- **Session Persistence**: Guest sessions expire after 30 days of inactivity
- **Conversion**: On login, guest session calendars transferred to new user account via `UPDATE calendars SET user_id = ? WHERE session_id = ?`

**Security Best Practices:**

- **Token Storage**: JWT in `localStorage` (XSS risk mitigated by CSP and Vue's automatic escaping; CSRF not a concern with token-based auth)
- **HTTPS Only**: All cookies (if used for refresh tokens in future) marked `Secure; HttpOnly; SameSite=Strict`
- **Token Validation**: Every API request validates JWT signature, expiration, and issuer
- **Rate Limiting**: Login endpoints rate-limited (10 attempts per IP per 15 minutes) to prevent brute force
- **Secrets Management**: OAuth client secrets stored in Kubernetes secrets, injected as environment variables

<!-- anchor: logging-monitoring -->
#### 3.8.2. Logging & Monitoring

**Logging Strategy:**

**Structured Logging (JSON Format):**

All application logs are emitted as structured JSON to enable efficient parsing, filtering, and aggregation by observability tools.

**Example Log Entry:**
```json
{
  "timestamp": "2025-10-16T14:32:15.123Z",
  "level": "INFO",
  "logger": "com.villagecompute.calendar.service.OrderService",
  "message": "Order placed successfully",
  "context": {
    "userId": 12345,
    "orderId": 67890,
    "total": 29.99,
    "paymentMethod": "card"
  },
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7"
}
```

**Log Levels:**
- **ERROR**: Application errors requiring immediate attention (payment failures, database connection loss, job crashes)
- **WARN**: Degraded operation or unexpected behavior (high retry count, deprecated API usage, slow queries >1s)
- **INFO**: Significant business events (order placed, user registered, PDF generated, email sent)
- **DEBUG**: Detailed troubleshooting information (GraphQL query details, external API request/response, job queue polling)
- **TRACE**: Very verbose (SQL queries, method entry/exit) - enabled only in development

**Log Aggregation:**

- **Development**: Console output (stdout) with colorized formatting
- **Production**: JSON logs to stdout, collected by Kubernetes (fluentd/fluent-bit) and forwarded to centralized logging (future: Loki, ELK Stack, or cloud provider logging)
- **Retention**: Logs retained 30 days in hot storage, 1 year in cold storage (compressed)

**Contextual Logging:**

- **Trace IDs**: OpenTelemetry trace ID and span ID included in every log entry, enabling correlation with distributed traces
- **User Context**: Authenticated user ID (if available) logged with every request
- **Request Context**: HTTP method, path, query parameters, response status code

**Distributed Tracing:**

**Tool: OpenTelemetry + Jaeger**

Distributed tracing captures the full lifecycle of requests as they flow through the system, from frontend GraphQL query to database queries to asynchronous job execution.

**Instrumentation:**

1. **Automatic Instrumentation**: Quarkus OpenTelemetry extension auto-instruments:
   - HTTP requests (JAX-RS, GraphQL endpoints)
   - Database queries (JDBC, Hibernate)
   - External HTTP calls (Stripe API, OAuth providers)
   - Message bus events (Vert.x EventBus)

2. **Custom Spans**: Manual instrumentation for business-critical operations:
   ```java
   @WithSpan("generateCalendarPdf")
   public void generatePdf(Long calendarId) {
       Span span = Span.current();
       span.setAttribute("calendar.id", calendarId);
       span.setAttribute("calendar.year", calendar.year);
       // ... PDF generation logic ...
   }
   ```

**Trace Visualization (Jaeger UI):**

- **Service Dependency Graph**: Visualize relationships between API, database, job workers, external services
- **Latency Analysis**: Identify slow database queries, external API calls, PDF rendering bottlenecks
- **Error Tracking**: Trace errors back to originating request, view full call stack across services

**Metrics Collection:**

**Tool: Micrometer + Prometheus**

**Application Metrics:**

1. **HTTP Requests**:
   - `http_server_requests_total` (counter): Total requests by endpoint, status code
   - `http_server_requests_duration_seconds` (histogram): Request latency distribution (p50, p95, p99)

2. **Database**:
   - `jdbc_connections_active` (gauge): Active database connections
   - `hibernate_query_duration_seconds` (histogram): Query execution time
   - `database_transactions_total` (counter): Committed vs rolled back transactions

3. **Job Queue**:
   - `delayed_jobs_queue_depth` (gauge): Number of pending jobs by type
   - `delayed_jobs_processing_duration_seconds` (histogram): Job execution time
   - `delayed_jobs_failed_total` (counter): Failed jobs by type and error reason

4. **Business Metrics**:
   - `calendars_created_total` (counter): Calendars created by user type (guest, authenticated)
   - `orders_placed_total` (counter): Orders by product type, payment status
   - `revenue_total` (counter): Revenue in cents (use counter for monotonic increase, derive rates in queries)
   - `pdf_downloads_total` (counter): Downloads by watermark status (free vs paid)

**Metrics Export:**

- **Endpoint**: `/q/metrics` (Prometheus scrape format)
- **Scrape Interval**: 15 seconds
- **Visualization**: Grafana dashboards (future integration)
- **Alerting**: Prometheus Alertmanager rules (future: alert on queue depth >500, error rate >1%, p95 latency >2s)

**Health Monitoring:**

**SmallRye Health Endpoints:**

1. **Liveness Probe** (`/q/health/live`):
   - Purpose: Indicates if application is running (should container be restarted?)
   - Checks: Application thread pool not deadlocked, core services initialized
   - Failure Action: Kubernetes kills pod, starts replacement

2. **Readiness Probe** (`/q/health/ready`):
   - Purpose: Indicates if application is ready to serve traffic (should traffic be routed?)
   - Checks: Database connection pool healthy, job queue accessible, external services reachable
   - Failure Action: Kubernetes removes pod from load balancer, stops routing traffic

**Custom Health Checks:**

```java
@Readiness
public class DatabaseHealthCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        try {
            dataSource.getConnection().close();
            return HealthCheckResponse.up("database");
        } catch (SQLException e) {
            return HealthCheckResponse.down("database");
        }
    }
}
```

<!-- anchor: security-considerations -->
#### 3.8.3. Security Considerations

**Threat Model:**

The application faces standard web application threats plus e-commerce-specific risks:

1. **Account Takeover**: Attacker gains access to user account, places fraudulent orders
2. **Payment Fraud**: Stolen credit cards used to purchase calendars (chargebacks)
3. **Data Breach**: Unauthorized access to user data (emails, addresses, calendar content)
4. **Denial of Service**: Overwhelming system with requests, preventing legitimate users from accessing service
5. **XSS/Injection Attacks**: Malicious code injected via calendar events, user-generated content

**Security Controls:**

**1. Transport Security (HTTPS Everywhere)**

- **TLS Termination**: Cloudflare edge (TLS 1.3, modern cipher suites)
- **Certificate Management**: Cloudflare Origin Certificates for backend communication
- **HSTS**: Strict-Transport-Security header (max-age=31536000; includeSubDomains; preload)
- **No HTTP**: All HTTP requests redirected to HTTPS at Cloudflare edge

**2. Input Validation & Sanitization**

- **GraphQL Schema Validation**: SmallRye GraphQL validates input types, required fields, enum values
- **Backend Validation**: Service layer validates business rules (e.g., calendar year 2020-2030, event text <500 chars)
- **HTML Sanitization**: User-generated content (calendar event text) sanitized before rendering in PDFs (prevent PDF injection attacks)
- **SQL Injection Prevention**: Hibernate ORM uses parameterized queries (no string concatenation)
- **Path Traversal Prevention**: File download endpoints validate calendar ID ownership, generate presigned URLs (no direct file paths)

**3. Cross-Site Scripting (XSS) Prevention**

- **Vue.js Auto-Escaping**: Template expressions automatically escaped (`{{ eventText }}` becomes HTML entities)
- **Content Security Policy (CSP)**: HTTP header restricts resource loading
  ```
  Content-Security-Policy:
    default-src 'self';
    script-src 'self' 'unsafe-inline' https://js.stripe.com;
    style-src 'self' 'unsafe-inline';
    img-src 'self' data: https://cloudflare.r2.com;
    connect-src 'self' https://api.stripe.com;
  ```
- **Emoji Validation**: Only allow valid Unicode emoji sequences (prevent rendering exploits)

**4. Cross-Site Request Forgery (CSRF) Protection**

- **Stateless Auth**: JWT tokens in `Authorization` header (not cookies) inherently CSRF-resistant
- **State-Changing Operations**: GraphQL mutations require `Content-Type: application/json` (cannot be triggered via simple form POST from malicious site)
- **Double-Submit Cookie (Future)**: If session cookies introduced, implement CSRF token validation

**5. Rate Limiting & DDoS Protection**

- **Cloudflare DDoS Protection**: Automatic mitigation of network-layer attacks (SYN floods, UDP amplification)
- **Application-Level Rate Limiting**: SmallRye Fault Tolerance `@RateLimit` annotation
  ```java
  @RateLimit(value = 100, window = 1, windowUnit = ChronoUnit.MINUTES)
  public User createAccount(CreateAccountInput input) { ... }
  ```
- **IP-Based Throttling**: Login endpoints: 10 attempts/15 minutes, PDF generation: 10/hour for free tier
- **Authenticated User Limits**: 100 calendars per account, 50 orders per year (prevent abuse)

**6. Secrets Management**

- **Environment Variables**: OAuth client secrets, Stripe API keys, database passwords injected via Kubernetes secrets
- **No Hardcoded Secrets**: Code repository contains only placeholder values (`${STRIPE_API_KEY}`)
- **Access Control**: Kubernetes RBAC limits secret access to application service account
- **Rotation**: Secrets rotated annually (automated via Terraform for cloud resources)

**7. Dependency Management**

- **Automated Scanning**: GitHub Dependabot alerts for vulnerable dependencies
- **Regular Updates**: Quarkus platform BOM updated quarterly (security patches applied immediately)
- **OWASP Dependency-Check**: CI/CD pipeline scans for known vulnerabilities (fails build on high-severity issues)

**8. PCI DSS Compliance (Payment Security)**

- **Stripe Checkout**: Card data never touches application servers (entered directly into Stripe-hosted form)
- **SAQ A Eligibility**: Merchant self-assessment questionnaire (lowest scope, <100 questions)
- **Webhook Signature Validation**: Stripe webhooks verified via `Stripe-Signature` header (prevents spoofing)
- **No Card Storage**: Application stores only `stripe_payment_intent_id` (token, not card numbers)

**9. Data Privacy & Protection**

- **Encryption at Rest**: PostgreSQL transparent data encryption (TDE) via LUKS (filesystem-level)
- **Encryption in Transit**: TLS for all external communication (API, database, R2 storage)
- **Access Logging**: Database audit logs track data access by user (future compliance requirement)
- **Data Minimization**: Collect only necessary PII (name, email, shipping address for orders)
- **Right to Deletion**: GraphQL mutation `deleteAccount` removes user data, anonymizes orders (keep for tax records)

**10. Security Headers**

```http
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), microphone=(), camera=()
```

<!-- anchor: scalability-performance -->
#### 3.8.4. Scalability & Performance

**Scalability Strategy:**

**Horizontal Scaling (Stateless Architecture):**

The application is designed for horizontal scaling via Kubernetes Horizontal Pod Autoscaler (HPA):

1. **API Pods**: Scale based on CPU utilization (target 70%) and request rate
   - Baseline: 2 pods (min) for high availability
   - Peak (Q4): 10 pods (max) for holiday traffic
   - Scaling Trigger: Avg CPU >70% for 2 minutes → add pod

2. **Job Worker Pods**: Scale based on job queue depth
   - Baseline: 1 pod
   - Scaling Trigger: Queue depth >50 jobs → add pod (max 5 pods)
   - Custom Metric: Prometheus query `delayed_jobs_queue_depth{status="pending"}`

**Stateless Design:**
- **No In-Memory Sessions**: JWT tokens (client-side), guest sessions in database
- **No Local File Storage**: Generated PDFs uploaded to R2 immediately, no temp files on pod filesystem
- **No Sticky Sessions**: Load balancer can route requests to any pod

**Database Scaling:**

- **Vertical Scaling (MVP)**: PostgreSQL on single node with sufficient resources (8 vCPU, 32 GB RAM)
- **Connection Pooling**: HikariCP pool (max 20 connections per pod, 200 total for 10 pods)
- **Indexes**: Ensure all high-frequency queries use indexes (query plan analysis via `EXPLAIN ANALYZE`)
- **Future Horizontal Scaling**:
  - **Read Replicas**: Route analytics queries to read replica (reduce load on primary)
  - **Partitioning**: Partition `page_views` by month, `delayed_jobs` by status

**Caching Strategy:**

1. **CDN Caching (Cloudflare)**:
   - **Static Assets**: Vue.js bundles, CSS, images (cache TTL: 1 year, cache-busting via hash filenames)
   - **Calendar Previews**: Preview images cached at edge (cache TTL: 1 hour, purged on calendar update)
   - **Cache Keys**: Include calendar version in URL (`/previews/{calendar_id}?v={updated_at}`)

2. **Application-Level Caching (Future - Redis)**:
   - **GraphQL Query Results**: Cache expensive queries (template list, user calendars) for 5 minutes
   - **Session Data**: Move guest sessions from PostgreSQL to Redis (faster reads, automatic expiration)
   - **Rate Limiting Counters**: Track request counts in Redis (lower latency than database queries)

3. **Database Query Caching**:
   - **Prepared Statement Cache**: Hibernate caches query plans (reduces parse overhead)
   - **Second-Level Cache (Future)**: Hibernate L2 cache for rarely-changing entities (templates)

**Performance Optimizations:**

1. **GraphQL DataLoader Pattern**: Batch and cache database queries to prevent N+1 problem
   ```java
   // Without DataLoader: N+1 queries
   List<Calendar> calendars = getCalendars(userId); // 1 query
   for (Calendar cal : calendars) {
       User user = getUser(cal.userId); // N queries (one per calendar)
   }

   // With DataLoader: 2 queries
   List<Calendar> calendars = getCalendars(userId); // 1 query
   List<Long> userIds = calendars.stream().map(c -> c.userId).distinct().collect();
   Map<Long, User> users = getUsersBatch(userIds); // 1 batched query
   ```

2. **Lazy Loading**: Calendar events loaded only when explicitly requested in GraphQL query
   ```graphql
   query {
     calendar(id: "123") {
       title
       year
       # events not fetched unless requested
     }
   }
   ```

3. **Pagination**: Large lists (calendars, orders, events) use cursor-based pagination
   ```graphql
   query {
     calendars(userId: "456", first: 20, after: "cursor123") {
       edges { node { id title } }
       pageInfo { hasNextPage endCursor }
     }
   }
   ```

4. **Database Indexes**: Index all foreign keys, frequently filtered columns
   ```sql
   CREATE INDEX idx_calendars_user_id ON calendars(user_id);
   CREATE INDEX idx_calendars_year ON calendars(year);
   CREATE INDEX idx_orders_user_status ON orders(user_id, status);
   CREATE INDEX idx_delayed_jobs_run_at_priority ON delayed_jobs(run_at, priority) WHERE status='PENDING';
   ```

5. **Async PDF Generation**: Offload to job queue (prevents blocking HTTP threads)

**Load Testing:**

- **Tool**: JMeter or Gatling
- **Scenarios**:
  - Ramp-up: 0 → 1000 concurrent users over 10 minutes
  - Sustained load: 500 concurrent users for 1 hour
  - Spike: Burst to 2000 users for 5 minutes
- **Success Criteria**:
  - P95 latency <500ms for GraphQL queries
  - P99 latency <2s for mutations
  - Zero errors under sustained load
  - Graceful degradation under spike (queue requests, no crashes)

<!-- anchor: reliability-availability -->
#### 3.8.5. Reliability & Availability

**High Availability Design:**

**Target SLA: 99.5% Uptime (43.8 hours downtime/year, ~3.5 hours/month)**

**Redundancy:**

1. **Multi-Pod Deployment**: Minimum 2 API pods, 1 job worker pod (ensures availability during single pod failure)
2. **Database Replication (Future)**: PostgreSQL streaming replication (primary + standby, automatic failover via Patroni)
3. **Load Balancing**: Kubernetes service distributes traffic across healthy pods (health probe-based routing)

**Fault Tolerance:**

1. **Circuit Breakers**: Prevent cascading failures when external services (Stripe, OAuth) are unavailable
   ```java
   @CircuitBreaker(
       requestVolumeThreshold = 10,
       failureRatio = 0.5,
       delay = 5000,
       successThreshold = 2
   )
   public CheckoutSession createStripeCheckout(Order order) {
       // Stripe API call
   }
   ```
   - **Open State**: After 5/10 requests fail, circuit opens (fast-fail for 5 seconds)
   - **Half-Open State**: After delay, allow 2 test requests
   - **Closed State**: If test requests succeed, resume normal operation

2. **Retry Logic**: Transient failures (network timeouts, 5xx errors) retried with exponential backoff
   ```java
   @Retry(
       maxRetries = 3,
       delay = 1000,
       delayUnit = ChronoUnit.MILLIS,
       jitter = 500,
       abortOn = {UnauthorizedException.class}
   )
   public OAuthUserInfo fetchUserInfo(String accessToken) {
       // OAuth provider API call
   }
   ```

3. **Graceful Degradation**:
   - **OAuth Provider Down**: Show error message "Google login temporarily unavailable, try again in a few minutes"
   - **Stripe API Down**: Queue order as PENDING, process payment later via admin panel manual retry
   - **Email Service Down**: Queue emails in DelayedJob table, retry when service recovers
   - **PDF Generation Failure**: Notify user via email when PDF ready (avoid blocking UX)

**Data Durability:**

1. **Database Backups**:
   - **Frequency**: Daily full backups (2 AM UTC), continuous WAL archiving
   - **Retention**: 30 days online, 1 year archived (compressed to S3/R2)
   - **Verification**: Weekly restore tests to separate environment
   - **RPO (Recovery Point Objective)**: <1 hour (WAL archiving every 15 minutes)

2. **Object Storage (R2)**:
   - **Replication**: Cloudflare automatic geo-replication
   - **Versioning**: Enabled (accidental deletes recoverable for 30 days)
   - **Backup**: Secondary bucket in different region (sync daily)

**Disaster Recovery:**

**RTO (Recovery Time Objective): 4 hours**

**Runbook Steps:**

1. **Declare Incident**: Page on-call engineer via PagerDuty (critical alerts)
2. **Assess Impact**: Check monitoring dashboards (Grafana, Jaeger, Kubernetes)
3. **Database Failure**:
   - Promote standby to primary (Patroni automatic failover, or manual via `patronictl`)
   - Update database connection string in Kubernetes ConfigMap
   - Restart API pods to pick up new connection string
4. **Kubernetes Cluster Failure**:
   - Restore cluster state from Terraform/Ansible configurations
   - Restore database from latest backup + WAL replay
   - Redeploy application via CI/CD pipeline
5. **Data Corruption**:
   - Identify corruption scope via database logs
   - Restore from point-in-time backup (before corruption)
   - Replay WAL logs up to corruption event, skip corrupt transaction
6. **Communication**:
   - Post status page update (status.villagecompute.com)
   - Email affected users if data loss occurred
   - Post-incident review (blameless retrospective)

**Monitoring & Alerting:**

**Alert Rules (Prometheus Alertmanager):**

| Alert | Condition | Severity | Action |
|-------|-----------|----------|--------|
| High Error Rate | Error rate >1% for 5 min | Critical | Page on-call |
| API Latency Spike | P95 latency >2s for 10 min | High | Page on-call |
| Database Down | Connection failures >5 in 1 min | Critical | Page on-call, failover |
| Job Queue Backlog | Pending jobs >500 for 15 min | Medium | Notify #ops-alerts Slack |
| Disk Space Low | Disk usage >85% | High | Auto-scale storage, notify |
| Pod Crash Loop | Pod restart >5 in 10 min | High | Page on-call |
| Payment Failures | Stripe webhook 5xx >10% | Critical | Page on-call, escalate to Stripe support |

**On-Call Rotation:**

- **Primary**: 24/7 on-call engineer (1 week rotation)
- **Secondary**: Backup escalation (if primary doesn't respond in 15 minutes)
- **Escalation**: CTO for prolonged outages (>2 hours) or data breaches

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

**Kubernetes Resources:**

```yaml
# API Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: calendar-api
  namespace: calendar-prod
spec:
  replicas: 2
  selector:
    matchLabels:
      app: calendar-api
  template:
    metadata:
      labels:
        app: calendar-api
    spec:
      containers:
      - name: calendar-api
        image: villagecompute/calendar-api:latest
        ports:
        - containerPort: 8080
        env:
        - name: QUARKUS_DATASOURCE_JDBC_URL
          value: jdbc:postgresql://postgres-vm:5432/village_calendar
        - name: QUARKUS_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: postgres-credentials
              key: username
        - name: QUARKUS_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-credentials
              key: password
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

**Autoscaling:**

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: calendar-api-hpa
  namespace: calendar-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: calendar-api
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: http_server_requests_total
      target:
        type: AverageValue
        averageValue: "1000"
```

**Resource Allocation (Per Pod):**

| Container | CPU Request | CPU Limit | Memory Request | Memory Limit |
|-----------|-------------|-----------|----------------|--------------|
| API Pod | 500m | 2000m | 512Mi | 2Gi |
| Worker Pod | 1000m | 2000m | 1Gi | 4Gi (PDF rendering) |
| Jaeger | 200m | 500m | 256Mi | 1Gi |
| Prometheus | 500m | 1000m | 1Gi | 4Gi |

**Network Policies:**

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: calendar-api-netpol
  namespace: calendar-prod
spec:
  podSelector:
    matchLabels:
      app: calendar-api
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - namespaceSelector: {}
      podSelector:
        matchLabels:
          app: jaeger
    ports:
    - protocol: TCP
      port: 4317
  - to: # Allow external API calls (Stripe, OAuth, etc.)
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 443
```
