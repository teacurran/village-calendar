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

### Context: Integration Testing Strategy (from 03_Verification_and_Glossary.md)

**Testing Levels**

Integration tests validate that multiple components work correctly together:

- **Service Layer Integration:** Service classes interacting with repositories (Panache entities)
- **API Integration:** GraphQL resolvers calling services and returning proper responses
- **External Integration:** Mocked external services (Stripe, OAuth, Email SMTP, R2 storage)
- **Database Integration:** Real PostgreSQL database (via Testcontainers) to verify data persistence, transactions, and query correctness

**CI/CD Pipeline Testing**

All tests run automatically on every push via GitHub Actions:
- Unit tests must pass with >70% code coverage
- Integration tests verify end-to-end workflows
- Tests must complete in <90 seconds total execution time

**Code Quality Gates**

Mandatory quality gates enforced in CI:
- JaCoCo code coverage: >70% for service/API layers
- All integration tests must pass
- No critical SonarQube issues

### Context: E-commerce Testing Requirements (from tasks_I3.json)

Integration test scenarios that must be validated:

1. **Order Placement and Payment Success Workflow:**
   - Create calendar via GraphQL mutation
   - Place order via createOrder mutation (returns PaymentIntent with client secret)
   - Simulate Stripe webhook (checkout.session.completed event)
   - Verify order status transitions from PENDING to PAID
   - Verify Payment entity created with correct amount
   - Verify email job enqueued in delayed_jobs table (ORDER_CONFIRMATION queue)

2. **Order Cancellation and Refund Workflow:**
   - Create order in PAID status
   - Cancel order via cancelOrder GraphQL mutation
   - Verify order status transitions to CANCELLED
   - Verify Stripe refund processed (mock Stripe API)
   - Verify refund amount matches order total
   - Verify cancellation email job enqueued

3. **Admin Order Management Workflow:**
   - Create multiple orders for different users
   - Admin queries all orders (across users)
   - Admin updates order status: PAID → PROCESSING → SHIPPED → DELIVERED
   - Admin adds tracking number when marking as SHIPPED
   - Verify shipping notification email job enqueued
   - Verify status transitions validated (prevent invalid transitions)
   - Verify authorization checks (users can cancel own orders, admins can cancel any order)

### Context: Stripe Integration Architecture (from Plan Section "Payment Processing")

**Stripe Payment Flow:**
1. Frontend calls `createOrder` GraphQL mutation with calendar, quantity, shipping address
2. Backend creates Order entity in PENDING status, generates unique order number
3. Backend calls Stripe API to create PaymentIntent with amount in cents
4. Backend returns PaymentIntent client secret to frontend
5. Frontend redirects user to Stripe Checkout (hosted payment page)
6. User completes payment on Stripe
7. Stripe sends webhook (checkout.session.completed) to backend
8. Backend webhook handler validates signature, processes payment success
9. Backend updates order status to PAID, enqueues confirmation email
10. User redirected to order success page with order number

**Webhook Security:**
- All webhooks MUST validate Stripe signature using webhook secret
- Idempotent processing: handle duplicate webhook deliveries gracefully
- Webhook events stored/checked to prevent duplicate processing

### Context: Order Status State Machine (from OrderService.java)

Valid order status transitions:
- **PENDING** → PAID | CANCELLED
- **PAID** → PROCESSING | SHIPPED | CANCELLED
- **PROCESSING** → SHIPPED | CANCELLED
- **SHIPPED** → DELIVERED
- **DELIVERED** (terminal, no transitions allowed)
- **CANCELLED** (terminal, no transitions allowed)

Invalid transitions throw `IllegalStateException` with message: "Invalid status transition from {current} to {new}"

Terminal order check: `order.isTerminal()` returns true for DELIVERED or CANCELLED status

### Context: Email Job Enqueueing (from OrderService.java)

Email jobs are enqueued as DelayedJob entities with different queues:
- **EMAIL_ORDER_CONFIRMATION**: Sent when order status → PAID (payment success webhook)
- **EMAIL_SHIPPING_NOTIFICATION**: Sent when order status → SHIPPED (admin updates)
- **EMAIL_GENERAL**: Sent when order cancelled (user or admin cancellation)

