# System Architecture Blueprint: Village Calendar

**Version:** 1.0
**Date:** 2025-10-16

---

<!-- anchor: design-rationale-tradeoffs -->
## 4. Design Rationale & Trade-offs

<!-- anchor: key-decisions-summary -->
### 4.1. Key Decisions Summary

The following table summarizes the most critical architectural decisions, their rationale, and trade-offs accepted:

| Decision | Rationale | Trade-offs Accepted |
|----------|-----------|---------------------|
| **Modular Monolith over Microservices** | Simpler operations, faster development, team expertise. Workflows require strong consistency (e-commerce transactions). | Limited independent scaling (entire app scales together). Potential refactoring cost if modules extracted later. |
| **GraphQL Primary API** | Complex nested data (Calendar → Events → User). Single round-trip fetches. Frontend flexibility. Strong typing. | Learning curve for team. More complex caching than REST. Potential over-fetching if queries poorly designed. |
| **PostgreSQL Relational DB** | Calendar data is inherently relational (users, calendars, events, orders). ACID transactions critical for payments. JSONB provides flexibility. | Vertical scaling limits (single primary node). Read replicas add complexity. Less suited for time-series analytics (future: add ClickHouse). |
| **Quarkus (Java) Backend** | Mandated by existing stack. Fast startup, low memory (container-friendly). Rich ecosystem (Hibernate, GraphQL, OIDC). | Java verbosity vs. lightweight frameworks (Node.js, Go). Native image compilation complexity (reflection, proxies). |
| **Vue.js (Not React)** | Mandated by existing stack. Team expertise. Composition API performance. PrimeVue component library. | Smaller community vs. React. Fewer third-party libraries. Less common in job market (hiring risk). |
| **OAuth-Only Authentication (No Email/Password)** | Simpler to implement (no password storage, reset flows). Trusted providers (Google, Facebook, Apple). | Users without OAuth accounts excluded. Dependency on provider uptime. Privacy-conscious users may resist. |
| **Stripe for Payments** | PCI compliance delegation. Industry standard. Comprehensive API (checkout, webhooks, refunds). | 2.9% + $0.30 fee per transaction (vs. 1-2% for direct merchant account). Vendor lock-in (switching cost high). |
| **Cloudflare for Edge Services** | DDoS protection critical for public site. Global CDN. R2 cost-effective vs. S3. Tunnel simplifies ingress (no firewall rules). | Vendor lock-in (Cloudflare-specific features). R2 less mature than S3 (fewer integrations). DNS migration complexity if switching. |
| **K3s Self-Hosted (Not Cloud)** | Cost optimization (owned hardware). Team controls infrastructure. Learning opportunity. | Operational burden (patching, backups, scaling). No managed service SLAs. Single point of failure (on-prem datacenter). |
| **Asynchronous Job Queue (DelayedJob)** | PDF generation blocks requests (10-30s). Email sending delays HTTP responses. Retry logic for transient failures. | Added complexity (job monitoring, failure handling). Eventual consistency (user sees "processing" state). Database polling overhead (vs. dedicated queue like RabbitMQ). |
| **No Caching Layer Initially (Redis)** | Simplifies MVP architecture. Database performance sufficient for initial scale. | Higher database load (repeated queries). Slower rate limiting (database-backed vs. Redis). Harder to implement distributed sessions later. |
| **Single Database (No Read Replicas)** | Simpler operations. Write-heavy workload (calendar edits, orders). Analytics queries infrequent. | Analytics queries may slow down user-facing queries. Single point of failure (mitigated by backups, but RTO >1 hour). |
| **Server-Side PDF Rendering (Not Client-Side)** | Consistent rendering (avoid browser differences). High-quality output (Batik SVG → PDF). Complex astronomical calculations on server. | Server resource intensive (CPU, memory). Job queue required. User waits for async processing. |
| **99.5% SLA (Not 99.9%)** | Cost/complexity of 99.9% (multi-region, auto-failover) unjustified for MVP. Users tolerate occasional downtime (not mission-critical app). | 3.5 hours/month downtime acceptable. May lose sales during outages (holiday season risk). |

