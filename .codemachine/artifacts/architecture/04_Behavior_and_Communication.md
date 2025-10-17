# System Architecture Blueprint: Village Calendar

**Version:** 1.0
**Date:** 2025-10-16

---

<!-- anchor: behavior-and-communication -->
## 3. Proposed Architecture (Continued)

<!-- anchor: api-design-communication -->
### 3.7. API Design & Communication

<!-- anchor: api-style -->
#### 3.7.1. API Style

**Primary API: GraphQL**

The Village Calendar application uses **GraphQL** as its primary API protocol for frontend-to-backend communication. This choice is driven by the complex, nested data requirements of the calendar editor interface.

**Rationale for GraphQL:**

1. **Flexible Data Fetching**: Calendar editor requires nested data structures (User → Calendars → Events, Calendar → Template → Config). GraphQL allows fetching all related data in a single round-trip, eliminating the N+1 query problem common with REST.

2. **Reduced Over-fetching**: Frontend can request exactly the fields needed for each view (e.g., calendar list view only needs `id`, `title`, `preview_image_url`, while editor needs full `config`, `events[]`). This reduces payload size and improves performance on mobile connections.

3. **Schema Evolution**: GraphQL's strong typing and introspection enable adding new fields without versioning. Deprecated fields can be marked and gracefully removed over time.

4. **Developer Experience**: GraphQL Playground (auto-generated from schema) provides interactive API documentation and testing interface. Frontend developers can explore schema without reading separate docs.

5. **Type Safety**: SmallRye GraphQL generates TypeScript types for Vue.js frontend, ensuring compile-time type checking across the API boundary.

**GraphQL Schema Organization:**

- **Queries**: Read operations (e.g., `calendar(id)`, `calendars(userId)`, `templates()`, `order(orderId)`)
- **Mutations**: Write operations (e.g., `createCalendar`, `updateCalendar`, `placeOrder`, `generatePdf`)
- **Subscriptions**: Not implemented in MVP (future: real-time collaboration notifications)

**Example Schema Excerpt:**

```graphql
type Query {
  calendar(id: ID!): Calendar
  calendars(userId: ID!, year: Int): [Calendar!]!
  templates(isActive: Boolean): [CalendarTemplate!]!
  order(orderId: ID!): Order
  orders(userId: ID!, status: OrderStatus): [Order!]!
}

type Mutation {
  createCalendar(input: CreateCalendarInput!): Calendar!
  updateCalendar(id: ID!, input: UpdateCalendarInput!): Calendar!
  deleteCalendar(id: ID!): Boolean!

  generatePdf(calendarId: ID!, watermark: Boolean!): PdfJob!

  placeOrder(input: PlaceOrderInput!): Order!
  cancelOrder(orderId: ID!, reason: String): Order!

  convertGuestSession(sessionId: ID!): User!
}

type Calendar {
  id: ID!
  userId: ID!
  title: String!
  year: Int!
  config: CalendarConfig!
  events: [Event!]!
  template: CalendarTemplate
  previewImageUrl: String
  pdfUrl: String
  isPublic: Boolean!
  shareToken: String
  createdAt: DateTime!
  updatedAt: DateTime!
}

type CalendarConfig {
  holidaySets: [HolidaySet!]!
  astronomy: AstronomyConfig!
  layout: LayoutConfig!
  colors: ColorScheme!
}

type Event {
  id: ID!
  eventDate: Date!
  eventText: String!
  emoji: String
  color: String
}

type Order {
  id: ID!
  orderNumber: String!
  status: OrderStatus!
  items: [OrderItem!]!
  subtotal: Decimal!
  tax: Decimal!
  shippingCost: Decimal!
  total: Decimal!
  shippingAddress: Address!
  payment: Payment
  trackingNumber: String
  createdAt: DateTime!
  shippedAt: DateTime
}

enum OrderStatus {
  PENDING
  PAID
  IN_PRODUCTION
  SHIPPED
  DELIVERED
  CANCELLED
  REFUNDED
}

input CreateCalendarInput {
  title: String!
  year: Int!
  templateId: ID
  config: CalendarConfigInput
}

input PlaceOrderInput {
  calendarId: ID!
  productType: ProductType!
  quantity: Int!
  shippingAddress: AddressInput!
}
```

