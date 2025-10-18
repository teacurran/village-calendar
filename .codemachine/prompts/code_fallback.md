# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create end-to-end integration tests for complete e-commerce workflows using Quarkus test framework with REST Assured. Test scenarios: (1) Place order and payment success - create calendar, place order, simulate Stripe webhook (checkout.session.completed), verify order status PAID, verify email job enqueued; (2) Cancel order - create order, cancel order, verify status CANCELLED, verify refund processed (mock Stripe API); (3) Admin order management - admin queries all orders, updates order status to SHIPPED, adds tracking number, verifies changes persisted. Use Testcontainers for PostgreSQL, mock Stripe API for webhook simulation. Achieve >70% code coverage for order/payment services and API layer.

---

## Issues Detected

### 1. **GraphQL Mutation Syntax Errors - Multiple Test Files**

The `createOrder` GraphQL mutation requires an `input` parameter with an `OrderInput` object, NOT flat parameters. The schema.graphql file is OUT OF DATE - the actual GraphQL resolver implementation (OrderGraphQL.java:293-298) uses `OrderInput` with a single `input` parameter.

**Failed Tests:**
- `OrderWorkflowTest.testCreateOrder_GraphQL_ReturnsPendingOrder()` - GraphQL validation errors
- `OrderWorkflowTest.testCreateOrder_CalculatesTotalWithShipping()` - GraphQL validation errors
- `PaymentWorkflowTest.testPaymentIntentCreation_ReturnsValidIntent()` - GraphQL validation errors

**Error Message:**
```
Missing field argument 'input'
Unknown field argument 'calendarId', 'quantity', 'shippingAddress'
Field 'id', 'amount', 'status' in type 'CreateOrderResponse' is undefined
```

**Root Cause:**
Tests use incorrect mutation syntax:
```graphql
# INCORRECT (currently in tests):
mutation {
    createOrder(
        calendarId: "uuid"
        quantity: 1
        shippingAddress: { ... }
    ) {
        id
        amount
        status
    }
}

# CORRECT (required by actual GraphQL resolver):
mutation {
    createOrder(
        input: {
            calendarId: "uuid"
            quantity: 1
            shippingAddress: { ... }
        }
    ) {
        order {
            id
            status
        }
        clientSecret
    }
}
```

**Files to Fix:**
- `OrderWorkflowTest.java` - lines 147-166, 377-395
- `PaymentWorkflowTest.java` - lines 396-415

### 2. **JPQL Query Errors in Cleanup Methods**

The cleanup methods use SQL table names (`calendar_orders`) instead of JPA entity names (`CalendarOrder`). Panache/Hibernate requires entity names in JPQL queries.

**Failed Tests:**
- ALL tests in `AdminOrderWorkflowTest` fail during cleanup
- ALL tests in `PaymentWorkflowTest` fail during cleanup (when calendar has orders)

**Error Message:**
```
org.hibernate.query.sqm.UnknownEntityException: Could not resolve root entity 'calendar_orders'
```

**Root Cause:**
```java
// INCORRECT (line 113, 149, 154):
DelayedJob.delete("actorId IN (SELECT CAST(id AS VARCHAR) FROM calendar_orders WHERE calendar_id = ?1)", testCalendar.id);
CalendarOrder.delete("calendar.id", testCalendar.id);
```

**Correct Approach:**
Use simpler Panache query patterns:
```java
// First delete delayed jobs by finding orders, then deleting jobs
List<CalendarOrder> orders = CalendarOrder.find("calendar.id", testCalendar.id).list();
for (CalendarOrder order : orders) {
    DelayedJob.delete("actorId", order.id.toString());
}
// Then delete orders
CalendarOrder.delete("calendar.id", testCalendar.id);
```

**Files to Fix:**
- `OrderWorkflowTest.java` - lines 113, cleanup method
- `PaymentWorkflowTest.java` - line 112, cleanup method
- `AdminOrderWorkflowTest.java` - lines 149, 154, cleanup method

### 3. **Webhook Endpoint Validation Failures**

The webhook endpoint `/api/webhooks/stripe` returns 400 Bad Request because the mocked PaymentService still requires proper webhook structure validation.

**Failed Tests:**
- `PaymentWorkflowTest.testRefundWebhook_UpdatesOrderNotes()` - 400 error

**Root Cause:**
The `WebhookResource` expects specific Stripe webhook event structure and may validate fields beyond just signature. The mock isn't comprehensive enough.

**Solution:**
Either:
1. Mock the PaymentService.processRefundWebhook() method to skip validation
2. Use more realistic webhook payloads that match Stripe's exact structure
3. For refund webhook test, verify the behavior through service layer directly instead of REST endpoint

### 4. **Authentication/Authorization Errors**

Some tests fail with `UnauthorizedException` because GraphQL mutations require valid JWT authentication (`@RolesAllowed("USER")`), but tests don't provide authentication.

**Failed Tests:**
- `OrderWorkflowTest.testCreateOrder_GraphQL_ReturnsPendingOrder()` - Unauthorized
- `OrderWorkflowTest.testCreateOrder_CalculatesTotalWithShipping()` - Unauthorized
- `PaymentWorkflowTest.testCancelOrder_ProcessesRefund()` - Unauthorized
- Several others

**Root Cause:**
The GraphQL mutations are annotated with `@RolesAllowed("USER")` (see OrderGraphQL.java:291, 385, 472) which requires JWT authentication. The tests don't provide authentication headers.

**Solution:**
Tests should either:
1. **Call service layer directly** instead of GraphQL API (bypasses authentication) - RECOMMENDED for most tests
2. Or create mock JWT tokens (complex, not recommended for integration tests)