<!-- anchor: alternatives-considered -->
### 4.2. Alternatives Considered

<!-- anchor: alternative-microservices -->
#### 4.2.1. Microservices Architecture

**Proposed Structure:**
- **User Service**: Authentication, account management
- **Calendar Service**: Calendar CRUD, templates, events
- **PDF Service**: Async rendering, watermarking
- **Order Service**: E-commerce, Stripe integration
- **Notification Service**: Emails, webhooks

**Why Rejected:**

1. **Team Size**: Small team (2-3 developers) would spend disproportionate time on inter-service communication, service discovery, distributed debugging vs. feature development.

2. **Operational Overhead**: Managing multiple deployment pipelines, version compatibility matrices, and distributed tracing across services requires dedicated DevOps resources (not available).

3. **Tight Coupling**: Calendar creation workflow touches User, Calendar, and PDF services in rapid succession. Network latency between services would degrade UX (200ms API response → 600ms with 3 service hops).

4. **Transaction Complexity**: Order placement requires atomic updates to Order, Payment, and Calendar entities. Distributed transactions (2PC) or sagas add significant complexity without clear benefit.

5. **Premature Optimization**: No evidence that different services require independent scaling. PDF generation is the only CPU-intensive operation, and it's already isolated via job workers.

**When to Reconsider:**
- Team grows to 10+ developers (multiple teams owning services)
- PDF generation becomes scaling bottleneck (>10k PDFs/day)
- Third-party integrations require independent deployment cycles (white-label API)

<!-- anchor: alternative-nosql -->
#### 4.2.2. NoSQL Database (MongoDB)

**Proposed Schema:**
- Collections: `users`, `calendars`, `orders`, `jobs`
- Embedded documents: Events embedded in Calendar documents
- Flexible schema: Calendar metadata as nested JSON (no predefined schema)

**Why Rejected:**

1. **Relational Requirements**: E-commerce inherently relational (Users → Orders → OrderItems → Calendars). JOIN operations (order with user info and calendar preview) would require application-level joins or aggregation pipelines (complex).

2. **ACID Transactions**: Order placement requires multi-document transactions (Order + Payment + Job). MongoDB supports transactions, but PostgreSQL's implementation is more mature and performant.

3. **Analytics Queries**: Business metrics (revenue by month, popular templates, conversion funnels) require complex aggregations across entities. SQL is more expressive and better-optimized for analytical queries than MongoDB aggregation framework.

4. **Team Expertise**: Team familiar with PostgreSQL. Learning MongoDB query language and indexing strategies would slow development.

5. **JSONB Flexibility**: PostgreSQL JSONB provides schema flexibility where needed (calendar config, analytics dimensions) while maintaining relational integrity for core entities.

**When to Reconsider:**
- Calendar metadata becomes extremely heterogeneous (hundreds of custom fields per calendar)
- Horizontal sharding required for write scalability (>100k writes/second)
- Document versioning critical (MongoDB change streams, document history)

<!-- anchor: alternative-serverless -->
#### 4.2.3. Serverless Architecture (AWS Lambda + API Gateway)

**Proposed Design:**
- Lambda functions: GraphQL resolver, PDF generation, order webhook
- API Gateway: HTTP routing, authentication
- DynamoDB: Scalable NoSQL storage
- S3: PDF storage
- SQS: Job queue

**Why Rejected:**

1. **Cost Unpredictability**: Seasonal traffic spikes (Q4) could cause Lambda invocations to spike 10x. Cost prediction difficult vs. fixed k3s infrastructure costs.

2. **Cold Starts**: Lambda cold starts (500ms-2s for Java) would degrade UX for infrequent users. Provisioned concurrency adds cost and complexity.

3. **Vendor Lock-In**: AWS-specific code (DynamoDB SDK, Lambda handlers, API Gateway config) makes migration costly. Current hybrid architecture (k3s + Cloudflare) provides multi-cloud flexibility.