**Secondary API: REST (JAX-RS)**

REST endpoints are used for specific use cases where GraphQL is not optimal:

1. **Webhooks**: Stripe payment webhooks require predictable REST endpoints (`POST /api/webhooks/stripe`)
2. **Health Checks**: Kubernetes probes (`GET /q/health/live`, `GET /q/health/ready`)
3. **File Downloads**: Direct PDF downloads (`GET /api/downloads/pdf/{calendarId}?token={token}`)
4. **Metrics**: Prometheus scraping (`GET /q/metrics`)

**REST Endpoint Examples:**

- `POST /api/webhooks/stripe` - Stripe webhook receiver (validates signature, processes events)
- `GET /api/downloads/pdf/{calendarId}` - Generates signed download URL for PDF (requires auth token or share token)
- `GET /q/health/live` - Kubernetes liveness probe (returns 200 if app is running)
- `GET /q/health/ready` - Kubernetes readiness probe (returns 200 if app is ready to serve traffic)

<!-- anchor: communication-patterns -->
#### 3.7.2. Communication Patterns

The system employs two primary communication patterns based on operation characteristics:

**1. Synchronous Request/Response (GraphQL/REST over HTTPS)**

Used for operations requiring immediate feedback to the user:

- **Read Operations**: Fetching calendars, templates, orders (GraphQL queries)
- **Lightweight Writes**: Creating/updating calendar metadata (GraphQL mutations)
- **Authentication Flows**: OAuth redirects, token validation
- **Payment Initiation**: Creating Stripe checkout sessions

**Characteristics:**
- Client waits for server response (typically <500ms)
- Transactional consistency (database transaction commits before response)
- Error handling via HTTP status codes and GraphQL error extensions
- Retry logic in frontend for network failures

**2. Asynchronous Job Processing (DelayedJob + Vert.x EventBus)**

Used for long-running or retriable operations:

- **PDF Generation**: Rendering high-resolution calendars (10-30 seconds)
- **Email Sending**: Transactional notifications (prevent SMTP blocking request threads)
- **Analytics Aggregation**: Daily/weekly rollups (scheduled batch jobs)
- **Image Processing**: Generating calendar preview thumbnails (future)

**Workflow:**
1. Client initiates operation via GraphQL mutation (e.g., `generatePdf(calendarId)`)
2. API server validates request, creates `DelayedJob` record in database (status: PENDING)
3. API server publishes event to Vert.x EventBus
4. API server immediately returns job ID to client (status: PENDING)
5. Client polls for job status (GraphQL query: `pdfJob(id)`) or receives notification via email
6. Job worker pod consumes EventBus message, claims job (locks row in database)
7. Worker executes job logic (Batik PDF rendering), updates job status (IN_PROGRESS → COMPLETED/FAILED)
8. On success: Worker uploads PDF to R2, stores URL in `calendar.pdf_url`
9. On failure: Worker increments `attempts`, schedules retry with exponential backoff

**Benefits:**
- Non-blocking user experience (user can continue editing while PDF generates)
- Fault tolerance (job survives worker pod restarts via database persistence)
- Scalability (add more worker pods to handle load spikes)
- Retry logic (transient failures like network errors are automatically retried)

<!-- anchor: key-interaction-flows -->
#### 3.7.3. Key Interaction Flows

<!-- anchor: flow-user-login -->
##### Flow 1: User Login via OAuth (Google)

**Description:**

This flow illustrates how an anonymous user authenticates via Google OAuth, with the system converting their guest session into a permanent user account and linking any calendars created pre-authentication.

**Sequence Diagram:**

~~~plantuml
@startuml
actor User
participant "Vue SPA" as SPA
participant "Quarkus API" as API
participant "Quarkus OIDC" as OIDC
participant "Google OAuth" as Google
database "PostgreSQL" as DB

User -> SPA : Clicks "Sign in with Google"
SPA -> API : GET /oauth/login?provider=google
API -> OIDC : Initiate OAuth flow
OIDC -> Google : Redirect to Google login
Google -> User : Show Google consent screen
User -> Google : Approves access
Google -> OIDC : Redirect with auth code
OIDC -> Google : Exchange code for tokens
Google -> OIDC : ID token + access token
OIDC -> API : Validated user info (email, sub, name)