Email jobs created with:
```java
DelayedJob emailJob = DelayedJob.createDelayedJob(
    order.id.toString(),        // actorId (order ID)
    DelayedJobQueue.EMAIL_ORDER_CONFIRMATION,  // queue type
    Instant.now()               // runAt (immediate execution)
);
```

Jobs can be queried: `DelayedJob.find("actorId", order.id.toString()).list()`

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### **File:** `src/test/java/villagecompute/calendar/integration/OrderWorkflowTest.java`
   - **Summary:** Integration tests for order placement and payment webhook processing. Tests order creation via GraphQL, webhook payment success processing, email job enqueueing, and idempotent webhook handling.
   - **Current State:** The test file exists and contains 5 test methods covering order creation, payment success webhooks, email job enqueueing, idempotency, and shipping calculations.
   - **What You MUST DO:** Review all existing tests in this file. They provide the foundation and patterns you should follow. DO NOT modify existing tests unless they are broken. ADD additional test scenarios if any acceptance criteria are not covered.
   - **Key Pattern to Follow:** Uses `@QuarkusTest`, `@InjectMock` for PaymentService, `@Transactional` for setup/cleanup, and REST Assured for GraphQL API testing.

#### **File:** `src/test/java/villagecompute/calendar/integration/PaymentWorkflowTest.java`
   - **Summary:** Integration tests for payment and refund workflows. Tests order cancellation with refund processing, authorization checks (user can only cancel own orders), payment entity creation on webhook, and refund processing via Stripe.
   - **Current State:** The test file exists and contains 7 test methods covering order cancellation with refund, status validation for cancellation, pending order cancellation without refund, authorization checks, refund webhooks, payment intent creation, and cancellation email enqueueing.
   - **What You MUST DO:** Review existing test patterns, especially the refund processing mock setup and authorization test patterns. Ensure all acceptance criteria are met.
   - **Key Pattern to Follow:** Uses `@InjectMock PaymentService` and mocks `processRefund()` method. Authorization tests verify `SecurityException` thrown when user tries to cancel another user's order.

