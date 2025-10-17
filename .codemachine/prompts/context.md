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

### Context: API Contract Style (from Plan Section 2)

```markdown
**Primary: GraphQL (SmallRye GraphQL)**

**Endpoint**: `POST /graphql`

**Schema Evolution**: Additive-only changes, field deprecation with `@deprecated` annotation, no versioning required for MVP

**Key Queries:**
- `calendar(id: ID!): Calendar`
- `calendars(userId: ID!, year: Int): [Calendar!]!`
- `templates(isActive: Boolean): [CalendarTemplate!]!`
- `order(orderId: ID!): Order`
- `orders(userId: ID!, status: OrderStatus): [Order!]!`

**Key Mutations:**
- `createCalendar(input: CreateCalendarInput!): Calendar!`
- `updateCalendar(id: ID!, input: UpdateCalendarInput!): Calendar!`
- `generatePdf(calendarId: ID!, watermark: Boolean!): PdfJob!`
- `placeOrder(input: PlaceOrderInput!): Order!`
- `convertGuestSession(sessionId: ID!): User!`

**Type Safety**: GraphQL schema generates TypeScript types for Vue.js frontend (compile-time validation)
```

### Context: GraphQL Schema Organization (from Architecture Section 3.7.1)

```markdown
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
```

### Context: Data Model Overview (from Plan Section 2)