API -> DB : SELECT user WHERE oauth_provider='google' AND oauth_subject_id={sub}
alt User exists
  DB -> API : Return existing user
  API -> DB : UPDATE user SET last_login_at=NOW()
else User does not exist
  API -> DB : INSERT INTO users (oauth_provider, oauth_subject_id, email, display_name)
  DB -> API : Return new user_id

  API -> DB : SELECT calendars WHERE session_id={current_session_id}
  DB -> API : Guest session calendars
  API -> DB : UPDATE calendars SET user_id={new_user_id} WHERE session_id={current_session_id}
end

API -> API : Generate JWT token (user_id, role, exp)
API -> SPA : 200 OK + JWT token + user profile
SPA -> SPA : Store JWT in localStorage, update Pinia state
SPA -> User : Redirect to dashboard, show "Welcome {name}!"

@enduml
~~~

**Error Scenarios:**

- **OAuth Provider Unavailable**: API returns 503 Service Unavailable, SPA shows "Google login temporarily unavailable"
- **Email Already Exists (Different Provider)**: API merges accounts or prompts user to link accounts (Phase 2 feature)
- **Session Conversion Failure**: Calendars remain in session, API logs error, user can manually save after login

<!-- anchor: flow-place-order -->
##### Flow 2: Place Order for Printed Calendar

**Description:**

This flow demonstrates the complete e-commerce workflow from cart checkout to payment processing to asynchronous job creation for order confirmation emails.

**Sequence Diagram:**

~~~plantuml
@startuml
actor User
participant "Vue SPA" as SPA
participant "Quarkus API" as API
participant "Order Service" as OrderSvc
participant "Stripe Client" as Stripe
participant "Job Manager" as JobMgr
database "PostgreSQL" as DB
participant "Vert.x EventBus" as EventBus
queue "Email Job" as EmailJob

User -> SPA : Reviews cart, clicks "Checkout"
SPA -> API : GraphQL Mutation: placeOrder(input)
note right of SPA
  input = {
    calendarId: "123",
    productType: STANDARD,
    quantity: 1,
    shippingAddress: {...}
  }
end note

API -> OrderSvc : placeOrder(input, currentUser)

OrderSvc -> DB : BEGIN TRANSACTION
OrderSvc -> DB : INSERT INTO orders (user_id, status=PENDING, subtotal, tax, shipping_cost, total)
DB -> OrderSvc : order_id

OrderSvc -> DB : INSERT INTO order_items (order_id, calendar_id, product_type, quantity, unit_price)

OrderSvc -> Stripe : Create Checkout Session
note right of OrderSvc
  line_items: [{ name: "Custom Calendar 2025", amount: 2999 }]
  success_url: /orders/{order_id}/success
  cancel_url: /orders/{order_id}/cancel
end note

Stripe -> OrderSvc : Checkout Session (id, url)

OrderSvc -> DB : UPDATE orders SET stripe_checkout_session_id={session_id}
OrderSvc -> DB : COMMIT TRANSACTION

OrderSvc -> API : Return Order with checkout_url
API -> SPA : 200 OK + Order data
SPA -> User : Redirect to Stripe Checkout

User -> Stripe : Enters payment info, confirms
Stripe -> Stripe : Process payment
Stripe -> API : Webhook: checkout.session.completed
note right of Stripe
  POST /api/webhooks/stripe
  Signature: {stripe_signature}
end note

API -> Stripe : Validate webhook signature
API -> OrderSvc : handlePaymentSuccess(checkoutSessionId)

OrderSvc -> DB : BEGIN TRANSACTION
OrderSvc -> DB : SELECT order WHERE stripe_checkout_session_id={session_id}
DB -> OrderSvc : Order data

OrderSvc -> DB : INSERT INTO payments (order_id, stripe_payment_intent_id, amount, status=SUCCEEDED)
OrderSvc -> DB : UPDATE orders SET status=PAID, updated_at=NOW()

OrderSvc -> JobMgr : enqueueEmailJob(type=ORDER_CONFIRMATION, orderId)
JobMgr -> DB : INSERT INTO delayed_jobs (job_type, payload, priority=HIGH, run_at=NOW())
DB -> JobMgr : job_id

