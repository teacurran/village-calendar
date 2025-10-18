# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T9",
  "iteration_id": "I3",
  "iteration_goal": "Implement complete e-commerce workflow including Stripe payment integration, order placement, order management dashboard (admin), and transactional email notifications",
  "description": "Create end-to-end integration tests for complete e-commerce workflows using Quarkus test framework with REST Assured. Test scenarios: (1) Place order and payment success - create calendar, place order, simulate Stripe webhook (checkout.session.completed), verify order status PAID, verify email job enqueued; (2) Cancel order - create order, cancel order, verify status CANCELLED, verify refund processed (mock Stripe API); (3) Admin order management - admin queries all orders, updates order status to SHIPPED, adds tracking number, verifies changes persisted. Use Testcontainers for PostgreSQL, mock Stripe API for webhook simulation. Achieve >70% code coverage for order/payment services and API layer.",
  "agent_type_hint": "BackendAgent",
  "inputs": "All implemented services and resolvers from I3 tasks, Quarkus testing, REST Assured, Testcontainers documentation",
  "target_files": [
    "src/test/java/villagecompute/calendar/integration/OrderWorkflowTest.java",
    "src/test/java/villagecompute/calendar/integration/PaymentWorkflowTest.java",
    "src/test/java/villagecompute/calendar/integration/AdminOrderWorkflowTest.java"
  ],
  "input_files": [
    "api/schema.graphql"
  ],
  "deliverables": "Integration tests for all critical e-commerce workflows, Tests use GraphQL API and webhook endpoints, Stripe webhook simulation (mock events), Test database with Testcontainers, Tests achieve >70% coverage for order/payment code",
  "acceptance_criteria": "Order placement test creates order, simulates Stripe webhook, verifies order PAID, Payment success test verifies Payment entity created with correct amount, Email job enqueued on payment success (verifiable in delayed_jobs table), Cancel order test processes refund, updates order status to CANCELLED, Admin workflow test updates order status, adds tracking number, verifies persistence, All integration tests pass with ./mvnw verify, Tests run in <90 seconds",
  "dependencies": [
    "I3.T1",
    "I3.T2",
    "I3.T3",
    "I3.T4",
    "I3.T5",
    "I3.T6",
    "I3.T7",
    "I3.T8"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: flow-place-order (from 04_Behavior_and_Communication.md)

```markdown
##### Flow 2: Place Order for Printed Calendar

**Description:**

This flow demonstrates the complete e-commerce workflow from cart checkout to payment processing to asynchronous job creation for order confirmation emails.

**Key Design Points:**

1. **Two-Phase Payment**: Order created in PENDING state, updated to PAID after webhook confirmation (handles race conditions)
2. **Webhook Signature Validation**: Prevents fraudulent payment confirmations
3. **Transactional Integrity**: Order and payment records created atomically within database transaction
4. **Asynchronous Email**: Email sending offloaded to job queue to prevent SMTP latency from blocking webhook response
5. **Idempotent Webhooks**: Stripe may retry webhooks; order service checks if payment already processed (via `stripe_payment_intent_id` uniqueness)

**Complete Sequence:**
1. User places order via GraphQL `placeOrder` mutation
2. OrderService creates order in PENDING status with Stripe PaymentIntent
3. User completes payment on Stripe
4. Stripe sends `checkout.session.completed` webhook to `/api/webhooks/stripe`
5. WebhookResource validates signature, calls PaymentService.processPaymentSuccess()
6. PaymentService updates order status to PAID, enqueues email job
7. DelayedJob worker sends order confirmation email
8. User polls order status, sees PAID status
```

### Context: GraphQL Order Operations (from schema.graphql)

The GraphQL schema defines the following order-related queries and mutations that your tests will interact with:

**Queries:**
- `order(id: ID!): CalendarOrder` - Get a single order by ID (user must own it or be admin)
- `orders(userId: ID, status: OrderStatus): [CalendarOrder!]!` - Get orders with optional filtering
- `allOrders(status: OrderStatus, limit: Int = 50): [CalendarOrder!]!` - Admin-only query for all orders
- `myOrders(status: OrderStatus): [CalendarOrder!]!` - Get authenticated user's orders

**Mutations:**
- `createOrder(calendarId: ID!, quantity: Int!, shippingAddress: AddressInput!): PaymentIntent!` - Creates Stripe PaymentIntent
- `placeOrder(input: PlaceOrderInput!): PaymentIntent!` - Alternative order creation with structured input
- `cancelOrder(orderId: ID!, reason: String): CalendarOrder!` - Cancel order and initiate refund
- `updateOrderStatus(id: ID!, input: OrderUpdateInput!): CalendarOrder!` - Admin-only status updates

**Order Status Lifecycle:**
```
PENDING → PAID → PROCESSING → SHIPPED → DELIVERED
    ↓           ↓        ↓
CANCELLED ← CANCELLED ← CANCELLED
```

**Important CalendarOrder Type Fields:**
```graphql
type CalendarOrder {
  id: ID!
  calendar: UserCalendar!
  quantity: Int!
  unitPrice: BigDecimal!
  totalPrice: BigDecimal!
  status: OrderStatus!  # Values: PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED
  shippingAddress: JSON!
  stripePaymentIntentId: String
  stripeChargeId: String
  paidAt: DateTime
  shippedAt: DateTime
  trackingNumber: String
  notes: String
  user: CalendarUser!
}
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/services/OrderService.java`
    *   **Summary:** Contains complete order lifecycle management including `createOrder()`, `updateOrderStatus()`, `cancelOrder()`, and email job enqueueing. This is your PRIMARY service under test.
    *   **Recommendation:** You MUST test the following OrderService methods in your integration tests:
        - `createOrder()` - verify order creation with tax/shipping calculation
        - `updateOrderStatus()` - test status transitions (PENDING → PAID → PROCESSING → SHIPPED)
        - `cancelOrder()` - verify authorization checks and cancellation logic
        - Email job enqueueing on status changes (check `delayed_jobs` table)
    *   **Important Detail:** OrderService enqueues email jobs using `DelayedJob.createDelayedJob()` when order status changes to PAID or SHIPPED (lines 144-148). Your tests MUST verify these jobs are created in the database by querying `delayed_jobs` table after order status changes.
    *   **Authorization Pattern:** cancelOrder() method (lines 222-276) checks if user owns the order OR is admin (lines 234-238). Your tests should verify this authorization logic.

*   **File:** `src/main/java/villagecompute/calendar/services/PaymentService.java`
    *   **Summary:** Handles Stripe integration including `createPaymentIntent()`, `processPaymentSuccess()` (webhook handler), and `processRefund()`. Implements idempotent webhook processing.
    *   **Recommendation:** You MUST test PaymentService.processPaymentSuccess() which is called by webhook handler. This method (lines 203-248):
        - Finds order by PaymentIntent ID using `orderService.findByStripePaymentIntent()`
        - Updates order status to PAID (idempotent check at lines 216-220)
        - Enqueues order confirmation email job (lines 240-245)
        - Returns `false` if already processed (idempotency)
    *   **Critical for Testing:** The `processPaymentSuccess()` method implements idempotency by checking if order is already PAID before updating. Your tests should verify this by calling the method twice with same PaymentIntent ID.
    *   **Refund Processing:** processRefund() method (lines 260-303) creates Stripe refund and updates order notes. Your cancel order test should verify refund is attempted.

*   **File:** `src/main/java/villagecompute/calendar/api/rest/WebhookResource.java`
    *   **Summary:** REST endpoint at `/api/webhooks/stripe` (line 69) that handles Stripe webhook events. Validates webhook signatures (lines 126-149) and processes different event types (lines 172-188).
    *   **Recommendation:** You MUST test the webhook endpoint by POSTing mock Stripe event payloads. Key event types to test:
        - `checkout.session.completed` (lines 197-220) - triggers order payment success
        - `payment_intent.succeeded` (lines 229-252) - alternative payment success event
        - `charge.refunded` (lines 308-347) - triggers refund processing
    *   **Mock Strategy:** The webhook requires a valid Stripe signature (line 115 header). You SHOULD mock PaymentService to avoid signature validation:
        ```java
        @InjectMock
        PaymentService paymentService;

        when(paymentService.getWebhookSecret()).thenReturn("");
        when(paymentService.processPaymentSuccess(anyString(), anyString())).thenReturn(true);
        ```
    *   **Important:** Webhook handler parses JSON manually using `ObjectMapper.readTree()` (lines 199-205) to work in both test and production. Your mock events should be valid JSON with correct structure.

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/OrderResolver.java`
    *   **Summary:** GraphQL resolver implementing order queries and mutations. Handles authorization checks and delegates to OrderService/PaymentService.
    *   **Recommendation:** Your integration tests should call GraphQL mutations/queries via REST Assured to `/graphql` endpoint, NOT call service methods directly. This tests the full stack including GraphQL layer, authorization, and service integration.
    *   **Authentication Note:** Some GraphQL operations require authentication. Check existing test pattern in `CalendarServiceIntegrationTest.java` which uses `@Transactional` setup to create test users and avoids JWT complexity.

*   **File:** `src/test/java/villagecompute/calendar/integration/CalendarServiceIntegrationTest.java`
    *   **Summary:** Exemplary integration test showing the project's testing patterns. Uses Quarkus @QuarkusTest (line 43), REST Assured for GraphQL testing, @InjectMock for external dependencies (line 53-54), and @Transactional cleanup (lines 88-102).
    *   **Recommendation:** You SHOULD follow this exact pattern:
        - Extend your test classes with `@QuarkusTest`
        - Use `@BeforeEach` with `@Transactional` to create test data (users, calendars, templates, orders)
        - Use `@AfterEach` with `@Transactional` to clean up test data (lines 88-102)
        - Use `@InjectMock` to mock StorageService or PaymentService for external API calls (line 53-54)
        - Use `io.restassured.RestAssured.given()` to make GraphQL API calls (lines 142-153)
        - Use `@Order(N)` annotations to control test execution order (line 126)
        - Use `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` on class (line 44)
    *   **Code Pattern Example:**
        ```java
        @QuarkusTest
        @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
        class OrderWorkflowTest {
            @Inject ObjectMapper objectMapper;
            @InjectMock PaymentService paymentService;  // Mock Stripe calls

            private CalendarUser testUser;
            private UserCalendar testCalendar;

            @BeforeEach
            @Transactional
            void setup() {
                // Create test user, calendar, template...
                when(paymentService.getWebhookSecret()).thenReturn("");
            }

            @AfterEach
            @Transactional
            void cleanup() {
                // Delete test data in reverse FK order
            }
        }
        ```

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** JPA entity for orders with Panache active record pattern. Contains status constants like `STATUS_PENDING`, `STATUS_PAID`, `STATUS_SHIPPED`, etc.
    *   **Recommendation:** You MUST use CalendarOrder entity methods and constants in your tests:
        - Use `CalendarOrder.STATUS_PENDING`, `CalendarOrder.STATUS_PAID`, etc. constants
        - Use `CalendarOrder.findByStripePaymentIntent(paymentIntentId)` to query orders
        - Use `CalendarOrder.findByStatusOrderByCreatedDesc(status)` for status filtering
        - Check `order.isTerminal()` to verify cancellation logic

*   **File:** `src/main/java/villagecompute/calendar/data/models/DelayedJob.java`
    *   **Summary:** JPA entity for async job queue. Used for email sending after order events.
    *   **Recommendation:** Your tests MUST verify email jobs are enqueued by querying `delayed_jobs` table:
        ```java
        // After order paid, verify email job created
        List<DelayedJob> jobs = DelayedJob.find("payload", orderId.toString()).list();
        assertEquals(1, jobs.size(), "Order confirmation email job should be enqueued");
        assertEquals(DelayedJobQueue.EMAIL_ORDER_CONFIRMATION, jobs.get(0).queue);
        ```
    *   **Important:** Email jobs are created by `DelayedJob.createDelayedJob(orderId, queue, runAt)` method. The payload is the order ID as string.

### Implementation Tips & Notes

*   **Tip 1: Package Path**
    The project uses **package path `villagecompute.calendar`** (NO `.com` prefix). Your test files MUST be in `villagecompute.calendar.integration` package, NOT `com.villagecompute.calendar.integration`.

*   **Tip 2: Mock Stripe Webhook Events**
    For webhook testing, you can construct a mock Stripe Event JSON like this:
    ```java
    String webhookPayload = """
        {
          "id": "evt_test_webhook",
          "type": "checkout.session.completed",
          "data": {
            "object": {
              "id": "cs_test_session",
              "payment_intent": "pi_test_12345"
            }
          }
        }
        """;

    // Mock PaymentService to skip signature validation
    when(paymentService.getWebhookSecret()).thenReturn("");
    when(paymentService.processPaymentSuccess(eq("pi_test_12345"), isNull())).thenReturn(true);

    given()
        .contentType(ContentType.JSON)
        .header("Stripe-Signature", "t=123,v1=fake")
        .body(webhookPayload)
        .when()
        .post("/api/webhooks/stripe")
        .then()
        .statusCode(200)
        .body("status", equalTo("success"));
    ```

*   **Tip 3: GraphQL Mutation Pattern**
    For GraphQL testing, use this pattern from existing tests:
    ```java
    String mutation = """
        mutation {
            createOrder(
                calendarId: "%s"
                quantity: 1
                shippingAddress: {
                    street: "123 Main St"
                    city: "Nashville"
                    state: "TN"
                    postalCode: "37203"
                    country: "US"
                }
            ) {
                id
                clientSecret
                amount
            }
        }
        """.formatted(calendarId);

    Response response = given()
        .contentType(ContentType.JSON)
        .body(Map.of("query", mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", nullValue())
        .extract()
        .response();

    String paymentIntentId = response.jsonPath().getString("data.createOrder.id");
    ```

*   **Tip 4: Creating Test Address JsonNode**
    To create shipping address for test orders:
    ```java
    JsonNode shippingAddress = objectMapper.readTree("""
        {
            "street": "123 Main St",
            "city": "Nashville",
            "state": "TN",
            "postalCode": "37203",
            "country": "US"
        }
        """);
    ```

*   **Tip 5: Idempotency Testing**
    The PaymentService.processPaymentSuccess() implements idempotency. Test it:
    ```java
    // First call should return true (processed)
    boolean firstCall = paymentService.processPaymentSuccess(paymentIntentId, chargeId);
    assertTrue(firstCall, "First webhook should be processed");

    // Second call should return false (already processed)
    boolean secondCall = paymentService.processPaymentSuccess(paymentIntentId, chargeId);
    assertFalse(secondCall, "Second webhook should be idempotent (already processed)");

    // Verify order status is PAID (not changed by second call)
    CalendarOrder order = CalendarOrder.findByStripePaymentIntent(paymentIntentId).firstResult();
    assertEquals(CalendarOrder.STATUS_PAID, order.status);
    ```

*   **Tip 6: Admin Authorization Testing**
    To test admin-only operations, create a test user with admin role:
    ```java
    CalendarUser adminUser = new CalendarUser();
    adminUser.oauthProvider = "GOOGLE";
    adminUser.oauthSubject = "admin-test-" + System.currentTimeMillis();
    adminUser.email = "admin@example.com";
    adminUser.isAdmin = true;  // Make this user an admin
    adminUser.persist();
    ```
    Then call admin mutations. Without authentication JWT, you may need to call service methods directly for admin tests.

*   **Tip 7: Test Data Cleanup Order**
    Clean up test data in reverse FK dependency order to avoid violations:
    ```java
    @AfterEach
    @Transactional
    void cleanup() {
        // 1. Delete delayed jobs referencing order
        DelayedJob.delete("payload", testOrder.id.toString());
        // 2. Delete order
        CalendarOrder.deleteById(testOrder.id);
        // 3. Delete calendar
        UserCalendar.deleteById(testCalendar.id);
        // 4. Delete user
        CalendarUser.deleteById(testUser.id);
        // 5. Delete template
        CalendarTemplate.deleteById(testTemplate.id);
    }
    ```

*   **Warning 1: Mock External Services Only**
    The `PaymentService.createPaymentIntent()` makes real Stripe API calls. You MUST mock this:
    ```java
    @InjectMock
    PaymentService paymentService;

    Map<String, String> mockPaymentIntent = new HashMap<>();
    mockPaymentIntent.put("clientSecret", "pi_test_secret_123");
    mockPaymentIntent.put("paymentIntentId", "pi_test_12345");

    when(paymentService.createPaymentIntent(any(BigDecimal.class), anyString(), anyString()))
        .thenReturn(mockPaymentIntent);
    ```

*   **Warning 2: Performance - Reuse Test Data**
    The acceptance criteria requires tests to run in <90 seconds. Avoid creating excessive test data. Reuse test users/calendars/templates across test methods where possible. Use `@Order` annotations to control test execution order and share setup.

*   **Warning 3: Coverage Target**
    Aim for >70% code coverage on `OrderService.java` and `PaymentService.java`. Focus on testing all public methods and important branches (status transitions, authorization checks, idempotency).

*   **Warning 4: GraphQL Type Names**
    The GraphQL schema uses `CalendarOrder` as the type name (not just `Order`). Your test queries/mutations MUST use the correct type names from `api/schema.graphql`.

### Suggested Test Structure

**File: OrderWorkflowTest.java** (Main order placement and payment flow)
- `testOrderPlacement_CreatesPendingOrder()` - GraphQL createOrder returns PaymentIntent
- `testWebhookPaymentSuccess_UpdatesOrderToPaid()` - Webhook updates order status to PAID
- `testWebhookPaymentSuccess_EnqueuesEmailJob()` - Verify delayed_jobs entry created
- `testWebhookPaymentSuccess_Idempotent()` - Second webhook call does not duplicate processing

**File: PaymentWorkflowTest.java** (Payment and refund scenarios)
- `testPaymentIntentCreation_ReturnsValidIntent()` - Mock Stripe API returns clientSecret
- `testRefundProcessing_UpdatesOrderNotes()` - Refund webhook updates order
- `testPaymentFailure_LogsError()` - payment_intent.payment_failed webhook logs error

**File: AdminOrderWorkflowTest.java** (Admin operations)
- `testAdminUpdateOrderStatus_ToShipped()` - Admin updates status to SHIPPED with tracking
- `testAdminUpdateOrderStatus_EnqueuesShippingEmail()` - Verify shipping email job created
- `testAdminQueryAllOrders_ReturnsAllOrders()` - allOrders query returns orders across users
- `testCancelOrder_UserOwnsOrder()` - User can cancel their own order
- `testCancelOrder_UnauthorizedUser()` - User cannot cancel other user's order

### Expected File Structure

```
src/test/java/villagecompute/calendar/integration/
├── OrderWorkflowTest.java         (~250 lines, 4-5 test methods)
├── PaymentWorkflowTest.java       (~200 lines, 3-4 test methods)
└── AdminOrderWorkflowTest.java    (~300 lines, 5-6 test methods)
```

**Total Implementation Time:** ~4-6 hours for comprehensive integration test suite

---

**End of Task Briefing Package**
