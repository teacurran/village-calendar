# System Architecture Blueprint: Village Calendar

**Version:** 1.0
**Date:** 2025-10-16

---

<!-- anchor: system-structure-and-data -->
## 3. Proposed Architecture (Continued)

<!-- anchor: system-context-diagram -->
### 3.3. System Context Diagram (C4 Level 1)

**Description:**

This diagram illustrates the Village Calendar system in its operational environment, showing the primary actors (users, administrators) and external systems it integrates with. The system boundary encompasses the Quarkus backend application, Vue.js frontend, PostgreSQL database, and asynchronous job processing infrastructure deployed on Kubernetes. External dependencies include OAuth identity providers, Stripe for payment processing, email delivery services, and Cloudflare edge services.

**Key Interactions:**
- **End Users**: Create calendars, manage accounts, place orders via web browser (HTTPS)
- **Admin Users**: Manage templates, process orders, view analytics via web admin panel
- **OAuth Providers**: Authenticate users via Google, Facebook, Apple login flows
- **Stripe**: Process payments, send webhook notifications for payment events
- **Email Service**: Deliver transactional emails (order confirmations, shipping updates)
- **Cloudflare**: Provides CDN caching, DDoS protection, DNS, and tunnel ingress

**Diagram:**

~~~plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Context.puml

LAYOUT_WITH_LEGEND()

title System Context Diagram - Village Calendar

Person(customer, "Customer", "End user creating and ordering calendars")
Person(admin, "Administrator", "Manages templates, orders, and analytics")

System_Boundary(village_calendar_boundary, "Village Calendar System") {
  System(village_calendar, "Village Calendar Platform", "Enables custom calendar creation, PDF generation, and e-commerce fulfillment")
}

System_Ext(oauth_google, "Google OAuth", "Identity provider for authentication")
System_Ext(oauth_facebook, "Facebook OAuth", "Identity provider for authentication")
System_Ext(oauth_apple, "Apple OAuth", "Identity provider for authentication")
System_Ext(stripe, "Stripe Payment Gateway", "Processes payments, manages subscriptions")
System_Ext(email_service, "Email Service", "Delivers transactional emails (GoogleWorkspace SMTP or AWS SES)")
System_Ext(cloudflare, "Cloudflare", "CDN, DNS, DDoS protection, tunnel ingress, R2 object storage")

Rel(customer, village_calendar, "Creates calendars, places orders", "HTTPS/Browser")
Rel(admin, village_calendar, "Manages templates and orders", "HTTPS/Admin Panel")

Rel(village_calendar, oauth_google, "Authenticates users", "OAuth 2.0/OIDC")
Rel(village_calendar, oauth_facebook, "Authenticates users", "OAuth 2.0/OIDC")
Rel(village_calendar, oauth_apple, "Authenticates users", "OAuth 2.0/OIDC")

Rel(village_calendar, stripe, "Processes payments", "Stripe API/HTTPS")
Rel_Back(stripe, village_calendar, "Sends payment webhooks", "HTTPS/Webhook")

Rel(village_calendar, email_service, "Sends transactional emails", "SMTP/TLS")
Rel_Back(email_service, customer, "Delivers emails", "Email")

Rel(village_calendar, cloudflare, "Stores PDFs/images, serves static assets", "S3 API/HTTPS")
Rel(cloudflare, customer, "Serves cached content", "HTTPS/CDN")

@enduml
~~~

<!-- anchor: container-diagram -->
### 3.4. Container Diagram (C4 Level 2)

**Description:**

This diagram decomposes the Village Calendar system into its major deployable containers and data stores. The architecture consists of:

1. **Vue.js SPA**: Client-side single-page application served via Quarkus Quinoa plugin
2. **Quarkus API Application**: Backend monolith exposing GraphQL API, REST endpoints, and business logic
3. **PostgreSQL Database**: Primary data store for all application data (users, calendars, orders, jobs)
4. **Async Job Workers**: Quarkus instances dedicated to processing DelayedJob queue (PDF generation, emails)
5. **Cloudflare R2**: Object storage for generated PDFs and calendar preview images
6. **Jaeger**: Distributed tracing backend for observability
7. **Prometheus**: Metrics collection and storage

All containers run within Kubernetes pods, with the API application and job workers sharing the same codebase but configured for different runtime modes.

**Diagram:**

~~~plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml

LAYOUT_TOP_DOWN()

title Container Diagram - Village Calendar Platform

Person(customer, "Customer", "End user creating calendars")
Person(admin, "Administrator", "Manages system")

