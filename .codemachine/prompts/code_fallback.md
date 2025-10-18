# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create end-to-end integration tests for complete e-commerce workflows using Quarkus test framework with REST Assured. Test scenarios: (1) Place order and payment success - create calendar, place order, simulate Stripe webhook (checkout.session.completed), verify order status PAID, verify email job enqueued; (2) Cancel order - create order, cancel order, verify status CANCELLED, verify refund processed (mock Stripe API); (3) Admin order management - admin queries all orders, updates order status to SHIPPED, adds tracking number, verifies changes persisted. Use Testcontainers for PostgreSQL, mock Stripe API for webhook simulation. Achieve >70% code coverage for order/payment services and API layer.

---

## Issues Detected

*   **Test Failure:** 3 webhook tests in `OrderWorkflowTest.java` are failing with HTTP 400 errors:
    - `testWebhookPaymentSuccess_UpdatesOrderToPaid`
    - `testWebhookPaymentSuccess_EnqueuesEmailJob`
    - `testWebhookIdempotency_DoesNotDuplicateProcessing`

*   **Root Cause:** The webhook tests are sending simplified JSON payloads and mocking `paymentService.getWebhookSecret()` to return empty string. However, the Stripe SDK's `Webhook.constructEvent()` method requires:
    1. Proper Stripe event structure (including `"object": "event"`, `"api_version"`, etc.)
    2. Valid HMAC-SHA256 signature matching the real webhook secret

    The current approach fails because even with an empty webhook secret, the Stripe SDK validates the JSON structure and rejects simplified payloads.

---

## Best Approach to Fix

You MUST update the failing webhook tests in `src/test/java/villagecompute/calendar/integration/OrderWorkflowTest.java` to follow the **exact pattern** used in `src/test/java/villagecompute/calendar/api/rest/StripeWebhookControllerTest.java`.

### Required Changes:

1. **Add webhook secret injection** at the top of `OrderWorkflowTest` class:
   ```java
   @ConfigProperty(name = "stripe.webhook.secret")
   String webhookSecret;
   ```

2. **Add signature generation method** (copy from `StripeWebhookControllerTest.java`):
   ```java
   private String generateStripeSignature(String payload) throws Exception {
       long timestamp = System.currentTimeMillis() / 1000;
       String signedPayload = timestamp + "." + payload;

       Mac mac = Mac.getInstance("HmacSHA256");
       SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256");
       mac.init(secretKey);
       byte[] hash = mac.doFinal(signedPayload.getBytes());
       String signature = HexFormat.of().formatHex(hash);

       return "t=" + timestamp + ",v1=" + signature;
   }
   ```

3. **Update webhook payload format** in all 3 failing tests to use proper Stripe event structure:
   ```java
   String webhookPayload = String.format("""
       {
           "id": "evt_test_webhook",
           "object": "event",
           "api_version": "2024-11-20.acacia",
           "created": %d,
           "type": "checkout.session.completed",
           "data": {
               "object": {
                   "id": "cs_test_session",
                   "object": "checkout.session",
                   "payment_intent": "%s",
                   "payment_status": "paid",
                   "status": "complete"
               }
           }
       }
       """, System.currentTimeMillis() / 1000, testPaymentIntentId);
   ```

4. **Generate valid signature** before sending webhook:
   ```java
   String signature = generateStripeSignature(webhookPayload);

   given()
       .contentType(ContentType.JSON)
       .header("Stripe-Signature", signature)  // Use real signature, not "t=123,v1=fake"
       .body(webhookPayload)
       .when()
       .post("/api/webhooks/stripe")
       .then()
       .statusCode(200);
   ```

5. **Remove mock of `getWebhookSecret()`** - The test should use the real configured webhook secret instead of mocking it to return empty string. Delete this line from the `setup()` method:
   ```java
   when(paymentService.getWebhookSecret()).thenReturn("");  // DELETE THIS
   ```

6. **Add required imports** at the top of the file:
   ```java
   import org.eclipse.microprofile.config.inject.ConfigProperty;
   import javax.crypto.Mac;
   import javax.crypto.spec.SecretKeySpec;
   import java.util.HexFormat;
   ```

### Example of Complete Fixed Test:

Reference lines 231-278 in `StripeWebhookControllerTest.java` for the exact pattern. Your updated `testWebhookPaymentSuccess_UpdatesOrderToPaid` should look very similar to `testWebhook_CheckoutSessionCompleted_UpdatesOrderStatus`.

### Verification:

After making these changes, run the tests:
```bash
./mvnw test -Dtest=OrderWorkflowTest
```

All 5 tests should pass with this output:
```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```