Example pattern for service-layer testing:
```java
// INSTEAD OF GraphQL mutation:
String mutation = "mutation { createOrder(...) }";
given().body(Map.of("query", mutation)).post("/graphql");

// USE service methods directly:
@Inject
OrderService orderService;

CalendarOrder order = orderService.createOrder(
    testUser,
    testCalendar,
    quantity,
    unitPrice,
    shippingAddress
);
```

### 5. **Test Coverage Target Not Met**

The task requires >70% code coverage for OrderService and PaymentService, but many tests fail so coverage cannot be measured.

---

## Best Approach to Fix

### Step 1: Fix GraphQL Mutation Syntax (High Priority)

Update ALL GraphQL `createOrder` mutations to use the correct syntax with `input` parameter and `OrderInput` structure:

```java
String mutation = """
    mutation {
        createOrder(
            input: {
                calendarId: "%s"
                quantity: %d
                shippingAddress: {
                    street: "123 Main St"
                    city: "Nashville"
                    state: "TN"
                    postalCode: "37203"
                    country: "US"
                }
            }
        ) {
            order {
                id
                status
                quantity
                orderNumber
            }
            clientSecret
        }
    }
    """.formatted(calendarId, quantity);
```

**Files:** `OrderWorkflowTest.java`, `PaymentWorkflowTest.java`

### Step 2: Fix JPQL Cleanup Queries (High Priority)

Replace complex subquery-based deletes with simple two-step cleanup:

```java
@AfterEach
@Transactional
void cleanup() {
    // Step 1: Delete delayed jobs for orders in this calendar
    if (testCalendar != null && testCalendar.id != null) {
        List<CalendarOrder> orders = CalendarOrder.find("calendar.id", testCalendar.id).list();
        for (CalendarOrder order : orders) {
            DelayedJob.delete("actorId", order.id.toString());
        }
        // Step 2: Delete orders
        CalendarOrder.delete("calendar.id", testCalendar.id);
        // Step 3: Delete calendar
        UserCalendar.deleteById(testCalendar.id);
    }
    if (testUser != null && testUser.id != null) {
        CalendarUser.deleteById(testUser.id);
    }
    if (testTemplate != null && testTemplate.id != null) {
        CalendarTemplate.deleteById(testTemplate.id);
    }
}
```

**Files:** All three test files

### Step 3: Replace GraphQL Calls with Service Layer Calls (Critical)

For tests that fail with authentication errors, bypass the GraphQL/REST layer and call services directly. This is standard practice for integration tests focusing on business logic:

**Pattern to use:**
```java
@Inject
OrderService orderService;

@Inject
PaymentService paymentService;

@Test
@Transactional
void testCreateOrder() {
    // Create order via service (no authentication needed)
    CalendarOrder order = orderService.createOrder(
        testUser,
        testCalendar,
        1,  // quantity
        new BigDecimal("25.00"),  // unitPrice
        shippingAddress
    );

    // Verify order created
    assertEquals(CalendarOrder.STATUS_PENDING, order.status);
}
```

**Tests to modify:**
- `OrderWorkflowTest.testCreateOrder_GraphQL_ReturnsPendingOrder()` - Use orderService.createOrder()
- `OrderWorkflowTest.testCreateOrder_CalculatesTotalWithShipping()` - Use orderService.createOrder()
- `PaymentWorkflowTest.testCancelOrder_ProcessesRefund()` - Use orderService.cancelOrder()
- `PaymentWorkflowTest.testCancelOrder_OnlyPendingOrPaid_Allowed()` - Use orderService.cancelOrder()

**Keep GraphQL for:**
- Webhook tests (POST to `/api/webhooks/stripe`) - these are REST endpoints, not GraphQL

### Step 4: Fix Webhook Test Expectations

For `testRefundWebhook_UpdatesOrderNotes()`, either:

**Option A:** Test via service layer:
```java
@Test
@Transactional
void testRefundProcessing_UpdatesOrderNotes() {
    // Given: A paid order with charge ID
    CalendarOrder order = ... // create order

    // When: Process refund via service (not webhook endpoint)
    paymentService.processRefund(order.stripePaymentIntentId, null, "Customer requested");

    // Then: Verify order notes updated
    CalendarOrder updated = CalendarOrder.findById(order.id);
    assertNotNull(updated.notes);
    assertTrue(updated.notes.contains("refund"));
}
```

**Option B:** Fix webhook payload and mock properly:
Ensure PaymentService.processRefundWebhook() is mocked if testing webhook endpoint directly.

### Step 5: Verify Test Coverage

After fixes, run:
```bash
./mvnw clean test -Dtest="OrderWorkflowTest,PaymentWorkflowTest,AdminOrderWorkflowTest"
./mvnw jacoco:report
```

Check `target/site/jacoco/index.html` to verify >70% coverage on:
- `villagecompute.calendar.services.OrderService`
- `villagecompute.calendar.services.PaymentService`

---

## Summary

**Critical Fixes Required:**
1. Change all `createOrder` mutations to use `input: { ... }` structure with `CreateOrderResponse` return type
2. Fix JPQL cleanup queries to use entity names, not table names
3. Replace GraphQL API calls with direct service calls for authenticated endpoints
4. Simplify webhook tests to avoid authentication/validation complexity

**Expected Outcome:**
- All 23 tests pass
- Tests run in <90 seconds
- Code coverage >70% for OrderService and PaymentService
- No authentication errors (tests use service layer)
- No JPQL errors (use Panache entity names)