JobMgr -> EventBus : publish("jobs.new", job_id)
OrderSvc -> DB : COMMIT TRANSACTION

OrderSvc -> API : Payment processed successfully
API -> Stripe : 200 OK (webhook acknowledged)

EventBus -> EmailJob : Job worker receives event
EmailJob -> DB : SELECT job, lock row
EmailJob -> DB : SELECT order with user info
EmailJob -> EmailJob : Compose order confirmation email
EmailJob -> "Email Service" : Send via SMTP
EmailJob -> DB : UPDATE delayed_jobs SET status=COMPLETED

User -> SPA : Returns from Stripe, polls order status
SPA -> API : GraphQL Query: order(id)
API -> DB : SELECT order
DB -> API : Order (status=PAID)
API -> SPA : Order data
SPA -> User : Show "Order confirmed! Check your email."

@enduml
~~~

**Key Design Points:**

1. **Two-Phase Payment**: Order created in PENDING state, updated to PAID after webhook confirmation (handles race conditions)
2. **Webhook Signature Validation**: Prevents fraudulent payment confirmations
3. **Transactional Integrity**: Order and payment records created atomically within database transaction
4. **Asynchronous Email**: Email sending offloaded to job queue to prevent SMTP latency from blocking webhook response
5. **Idempotent Webhooks**: Stripe may retry webhooks; order service checks if payment already processed (via `stripe_payment_intent_id` uniqueness)

<!-- anchor: flow-pdf-generation -->
##### Flow 3: Asynchronous PDF Generation

**Description:**

This flow shows how a user's request to generate a high-resolution PDF is handled asynchronously, with job status polling and final download via presigned URL.

**Sequence Diagram:**

~~~plantuml
@startuml
actor User
participant "Vue SPA" as SPA
participant "Quarkus API" as API
participant "PDF Service" as PDFSvc
participant "Job Manager" as JobMgr
database "PostgreSQL" as DB
participant "Vert.x EventBus" as EventBus
participant "Job Worker Pod" as Worker
participant "Batik Renderer" as Batik
participant "R2 Client" as R2
participant "Cloudflare R2" as R2Storage

User -> SPA : Clicks "Download PDF"
SPA -> API : GraphQL Mutation: generatePdf(calendarId, watermark=false)

API -> PDFSvc : generatePdf(calendarId, watermark, currentUser)

PDFSvc -> DB : SELECT calendar WHERE id={calendarId} AND user_id={currentUser.id}
alt Calendar not found or unauthorized
  DB -> PDFSvc : Empty result
  PDFSvc -> API : Throw UnauthorizedException
  API -> SPA : 403 Forbidden
  SPA -> User : Show "Calendar not found"
else Calendar found
  DB -> PDFSvc : Calendar data

  PDFSvc -> JobMgr : enqueueJob(type=PDF_GENERATION, payload={calendarId, watermark, userId})
  JobMgr -> DB : INSERT INTO delayed_jobs (job_type='PdfGenerationJob', payload, priority=10, run_at=NOW())
  DB -> JobMgr : job_id=456

  JobMgr -> EventBus : publish("jobs.new", job_id=456)

  JobMgr -> PDFSvc : Job created (id=456, status=PENDING)
  PDFSvc -> API : PdfJob(id=456, status=PENDING, progress=0%)
  API -> SPA : 200 OK + PdfJob data
  SPA -> User : Show progress bar "Generating PDF... 0%"
end

' Asynchronous processing
EventBus -> Worker : Consume event (job_id=456)
Worker -> DB : SELECT job WHERE id=456 FOR UPDATE SKIP LOCKED
DB -> Worker : Job data (status=PENDING)

Worker -> DB : UPDATE delayed_jobs SET status=IN_PROGRESS, locked_at=NOW(), locked_by={pod_name}
Worker -> DB : COMMIT

Worker -> DB : SELECT calendar with events, config
DB -> Worker : Full calendar data

Worker -> Batik : renderCalendarToPdf(calendar, watermark)
note right of Batik
  - Generate SVG from calendar config
  - Apply astronomical overlays (moon phases)
  - Embed events with emojis
  - Add watermark if free tier
  - Convert SVG to PDF (36" x 23" @ 300 DPI)
