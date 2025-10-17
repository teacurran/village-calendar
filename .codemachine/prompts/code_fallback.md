# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create PaymentService for handling Stripe payment processing: createPayment (store Payment record linked to Order), processPaymentSuccess (on Stripe webhook, update Payment and Order status to PAID), processRefund (create refund via Stripe API, update Payment record). Implement StripeWebhookController (REST endpoint POST /api/webhooks/stripe) to receive Stripe webhook events: checkout.session.completed (payment succeeded), charge.refunded (refund processed). Add webhook signature validation to prevent spoofing. Implement idempotent webhook processing (handle duplicate webhook delivery). Enqueue email job on payment success (order confirmation email). Write integration tests for webhook handling.

---

## Issues Detected

*   **Test Failure:** `testWebhook_CheckoutSessionCompleted_UpdatesOrderStatus` - Order status remains PENDING instead of being updated to PAID
*   **Test Failure:** `testWebhook_PaymentIntentSucceeded_UpdatesOrderStatus` - Returns 500 error with "Order not found for PaymentIntent: pi_test_direct_flow"
*   **Test Failure:** `testWebhook_ChargeRefunded_UpdatesOrderNotes` - Order notes are null, refund not being recorded
*   **Test Failure:** `testWebhook_ChargeRefunded_IdempotentProcessing` - Refund not recorded (count is 0 instead of 1)
*   **Root Cause:** Test data created in `@BeforeEach @Transactional setup()` method is not visible to the REST endpoint when called via RestAssured HTTP request. The test transaction is not committed before the HTTP request is made, so the webhook handler runs in a separate transaction and cannot see the test data.

---

## Best Approach to Fix

You MUST modify the test file `src/test/java/villagecompute/calendar/api/rest/StripeWebhookControllerTest.java` to fix the transaction isolation issue:

### Solution 1: Use QuarkusTransaction for Programmatic Transactions

Replace the `@Transactional` annotation on the `setup()` method with programmatic transaction management using `io.quarkus.narayana.jta.QuarkusTransaction`:

1. Remove `@Transactional` annotation from the `setup()` method
2. Wrap the setup code in `QuarkusTransaction.requiringNew().run(() -> { ... })` to ensure data is committed
3. This ensures test data is persisted and visible to subsequent HTTP requests

Example fix:

```java
@BeforeEach
void setup() throws Exception {
    // Wrap in programmatic transaction that commits immediately
    QuarkusTransaction.requiringNew().run(() -> {
        try {
            // Create test user
            testUser = new CalendarUser();
            testUser.oauthProvider = "GOOGLE";
            testUser.oauthSubject = "webhook-test-" + System.currentTimeMillis();
            testUser.email = "webhook-test-" + System.currentTimeMillis() + "@example.com";
            testUser.displayName = "Webhook Test User";
            testUser.persist();

            // Create test template
            testTemplate = new CalendarTemplate();
            testTemplate.name = "Webhook Test Template";
            testTemplate.description = "Template for webhook testing";
            testTemplate.isActive = true;
            testTemplate.isFeatured = false;
            testTemplate.displayOrder = 1;
            testTemplate.configuration = createTestConfiguration();
            testTemplate.persist();

            // Create test calendar
            testCalendar = calendarService.createCalendar(
                "Test Calendar for Order",
                2025,
                testTemplate.id,
                null,
                true,
                testUser,
                null
            );

            // Create test order in PENDING status
            JsonNode shippingAddress = objectMapper.readTree("""
                {
                    "line1": "123 Test St",
                    "city": "Test City",
                    "state": "CA",
                    "postalCode": "12345",
                    "country": "US"
                }
                """);

            testOrder = orderService.createOrder(
                testUser,
                testCalendar,
                1,
                new BigDecimal("19.99"),
                shippingAddress
            );

            // Set payment intent ID for webhook correlation
            testOrder.stripePaymentIntentId = TEST_PAYMENT_INTENT_ID;
            testOrder.persist();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });
}
```

4. Add the import statement at the top of the file:
```java
import io.quarkus.narayana.jta.QuarkusTransaction;
```

5. Similarly, update ANY test method that uses `@Transactional` and makes HTTP calls to use programmatic transactions instead, OR remove `@Transactional` from those methods entirely and let the cleanup method handle transaction management.

6. Keep `@Transactional` on the `cleanup()` method as it doesn't make HTTP calls and just needs to clean up the database.

7. For test methods that need to update test data before making HTTP calls (like `testWebhook_ChargeRefunded_UpdatesOrderNotes`), wrap the setup code in `QuarkusTransaction.requiringNew().run(() -> { ... })` to commit changes before the HTTP request.

### Critical Implementation Notes:

- The test currently uses `@BeforeEach @Transactional` which keeps the transaction open throughout the entire test method execution
- When RestAssured makes an HTTP call to `/api/webhooks/stripe`, the REST endpoint runs in a DIFFERENT transaction and cannot see uncommitted data from the test transaction
- Using `QuarkusTransaction.requiringNew().run()` forces an immediate commit, making the data visible to subsequent HTTP requests
- This pattern is REQUIRED for all integration tests that create data and then make HTTP requests to REST endpoints

### Additional Fixes Needed:

1. In `testWebhook_PaymentIntentSucceeded_UpdatesOrderStatus`, the order created within the test method needs to be committed before the HTTP call:
   - Wrap the order creation in `QuarkusTransaction.requiringNew().run(() -> { ... })`

2. In `testWebhook_ChargeRefunded_UpdatesOrderNotes` and `testWebhook_ChargeRefunded_IdempotentProcessing`, the order status update needs to be committed before the HTTP call:
   - Wrap the order update code in `QuarkusTransaction.requiringNew().run(() -> { ... })`

This will ensure all test data is committed and visible to the webhook handler, allowing all tests to pass.