4. **Development Workflow**: Local testing requires SAM/LocalStack. Team prefers standard Quarkus dev mode (`./mvnw quarkus:dev`) for rapid iteration.

5. **Existing Infrastructure**: K3s cluster already operational. Migrating to serverless requires rewriting deployment pipelines, monitoring, and infrastructure-as-code.

6. **Long-Running Jobs**: PDF generation (10-30s) exceeds typical Lambda timeout comfort zone. Would require Step Functions or fargate tasks (adds complexity).

**When to Reconsider:**
- Traffic becomes extremely spiky (1000x variance between off-peak and peak)
- Global expansion requires multi-region active-active (Lambda's global edge advantage)
- Team shifts to event-driven architecture (Lambda + EventBridge)

<!-- anchor: alternative-rest-api -->
#### 4.2.4. REST-Only API (No GraphQL)

**Proposed Endpoints:**
- `GET /api/calendars/{id}` - Fetch calendar
- `GET /api/calendars/{id}/events` - Fetch events
- `POST /api/calendars` - Create calendar
- `POST /api/orders` - Place order

**Why Rejected:**

1. **Over-Fetching**: Calendar editor needs User + Calendars + Events + Templates. REST requires multiple round-trips or custom "include" parameters (e.g., `/api/calendars?include=events,user,template`), which becomes unwieldy.

2. **Under-Fetching**: Calendar list view needs only `id`, `title`, `preview_url`, but REST endpoint returns full calendar object (wasted bandwidth). Alternative is proliferation of endpoints (`/api/calendars/summary`).

3. **Frontend Flexibility**: GraphQL allows frontend to evolve queries without backend changes. Adding "last modified" timestamp to UI is just a query change, not a backend deployment.

4. **Versioning Burden**: REST API versioning (/v1, /v2) required for breaking changes. GraphQL deprecation mechanism smoother (mark fields `@deprecated`, remove after grace period).

5. **Type Safety**: GraphQL schema generates TypeScript types for frontend (compile-time validation). REST requires manual type definitions or OpenAPI codegen (less integrated with Vue).

**Trade-off Accepted:**
- GraphQL adds complexity (query parsing, N+1 problem mitigation). Team accepted this trade-off for frontend flexibility.

**When REST Still Used:**
- Webhooks (Stripe, future integrations) - predictable endpoints easier than GraphQL mutations for third parties
- File downloads - direct URLs simpler than GraphQL file upload/download patterns
- Health checks - Kubernetes probes expect HTTP GET, not GraphQL queries

<!-- anchor: known-risks-mitigation -->
### 4.3. Known Risks & Mitigation

<!-- anchor: risk-database-scaling -->
#### 4.3.1. Risk: Database Becomes Bottleneck

**Probability**: Medium (likely if growth exceeds 50k users in year 1)

**Impact**: High (degraded performance, slow queries, failed transactions)

**Symptoms**:
- P95 query latency exceeds 500ms
- Connection pool exhaustion (all connections in use)
- Increased database CPU/memory utilization (>80% sustained)

**Mitigation Strategies**:

1. **Short-Term (0-3 months)**:
   - Optimize slow queries via `EXPLAIN ANALYZE` (add missing indexes, rewrite inefficient joins)
   - Increase database VM resources (vertical scaling: 8 vCPU → 16 vCPU, 32GB RAM → 64GB RAM)
   - Tune PostgreSQL configuration (`shared_buffers`, `work_mem`, `max_connections`)

2. **Medium-Term (3-6 months)**:
   - Implement read replicas for analytics queries (offload SELECT load from primary)
   - Introduce Redis caching layer (cache hot data: templates, user sessions)
   - Partition large tables (`page_views` by month, `delayed_jobs` by status)

3. **Long-Term (6-12 months)**:
   - Evaluate managed PostgreSQL (AWS RDS, Google Cloud SQL) for automatic scaling and failover
   - Consider database sharding if single-node limits reached (shard by user_id for multi-tenant isolation)

**Monitoring**:
- Alert: Database CPU >80% for 10 minutes
- Alert: Query latency P95 >500ms for 5 minutes
- Dashboard: Connection pool utilization, active queries, table sizes

<!-- anchor: risk-pdf-generation-overload -->
#### 4.3.2. Risk: PDF Generation Job Queue Overload

**Probability**: High (expected during Q4 holiday season, Black Friday promotions)

**Impact**: Medium (delayed PDF generation, user frustration, potential lost sales)

**Symptoms**:
- Job queue depth exceeds 500 pending jobs
- Average job processing time increases (worker pod CPU saturation)
- User complaints about "PDF still processing after 30 minutes"

**Mitigation Strategies**:

1. **Horizontal Scaling**:
   - Kubernetes HPA scales worker pods based on queue depth (target: 50 jobs per worker)
   - Maximum 10 worker pods (limit to prevent database connection exhaustion)

2. **Priority Queuing**:
   - Paid PDF downloads: priority=10 (processed first)
   - Free tier watermarked PDFs: priority=5 (processed after paid)
   - Admin template previews: priority=1 (lowest)

3. **Rate Limiting**:
   - Free tier users: 3 PDF generations per day
   - Paid users: 20 PDF generations per day (prevent abuse)

4. **Caching**:
   - Cache generated PDFs for 24 hours (return cached PDF if calendar unchanged)
   - Cache key: `{calendar_id}_{updated_at_timestamp}_{watermark_flag}`

5. **Offloading (Future)**:
   - Evaluate dedicated PDF rendering service (e.g., AWS Lambda, Fargate) for burst capacity
   - Pre-generate PDFs for popular templates (triggered nightly, stored in R2)

**Monitoring**:
- Alert: Job queue depth >500 for 15 minutes (scale workers)
- Alert: Job failure rate >5% for 10 minutes (investigate rendering errors)
- Dashboard: Queue depth trend, average processing time, worker pod CPU

<!-- anchor: risk-third-party-downtime -->
#### 4.3.3. Risk: Third-Party Service Downtime (Stripe, OAuth Providers)

**Probability**: Low (major providers have 99.9%+ SLAs), but **Impact** is High

**Services at Risk**:
- **Stripe**: Payment processing (orders blocked, revenue loss)
- **Google OAuth**: User login (new users cannot sign up, existing users with expired tokens cannot log in)
- **Cloudflare**: CDN, DNS, tunnel (entire site down if DNS fails)

**Mitigation Strategies**:

1. **Stripe Downtime**:
   - **Circuit Breaker**: After 5 consecutive Stripe API failures, open circuit (fast-fail for 5 minutes)
   - **Queued Orders**: Allow users to "pre-order" (save order in PENDING_PAYMENT status, process when Stripe recovers)
   - **Admin Override**: Admin panel allows manual payment entry (e.g., customer called, paid via phone)
   - **Status Page**: Display banner "Payment processing temporarily unavailable, orders will be processed when service recovers"

2. **OAuth Provider Downtime**:
   - **Multi-Provider Fallback**: Offer Google, Facebook, Apple (unlikely all three fail simultaneously)
   - **Status Page**: Display "Google login unavailable, try Facebook or Apple"
   - **Session Extension**: Extend JWT token expiration to 7 days during provider outages (reduce re-auth pressure)

3. **Cloudflare Downtime** (Catastrophic Scenario):
   - **DNS Failover**: Secondary DNS provider (AWS Route 53) with health checks (failover to direct k3s IP if Cloudflare unreachable)
   - **Direct Tunnel Access**: Emergency WireGuard VPN access for team (bypass Cloudflare tunnel)
   - **Communication**: Post updates on Twitter, email newsletter (cannot rely on website during DNS outage)

**Monitoring**:
- External uptime monitoring (Pingdom, UptimeRobot) - checks from multiple global locations
- Alert: Stripe API error rate >10% for 5 minutes
- Alert: OAuth provider 5xx errors >5 in 1 minute
- Status page integration: Fetch Stripe/Cloudflare status (https://status.stripe.com API)

<!-- anchor: risk-seasonal-demand-surge -->
#### 4.3.4. Risk: Seasonal Demand Surge Overwhelms Infrastructure

**Probability**: High (calendar demand peaks in Q4: November, December, early January)

**Impact**: High (site slowdown or downtime during critical sales period)

**Scenarios**:
- 5x traffic spike: 100 concurrent users → 500 concurrent users
- 10x order volume: 10 orders/day → 100 orders/day
- PDF generation queue: 50 pending jobs → 500 pending jobs

**Mitigation Strategies**:

1. **Load Testing (Pre-Season)**:
   - Run load tests in October (before Q4 rush) to identify bottlenecks
   - Simulate 10x traffic (1000 concurrent users, 500 orders/hour)
   - Fix identified issues (slow queries, memory leaks, connection pool limits)

2. **Auto-Scaling Configuration**:
   - API pods: 2 min → 10 max (HPA target: 70% CPU)
   - Worker pods: 1 min → 5 max (HPA target: 50 jobs/worker)
   - Database VM: Pre-scale to 16 vCPU, 64GB RAM for Q4 (September upgrade)

3. **Queueing Strategy**:
   - Display estimated wait time for PDF generation ("Your PDF will be ready in ~5 minutes")
   - Email notification when PDF ready (avoid user polling, refreshing page)

4. **Feature Flags**:
   - Disable non-essential features during extreme load (analytics collection, email marketing campaigns)
   - Enable "read-only" mode if database under extreme stress (allow browsing, disable orders)

5. **Incident Response Plan**:
   - On-call engineer designated for Q4 (24/7 coverage November 15 - January 5)
   - Runbook for scaling database, adding worker pods, clearing job queue
   - Pre-approved budget for cloud bursting (temporary AWS Fargate workers if k3s saturated)

**Monitoring**:
- Alert: Overall request rate >1000 req/min (baseline: 100 req/min)
- Alert: API pod CPU >80% sustained for 5 minutes
- Dashboard: Real-time traffic (requests/min), order rate, queue depth

<!-- anchor: risk-security-breach -->
#### 4.3.5. Risk: Security Breach or Data Leak

**Probability**: Low (with proper controls), but **Impact** is Catastrophic (legal, reputational, financial)

**Attack Vectors**:
- SQL injection via GraphQL input
- XSS via calendar event text rendering in PDFs
- OAuth token theft (man-in-the-middle)
- Database access via compromised Kubernetes secrets
- Payment data exposure (despite Stripe delegation, `stripe_payment_intent_id` could leak)

**Mitigation Strategies**:

1. **Input Validation** (Defense in Depth):
   - GraphQL schema validation (type checking, max string lengths)
   - Service layer validation (business rules, allowed character sets)
   - Database constraints (NOT NULL, foreign keys, CHECK constraints)

2. **Output Encoding**:
   - Vue.js auto-escaping (prevents XSS in web UI)
   - PDF rendering sanitization (strip HTML tags from event text before Batik rendering)

3. **Secrets Rotation**:
   - Rotate OAuth client secrets annually
   - Rotate database passwords quarterly (automated via Ansible)
   - Rotate Stripe API keys on suspected compromise (immediate, not scheduled)

4. **Audit Logging**:
   - Log all admin actions (order status changes, template creation, user impersonation)
   - Log payment events (order placed, payment succeeded, refund issued)
   - Retain audit logs 7 years (compliance requirement)

5. **Incident Response Plan**:
   - **Detection**: Automated alerts (unusual data access patterns, failed login spikes, SQL errors)
   - **Containment**: Isolate compromised pods, revoke API keys, force user password resets (OAuth token revocation)
   - **Communication**: Notify affected users within 72 hours (GDPR requirement), post incident report
   - **Recovery**: Restore from backups, patch vulnerabilities, security audit

**Monitoring**:
- Alert: Failed login attempts >100 in 5 minutes (credential stuffing attack)
- Alert: Database access from unknown IP (potential unauthorized access)
- Alert: Unusual data export volume (potential exfiltration)
- Quarterly penetration testing (external security firm)

---

<!-- anchor: future-considerations -->
## 5. Future Considerations

<!-- anchor: potential-evolution -->
### 5.1. Potential Evolution

The architecture is designed to evolve incrementally as the product matures and user base grows. Key evolution paths:

<!-- anchor: evolution-phase-2 -->
#### 5.1.1. Phase 2 Enhancements (6-12 Months Post-Launch)

**1. Advanced Collaboration Features**
- **Shared Editing**: Multiple users edit same calendar (WebSocket for real-time sync)
- **Comments**: Annotate specific dates, discuss event choices
- **Version History**: Rollback to previous calendar states
- **Architecture Changes**:
  - Introduce event sourcing for calendar edits (audit trail, undo/redo)
  - WebSocket server (Quarkus WebSocket or separate Node.js service)
  - Conflict resolution strategy (last-write-wins vs. operational transformation)

**2. Mobile Applications (iOS/Android)**
- **React Native** or **Flutter** for cross-platform development
- GraphQL API reuse (no backend changes required)
- Mobile-specific features: Push notifications (order shipped), offline editing
- **Architecture Changes**:
  - Add GraphQL subscriptions for real-time updates (calendar shared, order status changed)
  - Mobile-optimized image formats (WebP for smaller preview images)
  - App-specific authentication (refresh tokens for longer sessions)

**3. Public Calendar Gallery**
- Users opt-in to showcase calendars publicly
- SEO landing pages (e.g., `/gallery/2025-family-calendars`)
- Social sharing (Pinterest, Instagram integrations)
- **Architecture Changes**:
  - Add `calendars.is_public` flag, `gallery_category` field
  - Server-side rendering (SSR) for gallery pages (Next.js or Quarkus Qute templates)
  - Separate read-only database replica for gallery queries (reduce primary load)

**4. Referral Program**
- "Give $10, Get $10" credit system
- Unique referral links per user
- Credit applied to next order
- **Architecture Changes**:
  - New tables: `referrals`, `credits`
  - Stripe integration: Apply credits as discount codes
  - Analytics: Track referral conversions, top referrers

**5. Shipping Provider Integration**
- ShipStation or EasyPost API for label generation
- Real-time shipping rate calculation
- Automatic tracking number email notifications
- **Architecture Changes**:
  - New service: `ShippingService` (rate quotes, label printing, tracking)
  - Webhook receiver for tracking updates (delivered, exception)
  - Admin UI: Batch label printing, packing slip generation

<!-- anchor: evolution-phase-3 -->
#### 5.1.2. Phase 3 Scalability (12-24 Months)

**1. Read Replicas for Database**
- PostgreSQL streaming replication (primary + 2 read replicas)
- Route analytics queries to replicas (reduce primary load)
- **Trade-offs**:
  - Eventual consistency (replica lag 100ms-1s)
  - Application logic determines read/write routing

**2. Redis Caching Layer**
- Cache templates, user sessions, rate limiting counters
- Reduce database query load by 50%+
- **Use Cases**:
  - Session storage: Move `calendar_sessions` from PostgreSQL to Redis (faster, auto-expiration)
  - GraphQL query caching: Cache template list, popular calendars
  - Rate limiting: Track request counts in Redis (sub-millisecond lookups vs. database queries)

**3. CDN for Dynamic Content**
- Cloudflare Workers for edge-side rendering
- Cache calendar previews at edge (reduce latency for global users)
- **Example**: Request for `/api/calendar/123/preview` served from Cloudflare edge (cached), not origin server

**4. Separate PDF Rendering Service**
- Extract PDF generation from monolith → dedicated microservice
- **Technology Options**:
  - AWS Lambda (serverless, auto-scaling, pay-per-use)
  - Dedicated k3s deployment (same cluster, separate pods)
- **Rationale**: PDF rendering is CPU-intensive, scales independently from API traffic

<!-- anchor: evolution-phase-4 -->
#### 5.1.3. Phase 4 Advanced Features (24+ Months)

**1. Subscription Model**
- Monthly recurring calendar (updated with events user adds throughout year)
- Stripe Subscriptions integration
- **Architecture Changes**:
  - New tables: `subscriptions`, `subscription_plans`
  - Scheduled jobs: Generate monthly calendar PDFs, ship to customer
  - Billing portal: Stripe Customer Portal integration (manage subscription, update payment method)

**2. AI-Powered Features**
- Event suggestions based on user patterns (e.g., "Add birthday reminders for family members?")
- Auto-generate calendar themes via image generation (DALL-E, Stable Diffusion)
- **Architecture Changes**:
  - Integration with OpenAI API or self-hosted ML models
  - New table: `ai_suggestions` (track accepted/rejected suggestions for model training)

**3. White-Label Solution**
- Businesses license calendar platform for their own branding
- Multi-tenant architecture (isolated data per tenant)
- **Architecture Changes**:
  - Add `tenants` table (tenant_id foreign key on all entities)
  - Row-level security in PostgreSQL (enforce tenant isolation at database level)
  - Subdomain routing (tenant-slug.villagecalendar.com)

**4. International Expansion**
- Localization (Spanish, French, German)
- Multi-currency support (EUR, GBP, CAD)
- International shipping (DHL, FedEx integrations)
- **Architecture Changes**:
  - Vue I18n message catalogs (already structured for this)
  - Stripe multi-currency payments (present prices in user's currency)
  - Cloudflare geo-routing (serve from nearest region)

<!-- anchor: areas-for-deeper-dive -->
### 5.2. Areas for Deeper Dive

The following areas require detailed design during implementation phases:

<!-- anchor: deeper-dive-graphql-schema -->
#### 5.2.1. GraphQL Schema Design

**Outstanding Questions**:
- Exact field structure for `CalendarConfig` (nested vs. flat fields?)
- Pagination strategy (cursor-based vs. offset-based?) for large lists
- Error handling conventions (GraphQL errors vs. union types for failures?)
- File upload mechanism (multipart/form-data vs. base64 in GraphQL mutation?)

**Action Items**:
- Create comprehensive GraphQL schema document with examples
- Define naming conventions (camelCase vs. snake_case, plural vs. singular)
- Document resolver implementation patterns (DataLoader usage, N+1 prevention)
- Generate TypeScript types from schema, validate frontend integration

<!-- anchor: deeper-dive-job-system -->
#### 5.2.2. DelayedJob System Implementation

**Outstanding Questions**:
- Locking mechanism details (PostgreSQL advisory locks vs. `FOR UPDATE SKIP LOCKED`?)
- Retry backoff algorithm (exponential, capped at what max delay?)
- Job timeout handling (worker crashes mid-job, how to reclaim?)
- Priority queue implementation (separate tables per priority vs. indexed column?)

**Action Items**:
- Document job lifecycle state machine (PENDING → IN_PROGRESS → COMPLETED/FAILED)
- Define job payload schema (JSON structure for each job type)
- Create admin UI for job monitoring (queue depth, failed jobs, retry controls)
- Implement dead letter queue for permanently failed jobs (manual investigation)

<!-- anchor: deeper-dive-pdf-rendering -->
#### 5.2.3. PDF Rendering Engine

**Outstanding Questions**:
- Batik performance benchmarks (time/memory for 36" x 23" calendar?)
- Emoji rendering (how to embed Unicode emoji in PDF? font support?)
- Astronomical overlay positioning (SVG layer composition strategy?)
- Watermark implementation (transparent overlay, positioned where?)

**Action Items**:
- Prototype calendar SVG generation (sample layouts with events, holidays, moon phases)
- Performance test Batik rendering (measure time/memory for various calendar complexities)
- Define print-ready PDF spec (resolution, color space, bleed marks)
- Create quality assurance checklist (manual review before production deployment)

<!-- anchor: deeper-dive-analytics -->
#### 5.2.4. Analytics Data Model

**Outstanding Questions**:
- What business metrics are critical for launch? (revenue, conversion rate, popular templates...)
- Real-time vs. batch aggregation? (daily rollups sufficient or need real-time dashboard?)
- Data retention policy? (raw page views stored how long? rollups forever?)
- Analytics tool integration? (Google Analytics 4, Mixpanel, or custom only?)

**Action Items**:
- Define analytics event taxonomy (page_view, calendar_created, order_placed, etc.)
- Design analytics rollup jobs (daily aggregation, monthly reports)
- Create admin analytics dashboard wireframes (revenue chart, funnel visualization, template popularity)
- Implement event tracking instrumentation (GraphQL mutations emit analytics events)

<!-- anchor: deeper-dive-security-hardening -->
#### 5.2.5. Security Hardening

**Outstanding Questions**:
- WAF rules for Cloudflare (block known attack patterns, rate limit aggressive IPs?)
- Penetration testing scope (OWASP Top 10 coverage, third-party audit?)
- Incident response team (who gets paged for security alerts? escalation path?)
- Compliance requirements (GDPR checklist completed? CCPA opt-out mechanism?)

**Action Items**:
- Security threat modeling workshop (STRIDE framework)
- Code security audit (manual review + automated tools like SonarQube, Snyk)
- Create security runbooks (SQL injection response, DDoS mitigation, data breach protocol)
- Implement security training for team (OWASP awareness, secure coding practices)

<!-- anchor: deeper-dive-infrastructure-automation -->
#### 5.2.6. Infrastructure as Code Completeness

**Outstanding Questions**:
- Are all Cloudflare resources in Terraform? (DNS records, tunnel config, R2 buckets?)
- Kubernetes manifests version-controlled? (Deployments, Services, ConfigMaps, Secrets?)
- Disaster recovery tested? (restore from backup to fresh cluster, documented steps?)
- Secrets management strategy? (Kubernetes secrets encrypted at rest? rotation automated?)

**Action Items**:
- Audit existing Terraform/Ansible code (ensure all resources defined as code)
- Create infrastructure provisioning runbook (from-scratch cluster setup steps)
- Test disaster recovery scenario (simulate database corruption, restore from backup)
- Document infrastructure architecture (network diagram, firewall rules, VPN config)

---

<!-- anchor: glossary -->
## 6. Glossary

**Term** | **Definition**
---------|---------------
**C4 Model** | Context, Containers, Components, Code - hierarchical architectural diagram framework
**CDN** | Content Delivery Network - globally distributed cache servers (Cloudflare)
**Circuit Breaker** | Fault tolerance pattern that prevents cascading failures by fast-failing when error threshold exceeded
**CSRF** | Cross-Site Request Forgery - attack where malicious site tricks user's browser into making unauthorized request
**DelayedJob** | Asynchronous job processing pattern (queue table in database, workers poll and execute)
**ERD** | Entity Relationship Diagram - visual representation of database schema
**GraphQL** | Query language for APIs enabling clients to request exactly the data they need
**HPA** | Horizontal Pod Autoscaler - Kubernetes component that scales pods based on metrics
**JDBC** | Java Database Connectivity - Java API for database access
**JWT** | JSON Web Token - compact, self-contained token for securely transmitting information between parties
**K3s** | Lightweight Kubernetes distribution designed for resource-constrained environments
**OAuth 2.0** | Industry-standard authorization framework for delegating access to user resources
**OIDC** | OpenID Connect - identity layer on top of OAuth 2.0 for authentication
**Panache** | Quarkus extension simplifying Hibernate ORM with active record pattern
**PII** | Personally Identifiable Information - data that can identify a specific individual
**Quarkus** | Java framework optimized for containers and cloud-native applications
**R2** | Cloudflare's S3-compatible object storage service
**RBAC** | Role-Based Access Control - access control model based on user roles
**RPO** | Recovery Point Objective - maximum acceptable data loss duration
**RTO** | Recovery Time Objective - maximum acceptable downtime duration
**SLA** | Service Level Agreement - commitment to uptime and performance level
**SPA** | Single-Page Application - web app that loads single HTML page, dynamically updates content
**Vert.x** | Reactive toolkit for building event-driven applications on the JVM
**WAF** | Web Application Firewall - filters and monitors HTTP traffic to protect against attacks
**XSS** | Cross-Site Scripting - injection attack where malicious scripts executed in user's browser