end note

Batik -> Worker : PDF byte array (5-10 MB)

Worker -> R2 : uploadPdf(calendarId, pdfBytes)
R2 -> R2Storage : PUT /calendars/{user_id}/{calendar_id}.pdf
R2Storage -> R2 : Object URL

Worker -> DB : BEGIN TRANSACTION
Worker -> DB : UPDATE calendars SET pdf_url={r2_url}, updated_at=NOW()
Worker -> DB : UPDATE delayed_jobs SET status=COMPLETED, completed_at=NOW()
Worker -> DB : COMMIT

' User polling
SPA -> API : GraphQL Query: pdfJob(id=456) [polls every 2 seconds]
API -> DB : SELECT job WHERE id=456
DB -> API : Job (status=IN_PROGRESS, progress=50%)
API -> SPA : PdfJob(status=IN_PROGRESS, progress=50%)
SPA -> User : Update progress bar "Generating PDF... 50%"

SPA -> API : GraphQL Query: pdfJob(id=456) [after 15 seconds]
API -> DB : SELECT job WHERE id=456
DB -> API : Job (status=COMPLETED)
API -> DB : SELECT calendar.pdf_url
DB -> API : Calendar with pdf_url
API -> SPA : PdfJob(status=COMPLETED, downloadUrl={presigned_url})
SPA -> User : Show "Download ready!" button

User -> SPA : Clicks "Download"
SPA -> R2Storage : GET {presigned_url}
R2Storage -> User : PDF file (downloads to browser)

@enduml
~~~

**Failure Handling:**

- **Worker Crashes Mid-Job**: `locked_at` timeout (5 minutes) causes job to be reclaimed by another worker
- **Batik Rendering Failure**: Worker catches exception, updates job status=FAILED, increments `attempts`, schedules retry in 5 minutes (exponential backoff)
- **R2 Upload Failure**: Retryable error; worker retries 3 times before marking job as failed
- **Max Retries Exceeded**: Admin notified via monitoring alert, manual investigation required

**Performance Optimization:**

- **Caching**: If PDF already generated and calendar unchanged (`calendar.updated_at < pdf_generation_time`), return cached R2 URL immediately
- **Presigned URLs**: R2 presigned URLs (valid 1 hour) enable direct downloads without proxying through API server
- **Job Priority**: Paid users get priority=10, free tier gets priority=5 (workers process higher priority jobs first)

<!-- anchor: api-versioning-strategy -->
#### 3.7.4. API Versioning Strategy

**GraphQL Schema Evolution:**

GraphQL's introspection and strong typing enable **additive-only changes** without traditional versioning:

- **Adding Fields**: New fields can be added to types without breaking existing queries (clients only request fields they need)
- **Deprecating Fields**: Mark fields as `@deprecated(reason: "Use newField instead")`, remove after all clients migrated
- **Renaming Fields**: Add new field, deprecate old, support both temporarily
- **Breaking Changes**: Avoid in production; if unavoidable, create new mutation (e.g., `placeOrderV2`)

**REST API Versioning:**

For REST endpoints (webhooks, downloads), **URL-based versioning** is used:

- **Format**: `/api/v1/webhooks/stripe`, `/api/v2/webhooks/stripe`
- **Default**: Unversioned endpoints default to latest stable version
- **Deprecation Timeline**: Minimum 6 months notice before removing old version
- **Stripe Webhooks**: Stripe API version pinned in webhook endpoint configuration (prevents breaking changes from Stripe updates)

**Database Schema Versioning:**

MyBatis Migrations manages schema changes:

- **Up Scripts**: Apply schema changes (CREATE TABLE, ADD COLUMN, CREATE INDEX)
- **Down Scripts**: Rollback changes (for development; production uses forward-only migrations)
- **Deployment**: Migrations run automatically during Kubernetes pod startup (init container or startup probe)
- **Coordination**: Distributed lock (PostgreSQL advisory locks) ensures only one pod runs migrations

**Backward Compatibility Guarantees:**

- **GraphQL**: All changes backward-compatible for 6 months (deprecated fields supported)
- **REST**: Previous major version supported for 12 months after new version release
- **Database**: Additive changes only in production (new columns nullable or with defaults, no column drops until data migrated)
