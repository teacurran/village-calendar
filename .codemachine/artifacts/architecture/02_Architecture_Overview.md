# System Architecture Blueprint: Village Calendar

**Version:** 1.0
**Date:** 2025-10-16

---

<!-- anchor: proposed-architecture -->
## 3. Proposed Architecture

<!-- anchor: architectural-style -->
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

**Asynchronous Processing Pattern:**

A critical architectural component is the **DelayedJob asynchronous processing system**, modeled after Ruby on Rails' DelayedJob:

- **Purpose**: Offload resource-intensive or retriable operations (PDF rendering, bulk emails, analytics rollups) from synchronous request/response cycles
- **Mechanism**: Vert.x EventBus for event-driven job triggering + PostgreSQL-backed job queue table with locking mechanism
- **Benefits**:
  - Non-blocking user experience (submit request → get immediate response → job processes in background)
  - Retry logic with exponential backoff for transient failures
  - Priority queues for urgent jobs (paid PDF downloads > free tier)
  - Monitoring and alerting on job failures

**Migration Path:**

The modular structure provides a clear evolution path. If specific modules require independent scaling (e.g., PDF generation becomes bottleneck), they can be extracted into separate services with minimal refactoring:

1. Extract module as standalone Quarkus application
2. Replace internal method calls with HTTP/gRPC service calls
3. Maintain shared data models via generated stubs or shared libraries

However, for MVP and foreseeable future (first 12-18 months), monolithic deployment is optimal.

**Alternative Considered: Full Microservices**

A microservices architecture was considered with services like:
- User Service (authentication, profiles)
- Calendar Service (CRUD operations, templates)
- PDF Service (rendering, storage)
- Order Service (e-commerce, fulfillment)
- Notification Service (emails, webhooks)

**Rejected because:**
- Operational complexity outweighs benefits at current scale
- Team lacks dedicated DevOps resources for service mesh, distributed tracing, and complex deployment orchestration
- Added latency from inter-service calls would degrade user experience (calendar preview requires data from multiple services)
- Distributed data consistency challenges (saga pattern, eventual consistency) inappropriate for transactional e-commerce flows

<!-- anchor: technology-stack-summary -->
### 3.2. Technology Stack Summary

The following table summarizes the technology choices across all architectural layers, with justifications aligned to project requirements and constraints.