System_Ext(oauth_providers, "OAuth Providers", "Google, Facebook, Apple")
System_Ext(stripe, "Stripe", "Payment processing")
System_Ext(email_service, "Email Service", "SMTP gateway")

System_Boundary(village_calendar_system, "Village Calendar System") {

  Container(spa, "Vue.js SPA", "Vue 3, Vite, PrimeVue, TypeScript", "Provides calendar editor UI, user account management, order checkout")

  Container(api_app, "Quarkus API Application", "Java 21, Quarkus 3.26", "Exposes GraphQL API, handles business logic, orchestrates jobs, serves SPA")

  Container(job_worker, "Async Job Workers", "Java 21, Quarkus 3.26", "Processes DelayedJob queue: PDF generation, email sending, analytics")

  ContainerDb(postgres, "PostgreSQL Database", "PostgreSQL 17 + PostGIS", "Stores users, calendars, events, orders, templates, job queue, analytics")

  Container(r2_storage, "Cloudflare R2", "Object Storage", "Stores generated PDFs, calendar preview images, user uploads")

  Container(jaeger, "Jaeger", "Distributed Tracing", "Collects and visualizes request traces")

  Container(prometheus, "Prometheus", "Metrics Storage", "Collects application and business metrics")
}

System_Ext(cloudflare_cdn, "Cloudflare CDN", "Edge caching, DDoS protection")

Rel(customer, spa, "Uses", "HTTPS/Browser")
Rel(admin, spa, "Uses", "HTTPS/Browser")

Rel(spa, api_app, "Makes API calls", "GraphQL/HTTPS, REST/HTTPS")
Rel(spa, cloudflare_cdn, "Loads static assets", "HTTPS")

Rel(api_app, postgres, "Reads/writes data", "JDBC/SQL, HikariCP pool")
Rel(api_app, r2_storage, "Uploads PDFs/images", "S3 API/HTTPS")
Rel(api_app, oauth_providers, "Authenticates users", "OAuth 2.0/OIDC")
Rel(api_app, stripe, "Processes payments", "Stripe API/HTTPS")
Rel_Back(stripe, api_app, "Sends webhooks", "HTTPS")
Rel(api_app, email_service, "Sends emails", "SMTP/TLS")
Rel(api_app, jaeger, "Sends traces", "OpenTelemetry/gRPC")
Rel(api_app, prometheus, "Exposes metrics", "/q/metrics endpoint")

Rel(job_worker, postgres, "Polls job queue, updates status", "JDBC/SQL")
Rel(job_worker, r2_storage, "Stores generated PDFs", "S3 API/HTTPS")
Rel(job_worker, email_service, "Sends async emails", "SMTP/TLS")
Rel(job_worker, jaeger, "Sends traces", "OpenTelemetry/gRPC")
Rel(job_worker, prometheus, "Exposes metrics", "/q/metrics endpoint")

Rel(api_app, job_worker, "Triggers jobs via EventBus", "Vert.x EventBus (in-process or clustered)")

Rel(cloudflare_cdn, r2_storage, "Fetches cached content", "Internal Cloudflare")
Rel(cloudflare_cdn, customer, "Serves content", "HTTPS")

@enduml
~~~

**Container Responsibilities:**

- **Vue.js SPA**: Reactive user interface, form validation, calendar editor interactions, state management (Pinia), routing
- **Quarkus API Application**: GraphQL schema, REST endpoints, business logic services, Hibernate repositories, OAuth integration, Stripe integration, job enqueueing
- **Async Job Workers**: DelayedJob processing loop, PDF rendering (Batik), email composition, retry logic, failure handling
- **PostgreSQL Database**: ACID transactions, relational data storage, job queue persistence, full-text search (future), JSONB for flexible schemas
- **Cloudflare R2**: Durable object storage, versioning, public/private bucket configuration, CDN origin
- **Jaeger**: Trace aggregation, visualization UI, dependency graphs, latency analysis
- **Prometheus**: Time-series metrics, query language (PromQL), alerting rules, integration with Grafana (future)

<!-- anchor: component-diagram -->
### 3.5. Component Diagram(s) (C4 Level 3)

**Description:**

This diagram zooms into the **Quarkus API Application** container, revealing the major internal components and their dependencies. The architecture follows a layered pattern:

1. **GraphQL/REST Controllers**: HTTP entry points, request validation, response formatting
2. **Service Layer**: Business logic, workflow orchestration, transaction boundaries
3. **Repository Layer**: Data access via Panache, query construction, entity mapping
4. **Job Management**: DelayedJob enqueueing, EventBus integration
5. **Integration Components**: OAuth client, Stripe client, email sender, R2 client
6. **Security Components**: OIDC token validation, RBAC enforcement, CSRF protection