#### **File:** `src/test/java/villagecompute/calendar/integration/AdminOrderWorkflowTest.java`
   - **Summary:** Integration tests for admin order management workflows. Tests admin-only operations like updating order status, adding tracking numbers, querying all orders across users, and authorization checks.
   - **Current State:** The test file exists and contains 11 test methods covering admin status updates (SHIPPED, PROCESSING, DELIVERED), shipping email enqueueing, terminal order restrictions, admin queries across users, status filtering, and authorization (users can cancel own orders, admins can cancel any order, users cannot cancel other users' orders).
   - **What You MUST DO:** Review existing admin workflow tests. Verify all acceptance criteria are met, especially status transition validation, tracking number handling, and multi-user query scenarios.
   - **Key Pattern to Follow:** Uses `OrderService` directly (not mocked) to test service layer logic. Uses `EntityManager.flush()` and `EntityManager.clear()` to ensure database persistence is tested correctly.

#### **File:** `src/main/java/villagecompute/calendar/services/OrderService.java`
   - **Summary:** Service for managing calendar orders. Handles order creation, status updates, cancellation, and order queries. Contains status transition validation logic, email job enqueueing, and tax/shipping calculation.
   - **Recommendation:** Your tests MUST call `OrderService` methods directly (via injection) to verify service layer logic works correctly. DO NOT mock OrderService in integration tests - only mock external dependencies like PaymentService (Stripe API calls).
   - **Critical Methods to Test:**
     - `createOrder()`: Creates order with PENDING status, calculates total with tax/shipping, generates order number
     - `updateOrderStatus()`: Validates status transitions, updates timestamps (paidAt, shippedAt), enqueues email jobs
     - `cancelOrder()`: Validates authorization (user owns order or isAdmin), validates cancellable status, enqueues cancellation email
     - `validateStatusTransition()`: Private method enforcing state machine - verify via public method tests

#### **File:** `src/main/java/villagecompute/calendar/services/PaymentService.java`
   - **Summary:** Service for integrating with Stripe payment processing. Handles PaymentIntent creation, payment confirmations, webhook processing, and refund processing.
   - **Recommendation:** You MUST mock PaymentService in all integration tests to avoid real Stripe API calls. Use `@InjectMock PaymentService paymentService` annotation.
   - **Critical Methods to Mock:**
     - `createPaymentIntent()`: Mock to return Map with "clientSecret" and "paymentIntentId"
     - `processPaymentSuccess()`: Mock to return true (payment processed) or false (already processed/idempotent)
     - `processRefund()`: Mock to return null (we don't validate Stripe Refund object in tests)
     - `getWebhookSecret()`: Mock to return empty string (bypasses signature validation in tests)

#### **File:** `api/schema.graphql`
   - **Summary:** GraphQL schema definition for the Village Calendar API. Defines all query/mutation types, input types, and entity types.
   - **Recommendation:** Use this schema as the source of truth for all GraphQL API tests. Your test mutations MUST match the exact schema syntax (field names, required fields, input structure).
   - **Key Mutations to Test:**
     - `createOrder(calendarId, quantity, shippingAddress)`: Returns PaymentIntent type
     - `cancelOrder(orderId, reason)`: Returns CalendarOrder type
     - `updateOrderStatus(id, input: OrderUpdateInput)`: Returns CalendarOrder type (admin only)
   - **Key Queries to Test:**
     - `allOrders(status, limit)`: Returns list of CalendarOrder (admin only)
     - `orders(userId, status)`: Returns list of CalendarOrder for specific user or current user

### Implementation Tips & Notes

#### **Tip 1: Test Execution Order**
All three test files use `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` and annotate individual tests with `@org.junit.jupiter.api.Order(N)`. This ensures deterministic test execution order. Your new test methods SHOULD follow this pattern if order matters (e.g., creating data in one test, querying it in the next).

#### **Tip 2: Test Data Cleanup**
All tests use `@AfterEach @Transactional void cleanup()` to delete test data in reverse foreign key order:
1. Delete DelayedJob entries referencing orders
2. Delete CalendarOrder entries
3. Delete UserCalendar entries
4. Delete CalendarUser entries
5. Delete CalendarTemplate entries

**IMPORTANT:** If tests fail during execution, cleanup may not run. Ensure your test database is reset between full test runs. The pattern used is:
```java
DelayedJob.delete("actorId IN (SELECT CAST(id AS VARCHAR) FROM calendar_orders WHERE calendar_id = ?1)", testCalendar.id);
```

#### **Tip 3: GraphQL Testing Pattern with REST Assured**
All GraphQL tests use REST Assured to POST to `/graphql` endpoint:
```java
String mutation = """
    mutation {
        createOrder(
            calendarId: "%s"
            quantity: 1
            shippingAddress: { ... }
        ) {
            id
            clientSecret
            amount
        }
    }
    """.formatted(testCalendar.id.toString());

given()
    .contentType(ContentType.JSON)
    .body(Map.of("query", mutation))
    .when()
    .post("/graphql")
    .then()
    .statusCode(200)
    .body("errors", nullValue())
    .body("data.createOrder.id", notNullValue());
```

**YOU MUST** use this exact pattern. DO NOT use GraphQL client libraries - REST Assured is the standard.

#### **Tip 4: Webhook Simulation**
Webhook tests simulate Stripe webhook events by POSTing JSON to `/api/webhooks/stripe`:
```java
String webhookPayload = """
    {
      "id": "evt_test_webhook",
      "type": "checkout.session.completed",
      "data": {
        "object": {
          "id": "cs_test_session",
          "payment_intent": "%s"
        }
      }
    }
    """.formatted(testPaymentIntentId);

given()
    .contentType(ContentType.JSON)
    .header("Stripe-Signature", "t=123,v1=fake")
    .body(webhookPayload)
    .when()
    .post("/api/webhooks/stripe")
    .then()
    .statusCode(200);
```

Mock `paymentService.getWebhookSecret()` to return empty string to bypass signature validation.

#### **Tip 5: Idempotent Webhook Testing**
Payment webhook tests verify idempotency by:
1. Sending webhook event once → verify order updated to PAID
2. Sending same webhook again → verify order still PAID (not modified)
3. Verify only ONE email job exists (not duplicated)

Mock `processPaymentSuccess()` to return true first time, false second time:
```java
when(paymentService.processPaymentSuccess(eq(testPaymentIntentId), anyString()))
    .thenReturn(true)
    .thenReturn(false);
```

#### **Tip 6: Authorization Testing**
Admin tests create 3 users: testUser (regular), testUser2 (regular), adminUser (admin). Authorization tests verify:
- User can cancel OWN order: `orderService.cancelOrder(orderId, testUser.id, false, reason)` succeeds
- User CANNOT cancel OTHER user's order: throws `SecurityException` with message containing "not authorized"
- Admin CAN cancel ANY order: `orderService.cancelOrder(orderId, adminUser.id, true, reason)` succeeds

**IMPORTANT:** Current tests use `orderService` directly with userId and isAdmin parameters because JWT authentication is not easily testable in @QuarkusTest. This is acceptable - verify business logic at service layer.

#### **Tip 7: Entity Manager Flushing**
Admin workflow tests frequently use:
```java
entityManager.flush();
entityManager.clear();
```

This ensures database writes are committed and entities are detached before re-querying. Without this, Hibernate may return cached entities instead of fresh database results. USE THIS PATTERN when testing database persistence.

#### **Warning: Test Database State**
All tests run against the same Quarkus test database. While `@Transactional` cleanup methods delete test data, there is a risk of test pollution if:
1. Test creates data with same unique constraints (e.g., order numbers, oauth subjects)
2. Cleanup fails mid-test
3. Tests are not properly isolated

**MITIGATION:** All test data uses unique timestamps in string fields:
```java
testUser.oauthSubject = "order-test-" + System.currentTimeMillis();
testPaymentIntentId = "pi_test_" + System.currentTimeMillis();
```

**YOU MUST** follow this pattern for all unique fields to prevent test pollution.

#### **Note: Code Coverage Verification**
The acceptance criteria states: "Tests achieve >70% coverage for order/payment code". To verify coverage:
```bash
./mvnw clean test jacoco:report
```

Then open `target/site/jacoco/index.html` and check:
- `villagecompute.calendar.services.OrderService`: Should show >70% line coverage
- `villagecompute.calendar.services.PaymentService`: Should show >70% line coverage
- `villagecompute.calendar.api.graphql.OrderResolver`: Should show >70% line coverage

If coverage is below 70%, identify uncovered branches/methods and add tests targeting those code paths.

---

## 4. Task Completion Checklist

Before marking this task as complete, verify ALL acceptance criteria:

- [ ] **Order placement test** creates order, simulates Stripe webhook, verifies order PAID
- [ ] **Payment success test** verifies Payment entity created with correct amount (NOTE: Current architecture stores payment in Order entity via `stripePaymentIntentId` and `stripeChargeId` fields, not separate Payment entity - adjust this criteria accordingly)
- [ ] **Email job enqueued** on payment success (verifiable in delayed_jobs table)
- [ ] **Cancel order test** processes refund, updates order status to CANCELLED
- [ ] **Admin workflow test** updates order status, adds tracking number, verifies persistence
- [ ] **All integration tests pass** with `./mvnw verify`
- [ ] **Tests run in <90 seconds** (measure with `time ./mvnw verify`)
- [ ] **Code coverage >70%** for order/payment services and API layer (verify with JaCoCo report)

---

## 5. Final Notes

This task is **I3.T9** - the final task in Iteration 3. Upon successful completion:
- All e-commerce workflows will have comprehensive integration test coverage
- The CI/CD pipeline will automatically catch regressions in order/payment/admin workflows
- The codebase will meet quality gates for production deployment
- The next iteration (I4) can begin with confidence in e-commerce foundation

**CRITICAL REMINDER:** The three target test files ALREADY EXIST with comprehensive tests. Your primary job is to:
1. **REVIEW** all existing tests to understand coverage
2. **VERIFY** all acceptance criteria are met by existing tests
3. **ADD** any missing test scenarios if acceptance criteria gaps exist
4. **RUN** all tests and verify they pass: `./mvnw verify`
5. **CHECK** code coverage meets >70% threshold: `./mvnw jacoco:report`

DO NOT delete or significantly refactor existing tests unless they are broken. The tests follow established patterns and conventions used throughout the project.