| **Category** | **Technology** | **Version** | **Justification** |
|--------------|----------------|-------------|-------------------|
| **Backend Framework** | Quarkus | 3.26.2 | Mandated by existing stack. Provides fast startup, low memory footprint (ideal for containers), and rich ecosystem (Hibernate, GraphQL, OIDC, Health). |
| **Runtime** | Java (OpenJDK) | 21 LTS | Latest LTS release. Performance improvements (virtual threads in future), security updates, long support lifecycle. |
| **ORM** | Hibernate ORM with Panache | (bundled) | Active record pattern simplifies CRUD. Type-safe queries. Integrated with Quarkus transaction management. |
| **Database** | PostgreSQL | 17+ | Mandated. PostGIS extension for geospatial queries (future: location-based features). JSONB for flexible calendar metadata. Robust, proven, open-source. |
| **Database Extensions** | PostGIS | Latest | Geospatial data types and functions (future: user location, shipping calculations). Required by existing infrastructure. |
| **Database Migrations** | MyBatis Migrations | (via Maven) | Already configured in project. Version-controlled schema changes. Simple up/down migration scripts. |
| **API - Primary** | GraphQL (SmallRye) | (bundled) | Flexible frontend queries (fetch calendar + user + templates in single request). Schema evolution without versioning. Strong typing. |
| **API - Secondary** | REST (JAX-RS) | (bundled) | Webhooks (Stripe payment confirmations), health checks, simple CRUD where GraphQL overkill. |
| **Async Processing** | Custom DelayedJob + Vert.x EventBus | (bundled) | Existing pattern proven in VillageCMS prototype. EventBus enables reactive, non-blocking job execution. PostgreSQL-backed queue for persistence. |
| **Job Scheduling** | Quarkus Scheduler | (bundled) | Cron-based fallback for stuck jobs, periodic analytics rollups, abandoned cart emails. |
| **Authentication** | Quarkus OIDC | (bundled) | OAuth 2.0 / OpenID Connect for Google, Facebook, Apple login. Industry-standard security. Delegated identity management. |
| **Security** | Quarkus Security + SmallRye JWT | (bundled) | RBAC (role-based access control) for admin vs user. JWT tokens for stateless auth. CSRF protection. |
| **Health Checks** | SmallRye Health | (bundled) | Kubernetes liveness/readiness probes. Custom checks for database, job queue health. |
| **Observability - Tracing** | OpenTelemetry + Jaeger | Latest | Distributed tracing for request flows (GraphQL query → service layer → database). Debug performance bottlenecks. |
| **Observability - Metrics** | Micrometer + Prometheus | (bundled) | Application metrics (request counts, durations, job queue depth). Business metrics (orders, revenue). |
| **Observability - Logging** | SLF4J + Logback | (bundled) | Structured JSON logging. Centralized aggregation (future: ELK stack or Loki). |
| **PDF Generation** | Apache Batik | 1.17 | SVG to PDF rendering. High-fidelity vector graphics. Supports custom fonts, complex layouts. Proven in prototype. |
| **Astronomical Calculations** | SunCalc (port) + Proj4J | Custom/4.1 | Moon phase calculations, Hebrew calendar conversions. Geospatial projections. |
| **Payment Processing** | Stripe SDK (Java) | Latest | PCI DSS compliance delegation. Checkout Sessions for secure payment flow. Webhooks for order updates. |
| **Email Delivery** | JavaMail + SMTP | (bundled) | GoogleWorkspace SMTP for low volume (<10k/month). Migrate to AWS SES if scaling needed. Transactional emails (order confirmations). |
| **Object Storage** | Cloudflare R2 SDK | Latest | S3-compatible API. Store generated PDFs, calendar preview images. Cost-effective, global distribution. |
| **Frontend Framework** | Vue.js | 3.5+ | Mandated. Composition API for reactive components. Strong ecosystem. Team expertise. |
| **UI Component Library** | PrimeVue | 4.2+ | Comprehensive component set (forms, tables, dialogs). Aura theme. Accessibility built-in. Reduces custom CSS. |
| **Icons** | PrimeIcons | 7.0+ | Consistent iconography. Optimized SVGs. Works seamlessly with PrimeVue. |
| **CSS Framework** | TailwindCSS | 4.0+ | Utility-first styling. Rapid prototyping. Small bundle size (purged unused classes). |
| **State Management** | Pinia | Latest | Vue 3 recommended store. Simpler than Vuex. Type-safe with TypeScript. DevTools integration. |
| **Routing** | Vue Router | 4.5+ | Client-side routing. Lazy-loaded routes for code splitting. Navigation guards for auth checks. |
| **Internationalization** | Vue I18n | Latest | Placeholder for future localization (Phase 2+). Supports message formatting, pluralization. |
| **Build Tool** | Vite | 6.1+ | Fast HMR (hot module replacement). Optimized production builds. Quinoa plugin integrates with Quarkus. |
| **TypeScript** | TypeScript | ~5.7.3 | Type safety for Vue components, API contracts. IDE autocomplete. Catch errors at compile time. |
| **Quarkus-Vue Integration** | Quinoa | (bundled) | Seamless SPA integration. Serves Vue app from Quarkus. Single deployment artifact. |
| **Container Runtime** | Docker | Latest | Quarkus native image or JVM mode container. Kubernetes deployment. |
| **Orchestration** | Kubernetes (k3s) | Latest | Existing infrastructure. Horizontal scaling, rolling updates, health probes. |
| **Infrastructure as Code** | Terraform | 1.7.4 | Cloudflare resources (DNS, tunnels, R2 buckets). AWS resources (future SES). Reproducible infrastructure. |
| **Configuration Management** | Ansible | Latest | Kubernetes deployment automation. Server provisioning. Secret management. |
| **CDN / Edge** | Cloudflare CDN | N/A | Global edge caching for static assets. DDoS protection. WAF rules. |
| **DNS** | Cloudflare DNS | N/A | Fast DNS resolution. Integrated with CDN. |
| **Ingress / Tunnel** | Cloudflare Tunnel | N/A | Secure ingress to k3s cluster without exposing IPs. No inbound firewall rules needed. |
| **VPN** | WireGuard | Latest | Secure access for CI/CD (GitHub Actions → k3s). Low overhead, modern cryptography. |
| **CI/CD** | GitHub Actions | N/A | Automated testing, builds, deployments. WireGuard integration for k3s access. |

**Key Technology Decisions:**

1. **GraphQL over REST**: Frontend complexity (calendar editor needs nested data: calendar → events → user → templates) benefits from GraphQL's flexible querying. Single request replaces multiple REST round-trips.

2. **Quarkus over Spring Boot**: Already mandated, but validated choice. Faster startup (important for Kubernetes autoscaling), lower memory usage, cloud-native design.

3. **PostgreSQL over NoSQL**: Calendar data is relational (users, calendars, events, orders). ACID transactions critical for e-commerce. JSONB provides flexibility for calendar metadata without schema rigidity.

4. **Stripe over Custom Payment**: PCI compliance is complex and risky. Stripe Checkout delegates payment form rendering, card data handling, and compliance to Stripe. 2.9% + $0.30 fee justified by reduced risk and development time.

5. **Cloudflare R2 over S3**: S3-compatible API (easy migration if needed), but lower egress costs. Tight integration with Cloudflare CDN. Single vendor for edge services simplifies billing and support.

6. **Pinia over Vuex**: Pinia is Vue 3 official recommendation. Simpler API, better TypeScript support, no mutations (actions only).

7. **Tailwind over Custom CSS**: Utility-first approach accelerates UI development. PurgeCSS removes unused classes (small bundle). PrimeVue handles complex components; Tailwind for layouts and spacing.

**Technology Evolution Path:**

- **Short-term (6-12 months)**: Migrate from GoogleWorkspace SMTP to AWS SES for higher email volume and better deliverability analytics
- **Medium-term (12-24 months)**: Introduce Redis for session caching and rate limiting (reduce database load)
- **Long-term (24+ months)**: Evaluate extracting PDF generation into separate service if it becomes scaling bottleneck