**Diagram (Quarkus API Application):**

~~~plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Component.puml

LAYOUT_WITH_LEGEND()

title Component Diagram - Quarkus API Application

Container_Boundary(api_app, "Quarkus API Application") {

  Component(graphql_api, "GraphQL API", "SmallRye GraphQL", "Exposes queries (calendars, users, orders) and mutations (createCalendar, placeOrder)")

  Component(rest_controllers, "REST Controllers", "JAX-RS", "Health checks, webhooks (Stripe), file downloads")

  Component(calendar_service, "Calendar Service", "CDI Bean", "Calendar CRUD, template application, astronomical calculations")

  Component(user_service, "User Service", "CDI Bean", "User registration, profile management, session conversion")

  Component(order_service, "Order Service", "CDI Bean", "Order placement, payment processing, status updates, refunds")

  Component(pdf_service, "PDF Service", "CDI Bean", "Enqueues PDF generation jobs, watermarking logic")

  Component(job_manager, "Job Manager", "CDI Bean", "DelayedJob enqueueing, priority assignment, EventBus publishing")

  Component(calendar_repo, "Calendar Repository", "Panache Repository", "Calendar entity queries, template filtering")

  Component(user_repo, "User Repository", "Panache Repository", "User entity queries, OAuth profile lookup")

  Component(order_repo, "Order Repository", "Panache Repository", "Order entity queries, status updates")

  Component(job_repo, "DelayedJob Repository", "Panache Repository", "Job queue queries, locking, status updates")

  Component(oauth_client, "OAuth Client", "Quarkus OIDC", "Google/Facebook/Apple token validation, user info retrieval")

  Component(stripe_client, "Stripe Client", "Stripe Java SDK", "Checkout session creation, webhook validation, refunds")

  Component(email_sender, "Email Sender", "JavaMail", "Transactional email composition, SMTP delivery")

  Component(r2_client, "R2 Client", "AWS SDK S3", "PDF upload, preview image storage, presigned URLs")

  Component(security_filter, "Security Filter", "Quarkus Security", "JWT validation, RBAC enforcement, CSRF checks")
}

ContainerDb_Ext(postgres, "PostgreSQL Database", "Relational data")
Container_Ext(r2_storage, "Cloudflare R2", "Object storage")
System_Ext(oauth_providers, "OAuth Providers", "Identity")
System_Ext(stripe_api, "Stripe API", "Payments")
System_Ext(email_service, "Email Service", "SMTP")
Container_Ext(eventbus, "Vert.x EventBus", "Job triggers")

Rel(graphql_api, calendar_service, "Uses")
Rel(graphql_api, user_service, "Uses")
Rel(graphql_api, order_service, "Uses")
Rel(graphql_api, security_filter, "Protected by")

Rel(rest_controllers, order_service, "Uses (webhooks)")
Rel(rest_controllers, pdf_service, "Uses (downloads)")

Rel(calendar_service, calendar_repo, "Uses")
Rel(calendar_service, pdf_service, "Triggers PDF generation")

Rel(user_service, user_repo, "Uses")
Rel(user_service, oauth_client, "Validates tokens")

Rel(order_service, order_repo, "Uses")
Rel(order_service, stripe_client, "Creates checkout, processes refunds")
Rel(order_service, email_sender, "Sends order confirmations")
Rel(order_service, job_manager, "Enqueues email jobs")

Rel(pdf_service, job_manager, "Enqueues PDF jobs")

Rel(job_manager, job_repo, "Persists jobs")
Rel(job_manager, eventbus, "Publishes job events")

Rel(calendar_repo, postgres, "Reads/writes", "JDBC")
Rel(user_repo, postgres, "Reads/writes", "JDBC")
Rel(order_repo, postgres, "Reads/writes", "JDBC")
Rel(job_repo, postgres, "Reads/writes", "JDBC")

Rel(oauth_client, oauth_providers, "Calls", "HTTPS/OIDC")
Rel(stripe_client, stripe_api, "Calls", "HTTPS/REST")
Rel(email_sender, email_service, "Sends", "SMTP/TLS")
Rel(r2_client, r2_storage, "Uploads/retrieves", "S3 API/HTTPS")

@enduml
~~~

**Component Interaction Patterns:**

1. **GraphQL Query Flow**: Client → GraphQL API → Service Layer → Repository → PostgreSQL → Response mapping → Client
2. **Order Placement Flow**: Client → GraphQL Mutation (placeOrder) → Order Service → Stripe Client (create checkout) → Order Repository (persist) → Job Manager (enqueue email) → EventBus → Response
3. **PDF Generation Flow**: Calendar Service → PDF Service → Job Manager → Job Repository → EventBus → Job Worker (async) → Batik Renderer → R2 Client (upload)

