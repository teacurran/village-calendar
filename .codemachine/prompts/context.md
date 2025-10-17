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
  "dependencies": [
    "I3.T2",
    "I3.T3"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: GraphQL API Style and Rationale (from 04_Behavior_and_Communication.md)

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
```

### Context: Place Order Flow (from 04_Behavior_and_Communication.md)

**Description:**

This flow demonstrates the complete e-commerce workflow from cart checkout to payment processing to asynchronous job creation for order confirmation emails.

**Key Design Points:**

1. **Two-Phase Payment**: Order created in PENDING state, updated to PAID after webhook confirmation (handles race conditions)
2. **Webhook Signature Validation**: Prevents fraudulent payment confirmations
3. **Transactional Integrity**: Order and payment records created atomically within database transaction
4. **Asynchronous Email**: Email sending offloaded to job queue to prevent SMTP latency from blocking webhook response
5. **Idempotent Webhooks**: Stripe may retry webhooks; order service checks if payment already processed (via `stripe_payment_intent_id` uniqueness)

**Workflow Steps:**

1. User reviews cart, clicks "Checkout"
2. Frontend sends GraphQL Mutation: `placeOrder(input)` with:
   - calendarId: "123"
   - productType: STANDARD
   - quantity: 1
   - shippingAddress: {...}
3. API calls OrderService.placeOrder(input, currentUser)
4. OrderService creates Order in database (status=PENDING) and OrderItem
5. OrderService calls Stripe to create Checkout Session with line items
6. Stripe returns Checkout Session with URL
7. OrderService stores stripe_checkout_session_id in Order
8. API returns Order with checkout_url to frontend
9. Frontend redirects user to Stripe Checkout
10. User enters payment info and confirms
11. Stripe processes payment and sends webhook: checkout.session.completed
12. API validates webhook signature and updates Order status to PAID
13. Payment success triggers email job enqueue (order confirmation)

### Context: Authentication & Authorization (from 05_Operational_Architecture.md)

**Authorization Model: Role-Based Access Control (RBAC)**

**Roles:**
- `user`: Standard authenticated user (can create/edit own calendars, place orders)
- `admin`: Administrative user (can create templates, view all orders, access analytics)
- `guest`: Implicit role for unauthenticated users (can create calendars in session, read-only templates)

**Access Control Rules for Orders:**

| Resource | Guest | User | Admin |
|----------|-------|------|-------|
| Place order | ❌ | ✅ (own calendars) | ✅ |
| View own orders | ❌ | ✅ | ✅ |
| View all orders | ❌ | ❌ | ✅ |
| Cancel own order | ❌ | ✅ | ✅ |
| Cancel any order | ❌ | ❌ | ✅ |
| Update order status | ❌ | ❌ | ✅ |

**Enforcement Mechanisms:**

1. **GraphQL Field-Level Security**: SmallRye GraphQL `@RolesAllowed` annotation on queries/mutations
   ```java
   @Query
   @RolesAllowed("admin")
   public List<Order> allOrders() { ... }
   ```

2. **Service Layer Checks**: Verify resource ownership before operations
   ```java
   public void cancelOrder(UUID orderId, UUID userId, boolean isAdmin) {
       Order order = orderRepo.findById(orderId);
       if (!order.userId.equals(userId) && !isAdmin) {
           throw new UnauthorizedException("Cannot cancel order owned by another user");
       }
       // ... cancel logic
   }
   ```

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

### Context: GraphQL Schema - Order Types (from schema.graphql)

```graphql
"""
E-commerce order for printed calendars.
Integrates with Stripe for payment processing. Orders are created
after successful payment via Stripe webhook.

Note: Payment details are embedded directly in the order entity via
Stripe-specific fields (stripePaymentIntentId, stripeChargeId, paidAt)
rather than a separate Payment entity. Orders currently support single-item
purchases (one calendar design with quantity field).
"""
type CalendarOrder {
  """Calendar being ordered for printing"""
  calendar: UserCalendar!

  """Timestamp when order was created"""
  created: DateTime!

  """Timestamp when order was delivered"""
  deliveredAt: DateTime

  """Unique order identifier (UUID)"""
  id: ID!

  """Admin notes about order fulfillment"""
  notes: String

  """Timestamp when payment was captured"""
  paidAt: DateTime

  """Number of calendar copies to print"""
  quantity: Int!

  """Timestamp when order was shipped"""
  shippedAt: DateTime

  """Shipping address (JSONB: street, city, state, postalCode, country)"""
  shippingAddress: JSON!

  """Order fulfillment status"""
  status: OrderStatus!

  """Stripe Charge ID (set after payment captured)"""
  stripeChargeId: String

  """Stripe Payment Intent ID"""
  stripePaymentIntentId: String

  """Total order price (quantity * unitPrice, USD)"""
  totalPrice: BigDecimal!

  """Shipment tracking number (set when order ships)"""
  trackingNumber: String

  """Price per calendar (USD)"""
  unitPrice: BigDecimal!

  """Timestamp when order was last updated"""
  updated: DateTime!

  """User who placed this order"""
  user: CalendarUser!
}

"""
Order fulfillment status tracking through the order lifecycle.
"""
enum OrderStatus {
  """Order cancelled by customer or admin before fulfillment"""
  CANCELLED

  """Order successfully delivered to customer"""
  DELIVERED

  """Payment successfully captured via Stripe"""
  PAID

  """Order created, awaiting payment confirmation from Stripe"""
  PENDING

  """Calendar is being printed and prepared for shipment"""
  PROCESSING

  """Payment refunded after cancellation"""
  REFUNDED

  """Order has been shipped to customer with tracking number"""
  SHIPPED
}

"""
Stripe PaymentIntent for checkout flow.
Returned by createOrder mutation to initiate payment on client.
The client uses the clientSecret to complete payment via Stripe.js.
The order entity is created by webhook after payment succeeds.
"""
type PaymentIntent {
  """Amount in cents (USD) - multiply dollar amount by 100"""
  amount: Int!

  """Associated calendar ID (before order is created)"""
  calendarId: ID!

  """Client secret for Stripe.js (passed to confirmCardPayment)"""
  clientSecret: String!

  """Stripe PaymentIntent ID (pi_...)"""
  id: ID!

  """Requested quantity"""
  quantity: Int!

  """Payment status from Stripe"""
  status: String!
}

"""
Input for placing a new order.
Alternative to createOrder mutation with more explicit input structure.
"""
input PlaceOrderInput {
  """ID of calendar to order"""
  calendarId: ID!

  """Product type to order"""
  productType: ProductType!

  """Number of copies to print"""
  quantity: Int!

  """Delivery address"""
  shippingAddress: AddressInput!
}
```

**Query and Mutation Definitions:**

```graphql
type Query {
  """
  Get a single order by ID.
  Returns order if user owns it (or user is admin).
  """
  order(
    """Order ID"""
    id: ID!
  ): CalendarOrder

  """
  Get orders for a specific user (admin only) with optional status filter.
  Requires ADMIN role in JWT claims when userId is provided.
  If userId is not provided, returns orders for authenticated user.
  """
  orders(
    """User ID to fetch orders for (admin only)"""
    userId: ID

    """Filter by order status (optional)"""
    status: OrderStatus
  ): [CalendarOrder!]!

  """
  Get orders for the authenticated user.
  Requires authentication.
  """
  myOrders(
    """Filter by order status (optional)"""
    status: OrderStatus
  ): [CalendarOrder!]!

  """
  Get all orders across all users (admin only).
  Requires ADMIN role in JWT claims.
  """
  allOrders(
    """Filter by order status (optional)"""
    status: OrderStatus

    """Maximum number of orders to return"""
    limit: Int = 50
  ): [CalendarOrder!]!
}

type Mutation {
  """
  Place an order for printed calendars.
  Alternative to createOrder with structured input type.
  Requires authentication. Creates Stripe PaymentIntent for checkout.
  """
  placeOrder(
    """Order details"""
    input: PlaceOrderInput!
  ): PaymentIntent!

  """
  Cancel an order and initiate refund.
  Requires authentication and order ownership (or admin role).
  Can only cancel orders in PENDING or PAID status.
  Automatically triggers Stripe refund for paid orders.
  """
  cancelOrder(
    """Order ID to cancel"""
    orderId: ID!

    """Reason for cancellation (optional, stored in notes)"""
    reason: String
  ): CalendarOrder!
}
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/SessionResolver.java`
    *   **Summary:** This is an EXCELLENT reference implementation of a GraphQL resolver. It demonstrates the correct patterns for:
        - Using SmallRye GraphQL annotations (@GraphQLApi, @Query, @Mutation, @Description, @Name)
        - Role-based authorization (@RolesAllowed, @PermitAll)
        - JWT injection and user context retrieval
        - Proper logging with structured messages
        - Error handling and validation
        - Service delegation pattern
    *   **Recommendation:** You MUST follow the same patterns and conventions used in SessionResolver. Study this file carefully as it's your blueprint. Key patterns:
        - Class-level: `@GraphQLApi`, `@ApplicationScoped`
        - Method-level: `@Query` or `@Mutation`, `@Description`, `@RolesAllowed` or `@PermitAll`
        - Parameters: `@Name("paramName")`, `@Description("...")`, `@NotNull` where applicable
        - Logging: Use structured logging with parameters (e.g., `LOG.infof("Query: order(id=%s)", orderId)`)
        - User context: Inject `JsonWebToken jwt` and use `authService.getCurrentUser(jwt)` to get current user
        - Error handling: Throw IllegalArgumentException for bad input, SecurityException for authorization failures

*   **File:** `src/main/java/villagecompute/calendar/services/OrderService.java`
    *   **Summary:** COMPLETE implementation of all order business logic required for the GraphQL resolvers. This service provides:
        - `createOrder()` - Creates order with tax/shipping calculation
        - `updateOrderStatus()` - Admin-only status transitions with validation
        - `getUserOrders(UUID userId)` - Get all orders for a user
        - `getOrdersByStatus(String status)` - Filter orders by status
        - `getOrderById(UUID orderId)` - Get single order
        - `findByStripePaymentIntent(String paymentIntentId)` - Find order by Stripe Payment Intent ID
        - `cancelOrder()` - Cancel order with authorization checks and refund notes
        - Status transition validation - Enforces order lifecycle state machine
    *   **Recommendation:** You MUST use OrderService methods directly in your resolvers. DO NOT reimplement any business logic. The service layer handles all validation, authorization checks, and state management. Your GraphQL resolver's job is ONLY to:
        1. Extract parameters from GraphQL request
        2. Get current user from JWT
        3. Call appropriate OrderService method
        4. Return result or throw GraphQL error

*   **File:** `src/main/java/villagecompute/calendar/integration/stripe/StripeService.java`
    *   **Summary:** Handles Stripe Checkout Session creation. Key method: `createCheckoutSession(CalendarOrder order, String successUrl, String cancelUrl)` which:
        - Converts order total to cents (Stripe uses smallest currency unit)
        - Creates Stripe line items with calendar name/description
        - Generates idempotency key to prevent duplicates
        - Stores order metadata in session (order_id, user_id, calendar_id)
        - Returns Stripe Session with checkout URL
    *   **Recommendation:** You MUST inject StripeService in your OrderResolver and call `createCheckoutSession()` in the `placeOrder` mutation. The flow is:
        1. Validate calendar ownership
        2. Create order via OrderService (status=PENDING)
        3. Create Stripe Checkout Session via StripeService
        4. Store Stripe session ID in order (future enhancement)
        5. Return PaymentIntent with checkout URL to client

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** JPA entity using Panache ActiveRecord pattern. Key features:
        - Static finder methods: `findByUser(UUID)`, `findByStatusOrderByCreatedDesc(String)`, `findByStripePaymentIntent(String)`, etc.
        - Helper methods: `markAsPaid()`, `markAsShipped()`, `cancel()`, `isTerminal()`
        - Status constants: `STATUS_PENDING`, `STATUS_PAID`, `STATUS_PROCESSING`, `STATUS_SHIPPED`, `STATUS_DELIVERED`, `STATUS_CANCELLED`
        - Important: There is NO separate OrderItem entity. Orders are single-item (one calendar with quantity field).
        - Payment info embedded in order entity: `stripePaymentIntentId`, `stripeChargeId`, `paidAt`
    *   **Recommendation:** Use the static finder methods and helper methods provided by the entity. The entity follows Panache conventions, so you can call methods like `CalendarOrder.findByUser(userId)` directly. Pay special attention to the simplified data model - there are NO separate OrderItem or Payment entities, everything is embedded in CalendarOrder.

*   **File:** `api/schema.graphql`
    *   **Summary:** Complete GraphQL schema definition showing the exact types and mutations you need to implement. Note the simplified structure:
        - CalendarOrder type has `calendar` field (single calendar, not a list of items)
        - No separate OrderItem type in schema
        - PaymentIntent type for checkout flow (returned by placeOrder mutation)
        - Input types: PlaceOrderInput with calendarId, productType, quantity, shippingAddress
    *   **Recommendation:** Your resolver return types and parameter types MUST match the schema EXACTLY. SmallRye GraphQL will auto-generate types from your Java code, but ensure your method signatures align with the schema. The schema shows that `placeOrder` returns `PaymentIntent!` (not CalendarOrder), because the order is created later by webhook after payment succeeds.

*   **File:** `src/main/java/villagecompute/calendar/services/AuthenticationService.java` (referenced but not shown)
    *   **Summary:** Provides `getCurrentUser(JsonWebToken jwt)` method to extract the authenticated user from the JWT token.
    *   **Recommendation:** You MUST inject AuthenticationService and use `authService.getCurrentUser(jwt)` to get the current user for authorization checks. The pattern is:
        ```java
        Optional<CalendarUser> userOpt = authService.getCurrentUser(jwt);
        if (userOpt.isEmpty()) {
            throw new SecurityException("Unauthorized: User not found");
        }
        CalendarUser currentUser = userOpt.get();
        ```

### Implementation Tips & Notes

*   **CRITICAL PATTERN:** The task description is somewhat misleading. It says "placeOrder mutation creates order, returns Stripe Checkout URL", but based on the actual schema and architecture, the flow is:
    1. `placeOrder` mutation creates an Order in PENDING status
    2. `placeOrder` creates a Stripe Checkout Session
    3. `placeOrder` returns a PaymentIntent object (NOT CalendarOrder) containing the Stripe checkout URL
    4. The actual order status update to PAID happens later via webhook (implemented in I3.T3)

    This is a two-phase payment flow for handling race conditions and ensuring payment confirmation before fulfillment.

*   **DataLoader Implementation:** The task mentions "Add DataLoader for efficient order item fetching". However, given the simplified data model (no separate OrderItem entities), there's less need for DataLoader in the traditional sense. Instead, focus on:
    1. Using Panache's batch fetching for order.calendar and order.user relationships
    2. Possibly implementing a DataLoader for UserCalendar fetching if multiple orders reference the same calendar
    3. The DataLoader would batch-load calendars for a list of orders to avoid N+1 queries

    If the data model evolves to include separate OrderItem entities in the future, then a proper DataLoader would be needed.

*   **GraphQL Error Handling:** SmallRye GraphQL provides automatic error handling, but you should throw meaningful exceptions:
    - `IllegalArgumentException` for bad input → GraphQL returns error with message
    - `SecurityException` for authorization failures → GraphQL returns error with "Unauthorized" message
    - Don't catch and swallow exceptions unless you have a specific reason
    - Let GraphQL framework handle exception-to-error conversion

*   **Authorization Pattern:** For the `orders` query, implement this logic:
    ```java
    @Query("orders")
    @Description("Get orders for a user. Admin can query any user, non-admin gets own orders.")
    @RolesAllowed({"USER", "ADMIN"})
    public List<CalendarOrder> orders(
        @Name("userId") UUID userId,
        @Name("status") String status
    ) {
        Optional<CalendarUser> userOpt = authService.getCurrentUser(jwt);
        CalendarUser currentUser = userOpt.get();

        // If userId provided, only admin can query other users
        if (userId != null && !userId.equals(currentUser.id)) {
            if (!currentUser.role.equals("ADMIN")) {
                throw new SecurityException("Cannot view orders for another user");
            }
            // Admin querying another user
            return orderService.getUserOrders(userId);
        }

        // Query own orders (userId null or matches current user)
        UUID targetUserId = userId != null ? userId : currentUser.id;

        if (status != null) {
            // Filter by status (need custom query or post-filter)
            List<CalendarOrder> allOrders = orderService.getUserOrders(targetUserId);
            return allOrders.stream()
                .filter(o -> o.status.equals(status))
                .collect(Collectors.toList());
        }

        return orderService.getUserOrders(targetUserId);
    }
    ```

*   **Logging Best Practices:** Follow the logging patterns in SessionResolver:
    - Query/Mutation start: `LOG.infof("Query: order(id=%s)", orderId)`
    - Service calls: `LOG.debugf("Fetching order from service")`
    - Results: `LOG.infof("Found order %s with status %s", order.id, order.status)`
    - Errors: `LOG.errorf("Order not found: %s", orderId)` before throwing exception

*   **Testing Strategy:** Integration tests should:
    1. Use `@QuarkusTest` annotation for full Quarkus context
    2. Test with REST Assured against the GraphQL endpoint `/graphql`
    3. Include both positive and negative test cases:
        - Query order by ID (found, not found, unauthorized)
        - Query orders with filters (status, userId)
        - Place order (success, calendar not found, not authenticated)
        - Cancel order (success, already shipped, not authorized)
    4. Set up test data with `@Transactional` before each test
    5. Use `@Inject` to access OrderService for verification
    6. Test admin vs user role differences

*   **Common Pitfalls to Avoid:**
    1. **DO NOT** create OrderItem entities - the data model uses quantity field on Order
    2. **DO NOT** return CalendarOrder from placeOrder mutation - return PaymentIntent per schema
    3. **DO NOT** manually convert between domain entities and GraphQL types - SmallRye GraphQL does this automatically
    4. **DO NOT** implement your own authorization logic - use `@RolesAllowed` and service layer checks
    5. **DO NOT** forget to validate status transitions before allowing cancellation

*   **Status Transition Rules (from OrderService):**
    - PENDING → PAID, CANCELLED
    - PAID → PROCESSING, SHIPPED, CANCELLED
    - PROCESSING → SHIPPED, CANCELLED
    - SHIPPED → DELIVERED
    - DELIVERED → (terminal, no transitions)
    - CANCELLED → (terminal, no transitions)

    Your cancelOrder mutation MUST respect these rules and use OrderService.cancelOrder() which validates transitions.

*   **Payment Intent Return Type:** For the placeOrder mutation, you'll need to create a PaymentIntent response object that includes:
    ```java
    public class PaymentIntentResponse {
        public String id; // Stripe PaymentIntent ID or Checkout Session ID
        public String clientSecret; // Stripe client secret for frontend
        public Long amount; // Amount in cents
        public UUID calendarId;
        public Integer quantity;
        public String status; // "requires_payment_method" or other Stripe status
    }
    ```

    This maps to the GraphQL PaymentIntent type in the schema.

*   **File Naming Convention:** The task specifies target files:
    - OrderResolver.java - Main resolver class
    - OrderDataLoader.java - DataLoader for batch fetching (create in `dataloader` sub-package)
    - OrderResolverTest.java - Integration tests

    However, based on the codebase structure, the resolver should be in `src/main/java/villagecompute/calendar/api/graphql/` (no separate sub-package for queries vs mutations).

---

## 4. Recommended Implementation Sequence

Here's the recommended order to implement the task:

### Step 1: Create OrderResolver skeleton

Create `OrderResolver.java` with:
- Class annotations: `@GraphQLApi`, `@ApplicationScoped`
- Inject dependencies: `JsonWebToken jwt`, `AuthenticationService authService`, `OrderService orderService`, `StripeService stripeService`
- Stub methods for all queries and mutations
- Add logging statements

### Step 2: Implement query resolvers

1. **order(id)** query:
   - Extract orderId parameter
   - Get current user from JWT
   - Call OrderService.getOrderById()
   - Authorize: user owns order OR user is admin
   - Return order or throw SecurityException

2. **orders(userId, status)** query:
   - Implement authorization logic (see pattern above)
   - Call OrderService methods
   - Filter by status if provided
   - Return list of orders

3. **myOrders(status)** query:
   - Simplified version of orders() for current user
   - Get current user from JWT
   - Call OrderService.getUserOrders()
   - Filter by status if provided

4. **allOrders(status, limit)** query (admin only):
   - Add `@RolesAllowed("ADMIN")`
   - Call OrderService.getOrdersByStatus() or get all
   - Apply limit
   - Return list

### Step 3: Implement mutation resolvers

1. **placeOrder(input)** mutation:
   - Extract PlaceOrderInput fields
   - Get current user from JWT
   - Validate calendar exists and user owns it
   - Create order via OrderService.createOrder()
   - Create Stripe Checkout Session via StripeService
   - Build and return PaymentIntent response

2. **cancelOrder(orderId, reason)** mutation:
   - Extract parameters
   - Get current user from JWT
   - Call OrderService.cancelOrder() with authorization
   - Return updated order

### Step 4: Implement DataLoader (if needed)

- Based on the simplified data model, DataLoader may not be required
- If implementing, create OrderDataLoader in dataloader package
- Register with SmallRye GraphQL context
- Use for batch-fetching calendars/users

### Step 5: Write integration tests

- Create OrderResolverTest.java
- Use @QuarkusTest
- Test all queries and mutations
- Test authorization scenarios
- Test error cases

### Step 6: Manual testing

- Start Quarkus in dev mode: `./mvnw quarkus:dev`
- Access GraphQL UI at http://localhost:8080/graphql-ui
- Test queries and mutations with sample data
- Verify authorization rules
- Check Stripe integration (use test mode)

---

## 5. Key Acceptance Criteria Mapping

Let me map each acceptance criterion to implementation guidance:

1. **"GraphQL query { order(orderId: \"123\") { orderNumber status items { calendar { title } } } } returns order with nested data"**
   - Note: Schema shows `calendar` field (singular), not `items` array
   - Implement `order(id)` query that returns CalendarOrder entity
   - SmallRye GraphQL auto-resolves nested fields (calendar.title)
   - Ensure Panache fetches calendar relationship (should be EAGER by default)

2. **"placeOrder mutation creates order, returns Stripe Checkout URL"**
   - Create order via OrderService.createOrder() with status=PENDING
   - Create Stripe Checkout Session via StripeService.createCheckoutSession()
   - Extract checkout URL from session.getUrl()
   - Build PaymentIntent response with id, clientSecret (or URL), amount, calendarId, quantity, status
   - Return PaymentIntent (NOT CalendarOrder)

3. **"Unauthorized access to other user's order returns GraphQL error"**
   - In `order(id)` query, check if order.user.id equals currentUser.id
   - If not equal and user not admin, throw SecurityException("Unauthorized: Cannot view order owned by another user")
   - SmallRye GraphQL converts exception to GraphQL error response

4. **"Admin role can query any user's orders"**
   - In `orders(userId, status)` query, check JWT role claim
   - If userId provided and != current user, require admin role
   - Use authService.getCurrentUser(jwt) to get user with role
   - Check currentUser.role.equals("ADMIN") or use @RolesAllowed

5. **"cancelOrder mutation validates order status (cannot cancel shipped order), returns updated order"**
   - Call OrderService.cancelOrder() which validates status transitions
   - OrderService throws IllegalStateException if order.isTerminal()
   - Status transition validation ensures SHIPPED orders cannot be cancelled
   - Return updated CalendarOrder entity

6. **"Integration tests verify end-to-end GraphQL order workflows"**
   - Create test class with @QuarkusTest
   - Use REST Assured to POST to /graphql endpoint
   - Build GraphQL query/mutation JSON payload
   - Verify response structure and data
   - Test auth scenarios with different JWT tokens

---

## 6. Summary

This task is about **implementing GraphQL resolvers** that expose the existing OrderService and StripeService functionality via GraphQL API. The services are already complete, so your job is to:

1. Create GraphQL resolver methods that map to schema queries/mutations
2. Handle authentication and authorization using JWT and role checks
3. Delegate business logic to OrderService (no logic in resolvers)
4. Return proper GraphQL types (especially PaymentIntent from placeOrder)
5. Provide meaningful error messages for invalid requests
6. Write comprehensive integration tests

**Key Architectural Notes:**
- Simplified data model: No separate OrderItem or Payment entities
- Two-phase payment: placeOrder creates pending order + Stripe session, webhook marks as paid later
- Authorization: User can only access own orders, admin can access all
- Status validation: Enforced by OrderService, not in resolver

Focus on creating clean, well-documented resolver code that follows the SessionResolver patterns. Test thoroughly with both unit and integration tests. Good luck!
