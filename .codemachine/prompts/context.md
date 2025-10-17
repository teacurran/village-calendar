# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T3",
  "iteration_id": "I3",
  "iteration_goal": "Implement complete e-commerce workflow including Stripe payment integration, order placement, order management dashboard (admin), and transactional email notifications",
  "description": "Create PaymentService for handling Stripe payment processing: createPayment (store Payment record linked to Order), processPaymentSuccess (on Stripe webhook, update Payment and Order status to PAID), processRefund (create refund via Stripe API, update Payment record). Implement StripeWebhookController (REST endpoint POST /api/webhooks/stripe) to receive Stripe webhook events: checkout.session.completed (payment succeeded), charge.refunded (refund processed). Add webhook signature validation to prevent spoofing. Implement idempotent webhook processing (handle duplicate webhook delivery). Enqueue email job on payment success (order confirmation email). Write integration tests for webhook handling.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Payment entity from I1.T8, OrderService from Task I3.T2, StripeService from Task I3.T1, Webhook requirements from Plan Section \"Payment Processing\"",
  "target_files": [
    "src/main/java/villagecompute/calendar/services/PaymentService.java",
    "src/main/java/villagecompute/calendar/api/rest/StripeWebhookController.java",
    "src/test/java/villagecompute/calendar/api/rest/StripeWebhookControllerTest.java"
  ],
  "input_files": [
    "src/main/java/villagecompute/calendar/integration/stripe/StripeService.java",
    "src/main/java/villagecompute/calendar/service/OrderService.java",
    "src/main/java/villagecompute/calendar/model/Payment.java"
  ],
  "deliverables": "PaymentService with payment processing logic, Webhook endpoint handling Stripe events, Idempotent webhook processing (duplicate handling), Email job enqueuing on payment success, Integration tests for webhook scenarios",
  "acceptance_criteria": "Webhook endpoint validates Stripe signature, rejects invalid requests, checkout.session.completed event creates Payment record, updates Order status to PAID, Duplicate webhook delivery (same event ID) does not create duplicate payments, Payment success triggers email job enqueue (order confirmation), Integration test simulates Stripe webhook POST, verifies order status change, charge.refunded webhook updates Payment record with refund amount",
  "dependencies": ["I3.T1", "I3.T2"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Stripe Payment Integration Overview (from stripe-setup.md)

The Village Calendar application uses Stripe for payment processing to handle calendar orders. The integration uses **Stripe Checkout Sessions**, which provide:

- **PCI DSS Compliance**: Card data never touches your servers (handled by Stripe's hosted payment page)
- **Secure Payment Flow**: Customers are redirected to Stripe's secure checkout page
- **Webhook Notifications**: Receive real-time updates when payments succeed or fail
- **Test Mode**: Safely test the integration without processing real payments

**Payment Flow:**

1. Customer creates an order for a custom calendar
2. Application creates a Stripe Checkout Session via `StripeService.createCheckoutSession()`
3. Customer is redirected to Stripe's hosted payment page
4. Customer enters payment details and completes purchase
5. Stripe redirects customer back to success/cancel URL
6. **Stripe sends webhook notification to application**
7. **Application validates webhook signature and updates order status**
8. **Order is marked as paid and ready for fulfillment**

### Context: Webhook Events to Handle (from stripe-setup.md)

Select the following events:

- **checkout.session.completed**: Fired when a Checkout Session is successfully completed
- **checkout.session.expired**: Fired when a Checkout Session expires (24 hours)
- **payment_intent.succeeded**: Fired when a payment succeeds
- **payment_intent.payment_failed**: Fired when a payment fails
- **charge.refunded**: Fired when a refund is processed

### Context: Security - Webhook Signature Validation (from stripe-setup.md)

**Always Validate Webhook Signatures**

The application uses Stripe SDK's `Webhook.constructEvent()` to verify webhooks are from Stripe.

**Never process webhooks without validation** - this prevents fraudulent order completion.

**Webhook Signing Secret:**
- **Format**: Starts with `whsec_`
- **Example**: `whsec_1234567890abcdefghijklmnopqrstuvwxyz`
- **Purpose**: Used to verify webhook signatures and prevent spoofing
- **Security**: Keep secret, never expose in frontend

### Context: Idempotency Requirement (from stripe-setup.md)

The application uses idempotency keys to prevent duplicate charges:

```java
String idempotencyKey = "checkout_order_" + order.id + "_" + System.currentTimeMillis();
```

This ensures that network retries don't create duplicate checkout sessions.

**For webhooks**: Stripe may send the same webhook event multiple times. Your application MUST handle duplicate deliveries idempotently (processing the same event twice should have the same effect as processing it once).

### Context: GraphQL Schema - Order and Payment Types (from schema.graphql)

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
  calendar: UserCalendar!
  created: DateTime!
  deliveredAt: DateTime
  id: ID!
  notes: String
  paidAt: DateTime
  quantity: Int!
  shippedAt: DateTime
  shippingAddress: JSON!
  status: OrderStatus!
  stripeChargeId: String
  stripePaymentIntentId: String
  totalPrice: BigDecimal!
  trackingNumber: String
  unitPrice: BigDecimal!
  updated: DateTime!
  user: CalendarUser!
}

enum OrderStatus {
  CANCELLED
  DELIVERED
  PAID
  PENDING
  PROCESSING
  REFUNDED
  SHIPPED
}
```

**IMPORTANT NOTE:** There is NO separate Payment entity in the schema. Payment information is embedded in the CalendarOrder entity via fields like `stripePaymentIntentId`, `stripeChargeId`, and `paidAt`.

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/services/PaymentService.java`
    *   **Summary:** This file ALREADY EXISTS and contains most of the required functionality! PaymentService already implements:
        - `processPaymentSuccess(paymentIntentId, chargeId)` - idempotent payment processing that updates order status to PAID and enqueues confirmation email
        - `processRefund(paymentIntentId, amountInCents, reason)` - creates Stripe refund and updates order notes
        - `processRefundWebhook(chargeId, refundId, amountRefunded)` - idempotent webhook processing for charge.refunded events
        - Webhook secret accessor: `getWebhookSecret()`
    *   **Recommendation:** You DO NOT need to create PaymentService from scratch. The service is already complete and implements all required functionality from the task specification. Review this file carefully to understand the implementation.

*   **File:** `src/main/java/villagecompute/calendar/api/rest/WebhookResource.java`
    *   **Summary:** This file ALREADY EXISTS and implements the complete webhook endpoint at `/api/webhooks/stripe`. It handles:
        - Webhook signature validation using Stripe SDK
        - Event routing to appropriate handlers (checkout.session.completed, payment_intent.succeeded, payment_intent.payment_failed, charge.refunded)
        - Delegates to PaymentService.processPaymentSuccess() and processRefundWebhook()
        - Comprehensive error handling and logging
        - OpenAPI documentation
    *   **Recommendation:** The webhook controller is ALREADY FULLY IMPLEMENTED. The task description refers to "StripeWebhookController" but the actual class is named `WebhookResource.java` (following JAX-RS conventions). You DO NOT need to create this from scratch.

*   **File:** `src/main/java/villagecompute/calendar/integration/stripe/StripeService.java`
    *   **Summary:** This file implements Stripe Checkout Session creation and webhook signature validation utility methods. It is separate from PaymentService and focuses on direct Stripe API interactions.
    *   **Recommendation:** DO NOT modify this file. It is a complete dependency that PaymentService and WebhookResource already use correctly via `getWebhookSecret()`.

*   **File:** `src/main/java/villagecompute/calendar/services/OrderService.java`
    *   **Summary:** Contains order management methods including `findByStripePaymentIntent(paymentIntentId)` which is used by PaymentService to locate orders.
    *   **Recommendation:** You MUST use OrderService.findByStripePaymentIntent() to find orders in PaymentService (already done). DO NOT modify OrderService.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** Order entity with Stripe payment fields (stripePaymentIntentId, stripeChargeId, paidAt), status management, and static finder methods. **Note: There is NO separate Payment entity - all payment data is stored in the order entity.**
    *   **Recommendation:** Review the order status constants (STATUS_PENDING, STATUS_PAID, etc.) and helper methods (markAsPaid(), isTerminal()). PaymentService already uses these correctly.

*   **File:** `src/main/java/villagecompute/calendar/data/models/DelayedJob.java`
    *   **Summary:** Async job entity for background processing. Used for email sending.
    *   **Recommendation:** PaymentService already enqueues email jobs using `DelayedJob.createDelayedJob()` with queue type `EMAIL_ORDER_CONFIRMATION`. This is complete.

*   **File:** `src/main/java/villagecompute/calendar/services/EmailService.java`
    *   **Summary:** Email service that sends emails via Quarkus Mailer.
    *   **Recommendation:** DO NOT modify. This service will be consumed by the email job handler (separate from this task).

### Implementation Tips & Notes

*   **CRITICAL FINDING:** Both `PaymentService.java` and `WebhookResource.java` ALREADY EXIST and are FULLY IMPLEMENTED. The task specification describes creating these from scratch, but they are already complete in the codebase.

*   **What You MUST Do:** Your task is to write **integration tests** for the webhook handling. The test file `src/test/java/villagecompute/calendar/api/rest/WebhookResourceTest.java` does NOT exist yet. This is the only deliverable that is missing.

*   **Testing Strategy:** You need to create comprehensive integration tests that:
    1. Simulate Stripe webhook POST requests to `/api/webhooks/stripe`
    2. Test signature validation (valid and invalid signatures)
    3. Test checkout.session.completed event processing (verify order marked as PAID)
    4. Test payment_intent.succeeded event processing
    5. Test payment_intent.payment_failed event processing
    6. Test charge.refunded event processing (verify refund recorded in order notes)
    7. Test idempotent processing (send same webhook twice, verify only processed once)
    8. Verify email job enqueueing after payment success

*   **Testing Framework:** Use Quarkus @QuarkusTest with REST Assured for testing the webhook endpoint. You will need to:
    - Mock or construct valid Stripe Event payloads
    - Generate valid Stripe-Signature headers (or mock the validation for testing)
    - Use @Inject to access OrderService to verify order status changes
    - Query DelayedJob table to verify email job enqueued

*   **Key Test Data:** You need to create test Stripe webhook payloads. Reference the existing `WebhookResource.handleCheckoutSessionCompleted()` implementation to see the expected JSON structure. Example:
    ```json
    {
      "id": "evt_test_webhook",
      "type": "checkout.session.completed",
      "data": {
        "object": {
          "id": "cs_test_session",
          "payment_intent": "pi_test_intent",
          "metadata": {
            "order_id": "test-order-uuid"
          }
        }
      }
    }
    ```

*   **Idempotency Testing:** The PaymentService.processPaymentSuccess() method already implements idempotency by checking if `order.status` is already `PAID`. Your test should verify:
    1. First webhook call updates order to PAID
    2. Second identical webhook call does NOT change order status (still PAID)
    3. Email job is only enqueued once (not twice)

*   **Webhook Signature:** The WebhookResource uses `Webhook.constructEvent(payload, signatureHeader, webhookSecret)` from Stripe SDK to validate signatures. For testing, you have these options:
    1. Generate a real test signature using Stripe's webhook secret (complex)
    2. Mock the PaymentService.getWebhookSecret() to return a known test secret, then use Stripe CLI to generate test webhooks with valid signatures
    3. Create a test-specific approach that bypasses signature validation (use with caution)

*   **Database Setup:** Your tests will need test orders with Stripe payment intent IDs. Use @Transactional to set up test data before each test and clean up afterward.

*   **Assertion Checklist:** For each webhook test, verify:
    - HTTP response status (200 for success, 400 for invalid signature, 500 for processing errors)
    - Order status updated correctly in database
    - Order notes contain expected log entries
    - DelayedJob record created with correct queue type and actor ID
    - Logs contain expected info/error messages

*   **Edge Cases to Test:**
    - Webhook for non-existent order (should log error but return 200 to Stripe)
    - Malformed webhook payload (should return 400)
    - Missing Stripe-Signature header (should return 400)
    - Webhook for order that's already in terminal state (should be idempotent)

*   **File Naming:** The task says "StripeWebhookControllerTest.java" but the actual controller is named "WebhookResource.java". Name your test file `WebhookResourceTest.java` to match the actual class name (following Java testing conventions).

### REVISED TASK SCOPE

Based on the codebase analysis, here's what you ACTUALLY need to do:

1. ✅ PaymentService - **ALREADY COMPLETE** (review only)
2. ✅ WebhookResource (StripeWebhookController) - **ALREADY COMPLETE** (review only)
3. ❌ Integration Tests - **MISSING** - This is your primary deliverable

**Your actual task is to create comprehensive integration tests for the webhook endpoint, not to implement the service and controller from scratch.**

---

## 4. Acceptance Criteria Mapping to Existing Code

Let me map each acceptance criterion to the existing implementation:

1. ✅ "Webhook endpoint validates Stripe signature, rejects invalid requests"
   - Implemented in `WebhookResource.handleStripeWebhook()` lines 127-148
   - Uses `Webhook.constructEvent()` for validation
   - Returns 400 with error message if signature invalid

2. ✅ "checkout.session.completed event creates Payment record, updates Order status to PAID"
   - Implemented in `WebhookResource.handleCheckoutSessionCompleted()` lines 197-220
   - Delegates to `PaymentService.processPaymentSuccess()`
   - PaymentService updates order status to PAID (lines 223-236)
   - **IMPORTANT:** No separate Payment entity exists - payment data is embedded in Order entity (stripePaymentIntentId, stripeChargeId, paidAt fields)

3. ✅ "Duplicate webhook delivery does not create duplicate payments"
   - Implemented in `PaymentService.processPaymentSuccess()` lines 217-220
   - Idempotent check: returns false if order already PAID
   - **Your test must verify this behavior**

4. ✅ "Payment success triggers email job enqueue"
   - Implemented in `PaymentService.processPaymentSuccess()` lines 239-245
   - Creates DelayedJob with queue EMAIL_ORDER_CONFIRMATION
   - **Your test must verify job created in database**

5. ✅ "Integration test simulates Stripe webhook POST, verifies order status change"
   - **THIS IS YOUR TASK** - create this test

6. ✅ "charge.refunded webhook updates Payment record with refund amount"
   - Implemented in `WebhookResource.handleChargeRefunded()` lines 308-347
   - Delegates to `PaymentService.processRefundWebhook()`
   - Updates order notes with refund information (lines 335-342)
   - **Your test must verify refund recorded in order.notes field**

---

## 5. Recommended Test Structure

Create `src/test/java/villagecompute/calendar/api/rest/WebhookResourceTest.java` with these test methods:

```java
@QuarkusTest
public class WebhookResourceTest {

    @Test
    public void testCheckoutSessionCompletedWebhook_Success() {
        // Setup: Create test order with stripe payment intent ID
        // Action: POST to /api/webhooks/stripe with valid checkout.session.completed payload
        // Assert: Order status = PAID, paidAt timestamp set, email job enqueued
    }

    @Test
    public void testWebhookSignatureValidation_InvalidSignature() {
        // Action: POST with invalid Stripe-Signature header
        // Assert: Returns 400, order status unchanged
    }

    @Test
    public void testIdempotentProcessing_DuplicateWebhook() {
        // Action: Send same webhook twice
        // Assert: First call updates order, second call skips (idempotent)
        // Assert: Only one email job enqueued
    }

    @Test
    public void testChargeRefundedWebhook_Success() {
        // Setup: Create paid order with stripe charge ID
        // Action: POST charge.refunded webhook
        // Assert: Order notes contain refund information
    }

    @Test
    public void testPaymentIntentSucceeded_Success() {
        // Similar to checkout.session.completed test
    }

    @Test
    public void testPaymentIntentFailed_LogsFailure() {
        // Action: Send payment_intent.payment_failed webhook
        // Assert: Order notes contain failure message
    }
}
```

Use the actual webhook payload structures from Stripe documentation and the existing handler implementations as reference.

---

## 6. Summary

This task is primarily a **TESTING TASK**, not an implementation task. The PaymentService and WebhookResource are already fully implemented. Your job is to create comprehensive integration tests that verify the webhook handling meets all acceptance criteria, particularly focusing on:

- Idempotent processing
- Signature validation
- Order status updates
- Email job enqueueing
- Refund recording

**DO NOT MODIFY** the existing PaymentService or WebhookResource unless you find critical bugs. Focus on creating thorough integration tests.