<!-- anchor: data-model-overview -->
### 3.6. Data Model Overview & ERD

**Description:**

The data model is optimized for the calendar creation and e-commerce workflows, with careful consideration for session persistence (anonymous users), job processing, and analytics. PostgreSQL's JSONB type is used for flexible calendar metadata (event details, configuration options) while maintaining relational integrity for core entities.

**Key Design Decisions:**

1. **User Identity**: `users` table stores OAuth provider info (`oauth_provider`, `oauth_subject_id`) to support multiple providers per user
2. **Anonymous Sessions**: `calendar_sessions` table tracks guest user calendars, linked to `users` table upon login conversion
3. **Calendar Versioning**: `calendars` table includes `version` field for optimistic locking, future support for edit history
4. **Order Status**: `orders.status` enum (PENDING, PAID, IN_PRODUCTION, SHIPPED, DELIVERED, CANCELLED, REFUNDED) drives workflow state machine
5. **Job Queue**: `delayed_jobs` table with `locked_at`, `locked_by`, `attempts`, `last_error` supports distributed worker coordination
6. **Templates**: `calendar_templates` is separate from `calendars` to enable admin-curated vs user-created distinction
7. **Analytics**: `page_views`, `analytics_rollups` tables support basic analytics without external service dependency (Phase 1)

**Key Entities:**

- **User**: Registered user account with OAuth authentication
- **CalendarSession**: Anonymous user session data (pre-authentication)
- **Calendar**: User's saved calendar with events and configuration
- **CalendarTemplate**: Admin-created template calendars
- **Event**: Custom event on a calendar (date, text, emoji)
- **Order**: E-commerce order for printed calendar
- **OrderItem**: Line items in an order (supports future multi-calendar orders)
- **Payment**: Stripe payment record linked to order
- **DelayedJob**: Asynchronous job queue entry
- **PageView**: Analytics event for page visits
- **AnalyticsRollup**: Aggregated analytics (daily/weekly/monthly)

**Diagram (ERD):**

~~~plantuml
@startuml

title Entity Relationship Diagram - Village Calendar

' Core user and identity
entity User {
  *user_id : bigserial <<PK>>
  --
  oauth_provider : varchar(50) <<google|facebook|apple>>
  oauth_subject_id : varchar(255) <<unique per provider>>
  email : varchar(255) <<unique>>
  display_name : varchar(255)
  profile_picture_url : text
  role : varchar(20) <<user|admin>>
  created_at : timestamp
  updated_at : timestamp
  last_login_at : timestamp
  --
  INDEX: email, oauth_provider+oauth_subject_id
}

entity CalendarSession {
  *session_id : uuid <<PK>>
  --
  user_id : bigint <<FK, nullable>>
  session_data : jsonb <<calendar state>>
  created_at : timestamp
  expires_at : timestamp
  converted_at : timestamp
  --
  INDEX: session_id, user_id
}

' Calendar domain
entity Calendar {
  *calendar_id : bigserial <<PK>>
  --
  user_id : bigint <<FK>>
  template_id : bigint <<FK, nullable>>
  title : varchar(255)
  year : integer
  config : jsonb <<settings, holidays, astronomy>>
  preview_image_url : text
  pdf_url : text <<R2 object key>>
  is_public : boolean
  share_token : uuid <<unique>>
  version : integer <<optimistic locking>>
  created_at : timestamp
  updated_at : timestamp
  --
  INDEX: user_id, year, is_public, share_token
}

entity CalendarTemplate {
  *template_id : bigserial <<PK>>
  --
  created_by_user_id : bigint <<FK>>
  name : varchar(255)
  description : text
  thumbnail_url : text
  config : jsonb <<default settings>>
  is_active : boolean
  sort_order : integer
  created_at : timestamp
  updated_at : timestamp
  --
  INDEX: is_active, sort_order
}

entity Event {
  *event_id : bigserial <<PK>>
  --
  calendar_id : bigint <<FK>>
  event_date : date
  event_text : varchar(500)
  emoji : varchar(100) <<unicode emoji>>
  color : varchar(20) <<hex color>>
  created_at : timestamp
  --
  INDEX: calendar_id, event_date
}

