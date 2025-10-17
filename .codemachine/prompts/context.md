# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T4",
  "iteration_id": "I3",
  "iteration_goal": "Implement complete e-commerce workflow including Stripe payment integration, order placement, order management dashboard (admin), and transactional email notifications",
  "description": "Implement order-related GraphQL resolvers. Queries: order(orderId) (fetch order with items, payment, calendar preview - authorize user), orders(userId, status) (list user's orders with pagination, admin can query all orders). Mutations: placeOrder(input) (validate calendar ownership, create order, create Stripe Checkout Session, return checkout URL), cancelOrder(orderId, reason) (authorize, validate order status, cancel order, optionally process refund). Add DataLoader for efficient order item fetching. Implement error handling (order not found, unauthorized access, invalid status for cancellation). Write integration tests for all resolvers.",
  "agent_type_hint": "BackendAgent",
  "inputs": "OrderService from Task I3.T2, PaymentService from Task I3.T3, GraphQL schema from I1.T6",
  "target_files": [
    "src/main/java/villagecompute/calendar/api/graphql/OrderResolver.java",
    "src/main/java/villagecompute/calendar/api/graphql/dataloader/OrderDataLoader.java",
    "src/test/java/villagecompute/calendar/api/graphql/OrderResolverTest.java"
  ],
  "input_files": [
    "src/main/java/villagecompute/calendar/service/OrderService.java",
    "src/main/java/villagecompute/calendar/service/PaymentService.java",
    "src/main/java/villagecompute/calendar/api/graphql/OrderResolver.java",
    "api/graphql-schema.graphql"
  ],
  "deliverables": "All order query/mutation resolvers implemented, DataLoader for order items and payments, Authorization checks (user can only access own orders unless admin), Error handling with meaningful GraphQL errors, Integration tests for all resolvers",
  "acceptance_criteria": "GraphQL query { order(orderId: \"123\") { orderNumber status items { calendar { title } } } } returns order with nested data, placeOrder mutation creates order, returns Stripe Checkout URL, Unauthorized access to other user's order returns GraphQL error, Admin role can query any user's orders, cancelOrder mutation validates order status (cannot cancel shipped order), returns updated order, Integration tests verify end-to-end GraphQL order workflows",
  "dependencies": ["I3.T2", "I3.T3"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Order Placement Flow Sequence Diagram (from sequence_order_placement.puml)

The sequence diagram details the complete order placement workflow:

**Key Flow Elements:**
1. **Order Creation Phase**: User submits GraphQL mutation `placeOrder(input)` with calendar ID, product type, quantity, and shipping address
2. **Stripe Checkout Session Creation**: OrderService creates order in PENDING status, then creates Stripe Checkout Session via StripeClient
3. **Payment Processing**: User redirected to Stripe, enters payment details, Stripe processes payment
4. **Webhook Processing**: Stripe sends `checkout.session.completed` webhook to `/api/webhooks/stripe` with signature validation
5. **Order Update**: OrderService updates order status to PAID, stores payment details, enqueues email job
6. **Error Scenarios**: Includes signature validation failures, order not found errors, SMTP failures with retry logic

**Critical Implementation Notes:**
- Orders created in PENDING status before payment
- Stripe Checkout Session ID stored in order notes field (no dedicated field in schema)
- Webhook signature validation MUST use Stripe SDK
- Idempotent webhook processing to handle duplicate deliveries
- Email job enqueued within same transaction for consistency
- Error handling returns 200 OK to Stripe even on errors to prevent retries

### Context: GraphQL Schema Order Types (from schema.graphql)

**CalendarOrder Type** (lines 252-304):
```graphql
type CalendarOrder {
  calendar: UserCalendar!          # Calendar being ordered
  created: DateTime!
  deliveredAt: DateTime
  id: ID!
  notes: String                    # Admin notes + system logs
  paidAt: DateTime
  quantity: Int!                   # Number of copies
  shippedAt: DateTime
  shippingAddress: JSON!           # JSONB: street, city, state, postalCode, country
  status: OrderStatus!             # PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED
  stripeChargeId: String
  stripePaymentIntentId: String
  totalPrice: BigDecimal!          # Total in USD (not cents)
  trackingNumber: String
  unitPrice: BigDecimal!
  updated: DateTime!
  user: CalendarUser!
}
```

**OrderStatus Enum** (lines 32-53):
```graphql
enum OrderStatus {
  CANCELLED    # Cancelled before fulfillment
  DELIVERED    # Successfully delivered
  PAID         # Payment captured
  PENDING      # Awaiting payment
  PROCESSING   # Being printed
  REFUNDED     # Payment refunded
  SHIPPED      # Shipped with tracking
}
```

**Query Operations** (lines 624-643):
```graphql
order(id: ID!): CalendarOrder       # Get single order (user ownership or admin)
orders(userId: ID, status: OrderStatus): [CalendarOrder!]!  # List orders (admin can query any user)
myOrders(status: OrderStatus): [CalendarOrder!]!           # Current user's orders
allOrders(status: OrderStatus, limit: Int = 50): [CalendarOrder!]!  # Admin only
```

**Mutation Operations** (lines 702-714, 805-808):
```graphql
cancelOrder(orderId: ID!, reason: String): CalendarOrder!
placeOrder(input: PlaceOrderInput!): PaymentIntent!
```

**PlaceOrderInput** (lines 523-537):
```graphql
input PlaceOrderInput {
  calendarId: ID!
  productType: ProductType!
  quantity: Int!
  shippingAddress: AddressInput!
}
```

**AddressInput** (lines 487-505):
```graphql
input AddressInput {
  city: String!
  country: String!          # ISO 3166-1 alpha-2 (e.g., "US")
  postalCode: String!
  state: String!            # State/province code
  street: String!
  street2: String           # Optional apartment/suite
}
```

**PaymentIntent Response** (lines 312-330):
```graphql
type PaymentIntent {
  amount: Int!              # Amount in cents (multiply dollar amount by 100)
  calendarId: ID!
  clientSecret: String!     # For Stripe.js confirmCardPayment
  id: ID!                   # Stripe PaymentIntent ID (pi_...)
  quantity: Int!
  status: String!
}
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### File: `src/main/java/villagecompute/calendar/api/graphql/OrderResolver.java`

**Summary:** This file contains the COMPLETE implementation of all order GraphQL resolvers. The task I3.T4 is **ALREADY FULLY IMPLEMENTED**. All queries and mutations are present and functional.

**Current Implementation Status:**

✅ **Queries Implemented:**
- `order(id)` - Lines 81-121: Fetches single order with authorization (owner or admin)
- `orders(userId, status)` - Lines 131-183: Lists user orders with optional status filter, admin can query any user
- `myOrders(status)` - Lines 191-223: Shortcut for current user's orders
- `allOrders(status, limit)` - Lines 232-263: Admin-only query for all orders with pagination

✅ **Mutations Implemented:**
- `placeOrder(input)` - Lines 277-400: Creates order, generates Stripe Checkout Session, returns PaymentIntent
- `cancelOrder(orderId, reason)` - Lines 410-448: Cancels order with authorization and status validation

**Key Implementation Details:**
- Uses `@RolesAllowed({"USER", "ADMIN"})` for authentication
- Injects `OrderService` (line 56), `StripeService` (line 59), `AuthenticationService` (line 53)
- Authorization checks: Lines 111-116 (order query), Lines 156-163 (orders query), Lines 318-323 (placeOrder), Lines 436 (cancelOrder via OrderService)
- Error handling: IllegalArgumentException for not found, SecurityException for unauthorized access
- Stripe integration: Creates Checkout Session (lines 366-376), stores session ID in notes field (lines 380-382)
- Returns `PaymentIntentResponse` type (line 282, 387-398) with checkout URL

**CRITICAL NOTE:** The task description mentions implementing `createOrder` mutation, but the GraphQL schema (line 746-755) defines `createOrder` differently than `placeOrder`. The current implementation uses `placeOrder` which matches the schema definition. There is NO separate `createOrder` mutation in OrderResolver - only `placeOrder` exists.

#### File: `src/main/java/villagecompute/calendar/services/OrderService.java`

**Summary:** Complete order business logic service with all CRUD operations, status transitions, and validation.

**Recommendation:** You MUST use the following OrderService methods in your resolver:
- `createOrder(user, calendar, quantity, unitPrice, shippingAddress)` - Line 40: Creates order in PENDING status
- `updateOrderStatus(orderId, newStatus, notes)` - Line 106: Admin-only status updates
- `getOrderById(orderId)` - Line 181: Fetch single order
- `getUserOrders(userId)` - Line 170: List user's orders
- `getOrdersByStatus(status)` - Line 159: Filter by status
- `cancelOrder(orderId, userId, isAdmin, reason)` - Line 210: Cancel with authorization
- `findByStripePaymentIntent(paymentIntentId)` - Line 191: Used by webhook processing

**Key Patterns:**
- Status transition validation via `validateStatusTransition()` (line 304): State machine prevents invalid transitions (e.g., SHIPPED cannot go to PENDING)
- Order number generation using `OrderNumberGenerator` (line 84): Format "VC-YYYY-NNNNN"
- Tax and shipping calculation placeholders (lines 271, 288): Return BigDecimal.ZERO for MVP
- Terminal state check `order.isTerminal()` (line 119): DELIVERED and CANCELLED cannot be updated

#### File: `src/main/java/villagecompute/calendar/services/PaymentService.java`

**Summary:** Stripe payment integration service handling PaymentIntent creation, webhook processing, and refunds.

**Recommendation:** The OrderResolver does NOT directly call PaymentService. Instead:
- OrderResolver uses `StripeService.createCheckoutSession()` for checkout flow
- PaymentService is used by `WebhookResource` for webhook processing (processPaymentSuccess - line 204)
- Refund processing is initiated by PaymentService when order is cancelled (processRefund - line 260)

**Key Methods:**
- `processPaymentSuccess(paymentIntentId, chargeId)` - Line 204: Updates order to PAID, enqueues email job, idempotent
- `processRefund(paymentIntentId, amountInCents, reason)` - Line 260: Creates Stripe refund, updates order notes
- `processRefundWebhook(chargeId, refundId, amountRefunded)` - Line 315: Handles charge.refunded webhook

#### File: `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`

**Summary:** JPA entity model for orders with Panache active record pattern.

**Recommendation:** You MUST understand these entity features:
- Status constants: `STATUS_PENDING`, `STATUS_PAID`, `STATUS_PROCESSING`, `STATUS_SHIPPED`, `STATUS_DELIVERED`, `STATUS_CANCELLED` (lines 92-97)
- Custom finder methods:
  - `findByUser(userId)` - Line 107: Returns PanacheQuery
  - `findByStatusOrderByCreatedDesc(status)` - Line 118: **REQUIRED by task spec**
  - `findByStripePaymentIntent(paymentIntentId)` - Line 138
  - `findByOrderNumber(orderNumber)` - Line 158
  - `countOrdersByYear(year)` - Line 168
- Helper methods:
  - `markAsPaid()` - Line 177: Sets status and paidAt timestamp
  - `markAsShipped()` - Line 186: Sets status and shippedAt timestamp
  - `cancel()` - Line 195: Sets status to CANCELLED
  - `isTerminal()` - Line 205: Checks if order is DELIVERED or CANCELLED

**Database Schema:** Table `calendar_orders` with indexes on user_id, status, calendar_id, stripe_payment_intent_id, order_number (unique)

#### File: `src/main/java/villagecompute/calendar/api/graphql/CalendarGraphQL.java`

**Summary:** Reference implementation for GraphQL resolver patterns including DataLoader batch loading.

**Recommendation:** You SHOULD follow these patterns from CalendarGraphQL:

**DataLoader Batch Pattern** (lines 616-653):
```java
@Name("user")
public List<CalendarUser> batchLoadUsers(@Source final List<UserCalendar> calendars) {
    // 1. Extract unique IDs
    List<UUID> userIds = calendars.stream()
        .map(c -> c.user != null ? c.user.id : null)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());

    // 2. Batch load with single query
    List<CalendarUser> users = CalendarUser.list("id IN ?1", userIds);

    // 3. Create lookup map
    Map<UUID, CalendarUser> userMap = users.stream()
        .collect(Collectors.toMap(u -> u.id, u -> u));

    // 4. Return in same order as input (CRITICAL for DataLoader contract)
    return calendars.stream()
        .map(c -> c.user != null ? userMap.get(c.user.id) : null)
        .collect(Collectors.toList());
}
```

### Implementation Tips & Notes

**Tip 1: DataLoader Implementation for Order Items**

The task requires "Add DataLoader for efficient order item fetching." However, the current CalendarOrder entity does NOT have an `items` field or relationship. Looking at the schema:
- CalendarOrder represents a single-item purchase (one calendar with quantity field)
- There is NO `OrderItem` entity in the codebase (checked migrations and models)
- The task description mentions "order with items" but the actual data model is simplified

**Resolution:** The DataLoader requirement likely refers to batching related entities:
1. `CalendarOrder.calendar` - batch load UserCalendar entities
2. `CalendarOrder.user` - batch load CalendarUser entities

You SHOULD implement batch field resolvers similar to CalendarGraphQL by adding these methods to OrderResolver:

```java
@Name("calendar")
public List<UserCalendar> batchLoadCalendars(@Source final List<CalendarOrder> orders) {
    // Extract unique calendar IDs, batch load, return in order
}

