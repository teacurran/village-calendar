# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T1",
  "iteration_id": "I2",
  "iteration_goal": "Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows",
  "description": "Create three PlantUML sequence diagrams documenting critical interaction flows: (1) OAuth Login Flow - showing user authentication via Google/Facebook/Apple, token exchange, user creation/retrieval, JWT token generation; (2) Order Placement Flow - showing cart checkout, Stripe Checkout session creation, payment webhook processing, order status updates, email job enqueueing; (3) PDF Generation Flow - showing async job workflow, calendar data retrieval, Batik rendering, R2 upload, status polling. Each diagram must include all actors (User, Vue SPA, Quarkus API, external services, database), show message sequences with proper timing, include error scenarios (alt/opt blocks), and follow PlantUML sequence diagram best practices.",
  "agent_type_hint": "DiagrammingAgent",
  "inputs": "Sequence diagram descriptions from Plan Section 3.7.3 \"Key Interaction Flows\", OAuth authentication architecture from Plan Section 3.8.1, Order placement workflow from Plan Section 3.7.3 Flow 2, PDF generation workflow from Plan Section 3.7.3 Flow 3",
  "target_files": [
    "docs/diagrams/sequence_oauth_login.puml",
    "docs/diagrams/sequence_order_placement.puml",
    "docs/diagrams/sequence_pdf_generation.puml"
  ],
  "input_files": [],
  "deliverables": "Three PlantUML sequence diagram files rendering correctly, PNG exports of all diagrams for documentation, Diagrams accurately reflect workflows described in architecture plan, Error handling scenarios included (alt blocks for failures)",
  "acceptance_criteria": "All three PlantUML files validate and render without errors, OAuth login diagram shows complete flow from user click to JWT token response, Order placement diagram includes Stripe webhook validation and async email job, PDF generation diagram shows job queue polling, worker processing, R2 upload, Timing and activation bars clearly show which components are active, Error scenarios documented (e.g., OAuth provider down, Stripe API failure)",
  "dependencies": [
    "I1.T1"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: flow-user-login (from 04_Behavior_and_Communication.md)

```markdown
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
```

### Context: flow-place-order (from 04_Behavior_and_Communication.md)

```markdown
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
```

### Context: flow-pdf-generation (from 04_Behavior_and_Communication.md)

```markdown
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
```

### Context: communication-patterns (from 01_Plan_Overview_and_Setup.md)

```markdown
### Communication Patterns

**Synchronous Request/Response (GraphQL/REST over HTTPS):**
- Client ↔ API server for real-time operations (calendar CRUD, order placement, authentication)
- Typical latency: <500ms (p95)
- Transactional consistency (database commit before response)

**Asynchronous Job Processing (DelayedJob + Vert.x EventBus):**
- Long-running operations: PDF generation (10-30s), email sending, analytics rollups
- Workflow:
  1. Client → GraphQL mutation → API creates DelayedJob record (status: PENDING)
  2. API publishes event to Vert.x EventBus → returns job ID to client immediately
  3. Job Worker pod consumes event → claims job (locks database row)
  4. Worker executes job logic → updates status (COMPLETED/FAILED)
  5. Client polls job status via GraphQL or receives email notification

**External Service Communication:**
- **Stripe API**: HTTPS/REST for checkout session creation, webhook signature validation
- **OAuth Providers**: HTTPS/OIDC for user authentication, token validation
- **Email Service**: SMTP/TLS for transactional email delivery
- **Cloudflare R2**: S3 API/HTTPS for PDF upload, presigned URL generation

**Key Interaction Flows** (detailed sequence diagrams in Iteration 2):
1. User Login via OAuth (Google/Facebook/Apple)
2. Place Order for Printed Calendar (Stripe Checkout, webhook processing)
3. Asynchronous PDF Generation (job queue, Batik rendering, R2 upload)
```

### Context: api-style (from 04_Behavior_and_Communication.md)

```markdown
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

**Secondary API: REST (JAX-RS)**

REST endpoints are used for specific use cases where GraphQL is not optimal:

1. **Webhooks**: Stripe payment webhooks require predictable REST endpoints (`POST /api/webhooks/stripe`)
2. **Health Checks**: Kubernetes probes (`GET /q/health/live`, `GET /q/health/ready`)
3. **File Downloads**: Direct PDF downloads (`GET /api/downloads/pdf/{calendarId}?token={token}`)
4. **Metrics**: Prometheus scraping (`GET /q/metrics`)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `docs/diagrams/order-flow.puml`
    *   **Summary:** This is an EXISTING comprehensive PlantUML sequence diagram showing the complete order placement flow from OAuth authentication through calendar generation, order placement, Stripe payment, webhook processing, and background email delivery.
    *   **Recommendation:** You MUST examine this file thoroughly as it provides a working reference implementation for PlantUML sequence diagram syntax in this project. It demonstrates the expected level of detail, actor naming conventions, note placement, alt/else blocks for error handling, and transaction boundaries. Use this as your primary template for diagram structure and style.

*   **File:** `docs/diagrams/component_diagram.puml`
    *   **Summary:** This file contains the C4 component architecture diagram for the Quarkus API, showing the internal component structure, service layers, and integration points.
    *   **Recommendation:** Reference this diagram to understand the exact component names, service names, and integration component names (OAuth Client, Stripe Client, R2 Client, etc.) that should be used as actors/participants in your sequence diagrams. Use consistent naming with this diagram.

*   **File:** `api/schema.graphql`
    *   **Summary:** This is the complete GraphQL schema definition showing all types, queries, mutations, and their relationships. It includes comprehensive documentation comments and type definitions.
    *   **Recommendation:** You SHOULD reference this schema to understand the exact GraphQL mutation names, input types, and response types that appear in the sequence diagrams. The order placement and PDF generation flows involve GraphQL mutations like `createOrder`, `generatePdf`, and queries like `pdfJob`, `order`.

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/CalendarGraphQL.java`
    *   **Summary:** This is an existing GraphQL resolver implementation showing the patterns used in the codebase: @GraphQLApi annotation, @Query/@Mutation annotations, JWT injection, AuthenticationService usage, logging patterns, and error handling.
    *   **Recommendation:** While not directly needed for sequence diagrams, this file demonstrates the actual implementation flow that your diagrams should accurately represent. Note the authentication checks, transaction boundaries (@Transactional), and error handling patterns that should appear in your sequence diagrams' alt/opt blocks.

### Implementation Tips & Notes

*   **Tip:** The existing `order-flow.puml` diagram already covers most of the Order Placement flow requirements. You MUST create a NEW diagram named `sequence_order_placement.puml` based on the architectural specification provided in the context, which may have slight differences from the existing prototype diagram. Do NOT simply copy the existing file - synthesize the requirements from the architecture documents.

*   **Note:** All three sequence diagrams must follow PlantUML syntax and best practices as demonstrated in the existing `order-flow.puml` file. Key patterns to follow:
    - Use consistent participant naming (e.g., "Vue SPA" as SPA, "Quarkus API" as API, "PostgreSQL" as DB)
    - Include descriptive notes using `note right of` or `note left of` to explain complex logic
    - Use `alt`/`else`/`end` blocks for conditional flows and error scenarios
    - Use activation bars to show when components are actively processing
    - Group related operations visually using spacing and notes
    - Include transaction boundaries with explicit BEGIN/COMMIT annotations

*   **Tip:** For the OAuth login flow, you MUST show the complete flow including:
    - Guest session calendar conversion logic (this is unique to this architecture)
    - JWT token generation with specific claims (user_id, role, exp)
    - The two database paths: existing user (UPDATE last_login) vs new user (INSERT + session conversion)
    - Error scenarios for OAuth provider failures and session conversion failures

*   **Tip:** For the PDF generation flow, focus on demonstrating:
    - The asynchronous nature of the job processing (immediate return of job ID, then background processing)
    - The polling mechanism (SPA polls pdfJob query every 2 seconds)
    - The job state transitions (PENDING → IN_PROGRESS → COMPLETED)
    - The worker pod claiming jobs using `FOR UPDATE SKIP LOCKED` pattern
    - R2 presigned URL generation for secure downloads

*   **Warning:** The diagrams MUST accurately reflect the architecture as specified in the `04_Behavior_and_Communication.md` document, NOT the existing prototype `order-flow.puml`. There may be differences in service names, data flow, or error handling that must be incorporated from the official architecture specification.

*   **Note:** After creating the `.puml` files, you should generate PNG exports of the diagrams for documentation purposes. The target files list includes PNG generation as a deliverable. You can use PlantUML CLI or online rendering to create the PNGs.

*   **Tip:** For error scenarios, ensure you include at least these critical failure cases in each diagram:
    - **OAuth Login**: OAuth provider unavailable (503 error), email already exists with different provider
    - **Order Placement**: Payment declined, webhook signature validation failure, order not found
    - **PDF Generation**: Calendar not found/unauthorized, worker crash mid-job, Batik rendering failure, R2 upload failure

*   **Best Practice:** Each sequence diagram should be self-contained and readable without external context. Include a brief title and description comment at the top of each `.puml` file explaining the workflow being documented.