' E-commerce
entity Order {
  *order_id : bigserial <<PK>>
  --
  user_id : bigint <<FK>>
  order_number : varchar(50) <<unique, display>>
  status : varchar(50) <<enum>>
  subtotal : decimal(10,2)
  tax : decimal(10,2)
  shipping_cost : decimal(10,2)
  total : decimal(10,2)
  currency : varchar(3) <<USD>>
  shipping_address : jsonb <<name, address, city, state, zip>>
  tracking_number : varchar(255)
  admin_notes : text
  created_at : timestamp
  updated_at : timestamp
  shipped_at : timestamp
  delivered_at : timestamp
  --
  INDEX: user_id, status, order_number, created_at
}

entity OrderItem {
  *order_item_id : bigserial <<PK>>
  --
  order_id : bigint <<FK>>
  calendar_id : bigint <<FK>>
  product_type : varchar(50) <<standard|premium>>
  quantity : integer
  unit_price : decimal(10,2)
  subtotal : decimal(10,2)
  --
  INDEX: order_id
}

entity Payment {
  *payment_id : bigserial <<PK>>
  --
  order_id : bigint <<FK>>
  stripe_payment_intent_id : varchar(255) <<unique>>
  stripe_checkout_session_id : varchar(255)
  amount : decimal(10,2)
  status : varchar(50) <<succeeded|pending|failed|refunded>>
  payment_method : varchar(50)
  refund_amount : decimal(10,2)
  refund_reason : text
  created_at : timestamp
  updated_at : timestamp
  --
  INDEX: order_id, stripe_payment_intent_id
}

' Async jobs
entity DelayedJob {
  *job_id : bigserial <<PK>>
  --
  job_type : varchar(100) <<PdfGenerationJob, EmailJob>>
  payload : jsonb <<job parameters>>
  priority : integer <<0=default, higher=urgent>>
  attempts : integer
  max_attempts : integer
  run_at : timestamp <<scheduled execution time>>
  locked_at : timestamp
  locked_by : varchar(255) <<worker pod name>>
  failed_at : timestamp
  last_error : text
  completed_at : timestamp
  created_at : timestamp
  --
  INDEX: run_at, priority, locked_at
}

' Analytics
entity PageView {
  *page_view_id : bigserial <<PK>>
  --
  user_id : bigint <<FK, nullable>>
  session_id : uuid <<FK, nullable>>
  url_path : varchar(500)
  referrer : varchar(500)
  user_agent : varchar(500)
  ip_address : inet
  created_at : timestamp
  --
  INDEX: user_id, session_id, created_at
}

entity AnalyticsRollup {
  *rollup_id : bigserial <<PK>>
  --
  rollup_date : date
  metric_name : varchar(100)
  metric_value : bigint
  dimensions : jsonb <<breakdown by product, template, etc>>
  created_at : timestamp
  --
  INDEX: rollup_date, metric_name
  UNIQUE: rollup_date + metric_name + dimensions hash
}

' Relationships
User ||--o{ CalendarSession : "converts from"
User ||--o{ Calendar : "owns"
User ||--o{ CalendarTemplate : "creates"
User ||--o{ Order : "places"
User ||--o{ PageView : "generates"

CalendarTemplate ||--o{ Calendar : "instantiated as"

Calendar ||--o{ Event : "contains"
Calendar ||--o{ OrderItem : "ordered as"

Order ||--o{ OrderItem : "contains"
Order ||--|| Payment : "paid via"

CalendarSession ||--o{ PageView : "generates"

@enduml
~~~

**Database Indexes Strategy:**

- **Primary Keys**: All tables use `bigserial` auto-incrementing primary keys for simplicity and performance
- **Foreign Keys**: Enforce referential integrity with cascading deletes where appropriate (e.g., `Calendar.user_id` ON DELETE CASCADE)
- **Lookup Indexes**: Composite indexes on frequently queried columns (e.g., `(user_id, created_at)` for calendar lists)
- **Unique Constraints**: `users.email`, `orders.order_number`, `calendar.share_token` for business logic enforcement
- **JSONB Indexes**: GIN indexes on `calendar.config` for filtering by holiday sets, astronomy options (e.g., `WHERE config @> '{"astronomy": {"moon_phases": true}}'`)

**Data Volume Projections (First Year):**

- Users: ~10,000 registered (+ 50,000 anonymous sessions)
- Calendars: ~25,000 saved (2.5 calendars per registered user)
- Orders: ~5,000 (20% conversion from saved calendars)
- DelayedJobs: ~100,000 processed (PDF generation, emails, analytics)
- PageViews: ~500,000 (avg 10 page views per session)

**Scalability Considerations:**

- **Partitioning**: `page_views` and `analytics_rollups` tables are candidates for time-based partitioning (monthly) if growth exceeds projections
- **Archival**: Orders older than 7 years moved to archive table (legal retention requirements)
- **Read Replicas**: Analytics queries can be offloaded to read replicas in Phase 2