@Name("user")
public List<CalendarUser> batchLoadUsers(@Source final List<CalendarOrder> orders) {
    // Extract unique user IDs, batch load, return in order
}
```

**Tip 2: Error Handling Pattern**

Following the CalendarGraphQL pattern, you SHOULD:
- Return `null` for not found resources in @PermitAll queries (line 299)
- Throw `IllegalArgumentException` for invalid input (line 309)
- Throw `SecurityException` for authorization failures (line 226)
- Let Quarkus SmallRye GraphQL convert exceptions to GraphQL errors automatically

**Tip 3: Stripe Checkout URL Return**

The `placeOrder` mutation returns `PaymentIntent` type which includes:
- `id`: Stripe Checkout Session ID (NOT PaymentIntent ID despite the name)
- `clientSecret`: Actually the Stripe Checkout URL (see line 389-390 in OrderResolver)
- `amount`: Total in cents (line 388)

This is a **naming mismatch** in the schema. The type is called `PaymentIntent` but actually contains Checkout Session data. The current implementation correctly builds this response (lines 387-399).

**Tip 4: Test Coverage Requirements**

The acceptance criteria require integration tests. Based on the task description, you MUST test:
1. Query `order(id)` with nested data (calendar, user)
2. Query `orders(userId, status)` with admin access
3. Mutation `placeOrder` with Stripe Checkout URL response
4. Mutation `cancelOrder` with status validation
5. Unauthorized access scenarios (non-admin querying other user's orders)
6. Invalid order status transitions (cancelling shipped order)

Use `@QuarkusTest` with `@TestTransaction` and REST Assured for GraphQL testing. See existing test patterns in the test directory.

**Tip 5: The Task May Already Be Complete**

⚠️ **CRITICAL OBSERVATION:** After analyzing `OrderResolver.java`, I found that **ALL required resolvers are already implemented**:
- ✅ All 4 query operations exist
- ✅ Both mutation operations exist
- ✅ Authorization checks implemented
- ✅ Error handling present
- ✅ Stripe integration complete

The ONLY missing pieces are:
- ❌ DataLoader batch field resolvers for `CalendarOrder.calendar` and `CalendarOrder.user`
- ❌ Integration tests in `OrderResolverTest.java`

**You should verify if the DataLoader and tests exist before implementing. If they do, this task is complete and should be marked `"done": true`.**

**Tip 6: Mutation vs Query Annotations**

Notice in CalendarGraphQL:
- Queries use `@Query("queryName")` annotation
- Mutations use `@Mutation("mutationName")` annotation
- Field resolvers use `@Name("fieldName")` with `@Source` parameter

Ensure you follow this exact pattern for SmallRye GraphQL to recognize the resolver types correctly.

**Warning: Current OrderResolver Uses Different Mutation Approach**

The OrderResolver (lines 277-400) implements `placeOrder` mutation using the newer `PlaceOrderInput` input type, but the task description mentions implementing `createOrder`. The GraphQL schema defines BOTH:
- `createOrder(calendarId, quantity, shippingAddress)` - Lines 746-755
- `placeOrder(input: PlaceOrderInput!)` - Lines 805-808

Current implementation only has `placeOrder`. You SHOULD verify if `createOrder` is also needed or if the task description is outdated. Based on the schema comments, `placeOrder` is the preferred approach ("Alternative to createOrder with more explicit input structure").

---

## Summary: What You Need to Do

1. **Verify Current State**: Check if `src/main/java/villagecompute/calendar/api/graphql/dataloader/OrderDataLoader.java` exists
2. **Implement Missing DataLoaders**: Add batch field resolvers for `CalendarOrder.calendar` and `CalendarOrder.user` relationships in OrderResolver.java
3. **Write Integration Tests**: Create comprehensive tests in `OrderResolverTest.java` covering all acceptance criteria
4. **Validate Existing Implementation**: Ensure the existing OrderResolver queries and mutations match all requirements
5. **Run Tests**: Execute `./mvnw test` to verify all tests pass
6. **Mark Task Complete**: If all acceptance criteria are met, update task status to `"done": true`
