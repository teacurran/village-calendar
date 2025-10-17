# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create PaymentService for handling Stripe payment processing: createPayment (store Payment record linked to Order), processPaymentSuccess (on Stripe webhook, update Payment and Order status to PAID), processRefund (create refund via Stripe API, update Payment record). Implement StripeWebhookController (REST endpoint POST /api/webhooks/stripe) to receive Stripe webhook events: checkout.session.completed (payment succeeded), charge.refunded (refund processed). Add webhook signature validation to prevent spoofing. Implement idempotent webhook processing (handle duplicate webhook delivery). Enqueue email job on payment success (order confirmation email). Write integration tests for webhook handling.

---

## Issues Detected

### Test Failures (6 tests failing)

All webhook processing tests are failing with HTTP 500 errors due to a `ClassCastException` when deserializing Stripe webhook events:

*   **Test Failure:** `StripeWebhookControllerTest.testWebhook_CheckoutSessionCompleted_UpdatesOrderStatus` - Expected 200 but got 500
*   **Test Failure:** `StripeWebhookControllerTest.testWebhook_CheckoutSessionCompleted_EnqueuesEmailJob` - Expected 200 but got 500
*   **Test Failure:** `StripeWebhookControllerTest.testWebhook_IdempotentProcessing_NoDuplicateUpdates` - Expected 200 but got 500
*   **Test Failure:** `StripeWebhookControllerTest.testWebhook_PaymentIntentSucceeded_UpdatesOrderStatus` - Expected 200 but got 500
*   **Test Failure:** `StripeWebhookControllerTest.testWebhook_ChargeRefunded_UpdatesOrderNotes` - Expected 200 but got 500
*   **Test Failure:** `StripeWebhookControllerTest.testWebhook_ChargeRefunded_IdempotentProcessing` - Expected 200 but got 500

### Root Cause Analysis

The stack trace shows:
```
java.lang.ClassCastException: class com.stripe.model.StripeObject cannot be cast to class com.stripe.model.checkout.Session
	at villagecompute.calendar.api.rest.WebhookResource.handleCheckoutSessionCompleted(WebhookResource.java:195)
```

**Problem:** In `WebhookResource.java`, the methods `handleCheckoutSessionCompleted()`, `handlePaymentIntentSucceeded()`, and `handleChargeRefunded()` are directly casting the result of `event.getDataObjectDeserializer().getObject().orElseThrow()` to specific Stripe types (Session, PaymentIntent, Charge). However, when `Webhook.constructEvent()` is called with test webhook signatures and payloads, the Stripe SDK may not fully deserialize these objects to their specific types, leaving them as generic `StripeObject` instances.

**Specific Issues:**
1. **Line 195-197 in WebhookResource.java:** Direct cast to `Session` fails
2. **Line 220-222:** Direct cast to `PaymentIntent` fails
3. **Line 276-278:** Direct cast to `Charge` fails

The Stripe SDK's `getDataObjectDeserializer().getObject()` returns an `Optional<StripeObject>`, not the specific typed object. When working with webhook events, especially in test environments, you need to either:
- Use the raw JSON from `event.getData()` and manually parse it
- Use Stripe's API classes to retrieve the objects by ID after extracting the ID from the event data
- Use `deserializeUnsafe()` with proper type handling

---

## Best Approach to Fix

### Fix the WebhookResource.java Event Deserialization

You MUST modify the event handling methods in `src/main/java/villagecompute/calendar/api/rest/WebhookResource.java` to safely extract data from webhook events without relying on direct type casting. Use one of these approaches:

#### Approach 1: Parse JSON Directly (Recommended for Tests)

Instead of casting the deserialized object, parse the event's JSON data directly:

```java
private void handleCheckoutSessionCompleted(Event event) {
    // Parse the data object as JSON
    JsonObject dataObject = event.getDataObjectDeserializer()
        .getObject()
        .map(obj -> new Gson().toJsonTree(obj).getAsJsonObject())
        .orElseThrow(() -> new IllegalStateException("Missing checkout session data"));

    String sessionId = dataObject.get("id").getAsString();
    String paymentIntentId = dataObject.get("payment_intent").getAsString();

    LOG.infof("Checkout session completed: %s (PaymentIntent: %s)", sessionId, paymentIntentId);

    if (paymentIntentId == null || paymentIntentId.equals("null")) {
        LOG.errorf("Checkout session %s has no PaymentIntent", sessionId);
        return;
    }

    paymentService.processPaymentSuccess(paymentIntentId, null);
}
```

Apply the same pattern to:
- `handlePaymentIntentSucceeded()` - extract `id` and `latest_charge` from JSON
- `handleChargeRefunded()` - extract `id` and navigate `refunds.data[0]` from JSON

#### Approach 2: Use deserializeUnsafe() with Type Information

Alternatively, use `deserializeUnsafe()` with the API version:

```java
private void handleCheckoutSessionCompleted(Event event) {
    Session session = (Session) event.getDataObjectDeserializer()
        .deserializeUnsafe();

    if (session == null) {
        throw new IllegalStateException("Failed to deserialize checkout session");
    }

    String sessionId = session.getId();
    String paymentIntentId = session.getPaymentIntent();
    // ... rest of the code
}
```

### Detailed Instructions

1. **Update `handleCheckoutSessionCompleted()` in WebhookResource.java (line 194-210):**
   - Replace the direct cast on line 195-197
   - Use JSON parsing or `deserializeUnsafe()` to safely extract session data
   - Extract `payment_intent` field from the session object

2. **Update `handlePaymentIntentSucceeded()` in WebhookResource.java (line 219-230):**
   - Replace the direct cast on line 220-222
   - Use JSON parsing or `deserializeUnsafe()` to safely extract payment intent data
   - Extract `id` and `latest_charge` fields

3. **Update `handleChargeRefunded()` in WebhookResource.java (line 275-298):**
   - Replace the direct cast on line 276-278
   - Use JSON parsing or `deserializeUnsafe()` to safely extract charge data
   - Navigate to `refunds.data[0]` to get refund details (id and amount)

4. **Ensure null safety:** Add null checks after extracting payment intent IDs, charge IDs, and refund data to prevent NPEs.

5. **Test the changes:** Run `mvn test -Dtest=StripeWebhookControllerTest` to verify all 9 tests pass.

### Additional Notes

- The `handlePaymentIntentFailed()` method (line 238-266) uses the same casting pattern but wasn't tested. You SHOULD apply the same fix to that method for consistency.
- The Stripe SDK's behavior differs between production (real webhook events) and test environments (manually constructed events). The JSON parsing approach is more robust across both environments.
- Do NOT modify the signature validation logic - that part is working correctly (tests for invalid signatures pass).
- Do NOT modify PaymentService - it's working correctly.
- The integration tests are well-written and comprehensive - they just need the WebhookResource deserialization to be fixed.