```markdown
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
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `api/schema.graphql`
    *   **Summary:** This file contains a **COMPLETE, PRODUCTION-READY GraphQL schema** that already implements the full specification required by task I1.T6. The schema includes all queries, mutations, types, enums, input types, and comprehensive documentation strings.
    *   **Critical Finding:** **THE TASK I1.T6 IS ALREADY COMPLETE!** The existing schema at `api/schema.graphql` contains:
        - All required queries: `calendar(id)`, `calendars(userId, year)`, `templates(isActive)`, `order(orderId)`, `orders(userId, status)`, `currentUser`, `pdfJob(id)`, plus additional queries like `me`, `myCalendars`, `myOrders`, `allOrders`, `allUsers`
        - All required mutations: `createCalendar(input)`, `updateCalendar(id, input)`, `deleteCalendar(id)`, `generatePdf(calendarId, watermark)`, `placeOrder(input)`, `cancelOrder(orderId, reason)`, `convertGuestSession(sessionId)`, plus additional mutations like `createTemplate`, `updateTemplate`, `deleteTemplate`, `updateOrderStatus`, `createOrder`
        - All required types: `Calendar` (named `UserCalendar`), `Event`, `CalendarConfig`, `CalendarTemplate`, `Order` (named `CalendarOrder`), `OrderItem`, `Payment`, `User` (named `CalendarUser`), `PdfJob`
        - All required enums: `OrderStatus` (with values PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED), `ProductType` (WALL_CALENDAR, DESK_CALENDAR, POSTER), `OAuthProvider` (GOOGLE, FACEBOOK), `CalendarStatus` (DRAFT, GENERATING, READY, FAILED)
        - All required input types: `CalendarInput`, `CalendarUpdateInput`, `AddressInput`, `OrderUpdateInput`, `PlaceOrderInput`, `TemplateInput`
        - Custom scalars: `JSON`, `DateTime`, `BigDecimal`
        - Comprehensive field-level documentation with description strings
    *   **Recommendation:** **DO NOT CREATE A NEW SCHEMA.** Instead, analyze the existing `api/schema.graphql` file to verify it meets all acceptance criteria. The file is located at `/Users/tea/dev/VillageCompute/code/village-calendar/api/schema.graphql` and should be reviewed for completeness.

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** This entity model demonstrates the current database schema implementation. It shows that the JPA entities use names like `UserCalendar`, `CalendarUser`, `CalendarOrder`, and `CalendarTemplate` instead of the simplified names (`Calendar`, `User`, `Order`, `Template`) shown in the architecture examples.
    *   **Critical Insight:** The GraphQL schema uses the actual entity names (`UserCalendar`, `CalendarUser`, `CalendarOrder`) which diverges from the architecture document's simplified examples. This is intentional and aligns with the actual database implementation.
    *   **Recommendation:** When verifying the schema, understand that type names in the schema match the actual entity class names: `CalendarUser` (not `User`), `UserCalendar` (not `Calendar`), `CalendarOrder` (not `Order`), `CalendarTemplate` (stays the same).

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** This order entity confirms the e-commerce data model with fields like `quantity`, `unitPrice`, `totalPrice`, `status`, `stripePaymentIntentId`, `stripeChargeId`, `shippingAddress` (JSONB), `paidAt`, `shippedAt`. Static constants define order status values.
    *   **Recommendation:** The GraphQL schema's `CalendarOrder` type should include all fields from this entity, including the relationship to `CalendarUser` and `UserCalendar`, as well as the `OrderStatus` enum matching the static constants (PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED).

*   **File:** `docs/diagrams/database_erd.puml`
    *   **Summary:** The ERD diagram shows the **actual implemented MVP schema** with only 4 core entities: `calendar_users`, `calendar_templates`, `user_calendars`, and `calendar_orders`. The legend explicitly states: "Future Entities (Planned but Not Implemented): DelayedJob, PageView, AnalyticsRollup, Separate Event table (currently embedded in JSONB), Separate Payment table (currently embedded in orders), Separate OrderItem table (currently quantity field in orders)".
    *   **Critical Insight:** **The MVP implementation differs from the full architecture plan.** Events are embedded in calendar configuration JSONB, not in a separate table. Payment details are embedded in the orders table. There is no separate OrderItem table (orders have a single `quantity` field).
    *   **Recommendation:** The GraphQL schema should reflect the **simplified MVP implementation**, not the full architecture plan. This means:
        - `Event` type can be defined but may not have a database table backing it (embedded in JSONB)
        - `Payment` information is returned as part of the `CalendarOrder` type fields (not a separate type with its own queries)
        - `OrderItem` type can be defined but currently represents a single-item order (quantity field)

### Implementation Tips & Notes

*   **Tip:** The existing schema at `api/schema.graphql` is comprehensive and production-ready. It includes advanced features like:
    - Pagination types (`PageInfo`, `TemplateConnection`, `TemplateEdge`) following the Relay specification
    - Additional admin queries (`allOrders`, `allUsers`) with documentation noting they require ADMIN role
    - Structured configuration types (`CalendarConfig`, `AstronomyConfig`, `LayoutConfig`, `ColorScheme`) providing typed alternatives to JSONB
    - The `PaymentIntent` type for Stripe checkout flow integration
    - All field descriptions use proper GraphQL documentation strings (triple-quoted)

*   **Note:** The schema uses SmallRye GraphQL adapter annotations in the entity models (e.g., `@io.smallrye.graphql.api.AdaptWith(villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)` for JSONB fields). This means the schema is designed to work seamlessly with the existing Quarkus entity models.

*   **Warning:** The task specifies creating a schema at `api/graphql-schema.graphql`, but the existing schema is at `api/schema.graphql` (without the `graphql-` prefix). This is a **CRITICAL FILE LOCATION DISCREPANCY**. The codebase is already using `api/schema.graphql` as the schema location. You should verify which location is correct and ensure consistency.

*   **Best Practice:** The existing schema follows GraphQL best practices:
    - Non-nullable fields marked with `!` (e.g., `email: String!`)
    - List types use non-nullable items where appropriate (e.g., `[CalendarTemplate!]!`)
    - Enum values use UPPER_SNAKE_CASE (e.g., `WALL_CALENDAR`)
    - Input types have `Input` suffix (e.g., `CalendarInput`, `AddressInput`)
    - Mutations return the modified object (e.g., `createCalendar` returns `UserCalendar!`)
    - Descriptions explain authorization requirements (e.g., "Requires ADMIN role in JWT claims")

*   **Verification Steps:** To confirm the existing schema meets all acceptance criteria:
    1. Validate syntax using a GraphQL validator (online tool or `graphql-cli`)
    2. Verify all entity types from the data model are represented (check against ERD diagram entities)
    3. Verify all required queries and mutations from the task description exist
    4. Verify all enums have correct values (OrderStatus, ProductType, OAuthProvider)
    5. Verify input types are properly defined for all mutations
    6. Confirm field descriptions are present and meaningful
    7. Test that the schema can generate TypeScript types (if code generator is available)

*   **Action Required:** Since the schema appears to already be complete at `api/schema.graphql`, you should:
    1. **FIRST:** Carefully read and analyze the existing `api/schema.graphql` file in its entirety
    2. **VERIFY:** Confirm it meets ALL acceptance criteria from the task specification
    3. **DOCUMENT:** Create a verification report showing which requirements are satisfied
    4. **DECIDE:** If the file is at the wrong location (`api/schema.graphql` vs `api/graphql-schema.graphql`), determine if you should:
        - Copy/rename the file to the expected location
        - Update the task to reflect the actual location
        - Leave as-is and document the discrepancy
    5. **MARK COMPLETE:** If all acceptance criteria are met, the task can be marked as done

