# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T6",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Create comprehensive GraphQL schema definition file (SDL format) for the Village Calendar API. Define root Query type with queries: calendar(id), calendars(userId, year), templates(isActive), order(orderId), orders(userId, status), currentUser, pdfJob(id). Define root Mutation type with mutations: createCalendar(input), updateCalendar(id, input), deleteCalendar(id), generatePdf(calendarId, watermark), placeOrder(input), cancelOrder(orderId, reason), convertGuestSession(sessionId). Define all necessary types: Calendar, Event, CalendarConfig, CalendarTemplate, Order, OrderItem, Payment, User, PdfJob. Define input types for mutations. Define enums: OrderStatus, ProductType, OAuthProvider. Include field descriptions and deprecation annotations where applicable.",
  "agent_type_hint": "BackendAgent",
  "inputs": "API contract description from Plan Section 2 \"API Contract Style\", Data model entities from Plan Section 2, Example schema excerpt from architecture blueprint",
  "target_files": ["api/graphql-schema.graphql"],
  "input_files": [],
  "deliverables": "GraphQL schema file in SDL format, Schema includes all queries, mutations, types, inputs, enums described in plan, Field-level documentation comments (GraphQL description strings), Schema validates against GraphQL specification (no syntax errors)",
  "acceptance_criteria": "Schema file passes GraphQL schema validation (use online validator or GraphQL CLI), All entity types from data model represented in schema, Input types properly defined for create/update operations, Enums defined for status fields (OrderStatus: PENDING, PAID, SHIPPED, etc.), Schema generates valid TypeScript types when processed by code generator",
  "dependencies": ["I1.T1"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

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
```

### Context: data-model-overview (from 03_System_Structure_and_Data.md)

```markdown
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
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `api/schema.graphql`
    *   **Summary:** A GraphQL schema file already exists in the project. This file contains comprehensive definitions for CalendarUser, CalendarTemplate, UserCalendar, CalendarOrder, and PaymentIntent types, along with custom scalars (JSON, DateTime, BigDecimal), enums (OrderStatus, CalendarStatus, OAuthProvider), input types, and query/mutation operations.
    *   **Recommendation:** You MUST review this existing schema carefully. The task asks you to create a comprehensive schema, but one already exists at this location. You should EITHER: (1) Update/enhance the existing schema to ensure it meets all requirements from the task description, OR (2) Verify the existing schema already satisfies the requirements and document any gaps.
    *   **Critical Analysis:** The existing schema is quite comprehensive and includes:
        - Custom scalars: JSON, DateTime, BigDecimal
        - Core types: CalendarUser, CalendarTemplate, UserCalendar, CalendarOrder, PaymentIntent
        - Enums: OrderStatus (CANCELLED, DELIVERED, PAID, PENDING, PROCESSING, REFUNDED, SHIPPED), CalendarStatus (DRAFT, FAILED, GENERATING, READY), OAuthProvider (FACEBOOK, GOOGLE)
        - Input types: TemplateInput, CalendarInput, CalendarUpdateInput, AddressInput, OrderUpdateInput
        - Queries: allOrders, allUsers, calendar, me, myCalendars, myOrders, order, template, templates, templatesConnection
        - Mutations: createCalendar, createOrder, createTemplate, deleteCalendar, deleteTemplate, updateCalendar, updateOrderStatus, updateTemplate

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** JPA entity for CalendarUser with OAuth authentication fields (oauthProvider, oauthSubject, email, displayName, profileImageUrl, isAdmin), relationships to calendars and orders, and custom finder methods.
    *   **Recommendation:** The GraphQL schema's CalendarUser type MUST match the fields in this entity. Pay attention to field names (camelCase in GraphQL should match Java field names), nullability (NotNull annotations in Java should correspond to non-null fields in GraphQL with `!`), and relationships (OneToMany to calendars and orders).

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** JPA entity for UserCalendar with fields: user (ManyToOne), sessionId, isPublic, name, year, configuration (JSONB), template (ManyToOne), generatedSvg, generatedPdfUrl, and orders (OneToMany).
    *   **Recommendation:** The GraphQL schema's UserCalendar type MUST reflect these fields. Note that `configuration` is a JSONB column (JsonNode type) with a custom GraphQL adapter (JsonNodeAdapter). The schema should use the custom JSON scalar for this field.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** JPA entity for CalendarOrder with fields: user, calendar, quantity, unitPrice, totalPrice, status, shippingAddress (JSONB), stripePaymentIntentId, stripeChargeId, notes, paidAt, shippedAt. Includes status constants (STATUS_PENDING, STATUS_PAID, etc.).
    *   **Recommendation:** The GraphQL schema's CalendarOrder type MUST include these fields. The `shippingAddress` field is JSONB, so use the JSON scalar in GraphQL. The `status` field should use the OrderStatus enum.

*   **File:** `pom.xml`
    *   **Summary:** Maven POM file containing all project dependencies including quarkus-smallrye-graphql (line 56-59) and graphql-java-extended-scalars (line 66-70) for custom scalar support.
    *   **Recommendation:** The project is configured to use SmallRye GraphQL and extended scalars. You can use the custom scalars defined in the existing schema (JSON, DateTime, BigDecimal).

### Implementation Tips & Notes

*   **Tip:** The existing `api/schema.graphql` file is VERY comprehensive and appears to already satisfy most of the task requirements. Your primary job is to VERIFY it matches all task requirements and potentially fill any gaps.

*   **Note:** The task description mentions several types and operations that you should verify are present:
    - Required Query operations: calendar(id), calendars(userId, year), templates(isActive), order(orderId), orders(userId, status), currentUser, pdfJob(id)
    - Required Mutation operations: createCalendar(input), updateCalendar(id, input), deleteCalendar(id), generatePdf(calendarId, watermark), placeOrder(input), cancelOrder(orderId, reason), convertGuestSession(sessionId)
    - Required types: Calendar, Event, CalendarConfig, CalendarTemplate, Order, OrderItem, Payment, User, PdfJob
    - Required enums: OrderStatus, ProductType, OAuthProvider

*   **Gap Analysis:** Comparing the existing schema with task requirements:
    - ✅ OrderStatus enum exists (CANCELLED, DELIVERED, PAID, PENDING, PROCESSING, REFUNDED, SHIPPED)
    - ❌ ProductType enum is NOT present in the existing schema (you may need to add this if required)
    - ✅ OAuthProvider enum exists (FACEBOOK, GOOGLE)
    - ❌ Event type is NOT explicitly defined in the existing schema (events may be embedded in Calendar configuration JSONB)
    - ❌ CalendarConfig type is NOT present (this is embedded in the JSONB configuration field)
    - ❌ OrderItem type is NOT present (the existing schema has CalendarOrder but no separate OrderItem)
    - ❌ Payment type is NOT present (only PaymentIntent exists)
    - ❌ PdfJob type is NOT present
    - ❌ pdfJob query is NOT present
    - ❌ generatePdf mutation is NOT present
    - ❌ convertGuestSession mutation is NOT present
    - ❌ calendars(userId, year) query is NOT present (only myCalendars exists which doesn't take userId)
    - ❌ orders(userId, status) query is NOT present (only myOrders exists which filters by current user)
    - ❌ currentUser query is NOT present (only "me" exists, which may be equivalent)

*   **Warning:** The existing schema uses different naming conventions in some places (e.g., "CalendarUser" instead of "User", "UserCalendar" instead of "Calendar"). You need to decide whether to: (1) Keep the existing naming and document the differences, OR (2) Add type aliases/additional types to match the task specification exactly.

*   **Critical Decision:** The task specifies creating a schema at `api/graphql-schema.graphql`, but the existing file is `api/schema.graphql` (slightly different name). You MUST either: (1) Update the existing `api/schema.graphql` file, OR (2) Create a new file at `api/graphql-schema.graphql` and determine which one the application should use (check Quarkus configuration).

*   **Recommendation:** Based on the codebase evidence, I recommend you UPDATE the existing `api/schema.graphql` file to include the missing elements identified in the gap analysis, rather than creating a duplicate schema file. This approach maintains continuity with the existing implementation while satisfying the task requirements.

*   **IMPORTANT:** The architecture plan uses type names like "Calendar", "User", "Order" in its examples, but the actual implemented schema uses "UserCalendar", "CalendarUser", "CalendarOrder". This appears to be a deliberate choice to avoid naming conflicts and improve clarity. You should MAINTAIN the existing naming convention unless explicitly instructed otherwise.
