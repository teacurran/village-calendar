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
    "src/main/java/villagecompute/calendar/services/OrderService.java",
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

### Context: Payment Processing Flow (from sequence_order_placement.puml)

```markdown
The order placement sequence diagram shows the complete webhook processing flow:

**Stripe Webhook Processing Phase:**
1. Stripe sends POST /api/webhooks/stripe asynchronously 1-3 seconds after payment succeeds
2. Webhook includes Stripe-Signature header with HMAC signature
3. API validates signature using Webhook.constructEvent(payload, signature, webhookSecret)
4. If signature is invalid, return 400 Bad Request to prevent fraudulent webhooks
5. If signature is valid, extract checkout session ID and metadata (order_id)
6. OrderService.handlePaymentSuccess(checkoutSessionId) processes payment
7. Transaction begins: SELECT order FOR UPDATE, INSERT payment record, UPDATE order status to PAID
8. Enqueue email job: INSERT INTO delayed_jobs for ORDER_CONFIRMATION
9. Publish EventBus event for async job processing
10. Return 200 OK to acknowledge webhook (prevents Stripe retries)

**Idempotency Handling:**
- Check if order already PAID before processing webhook
- Log and return 200 OK if duplicate (prevents duplicate payment records)
- Use FOR UPDATE lock to prevent concurrent webhook processing

**Error Scenarios:**
- Signature validation failure → 400 Bad Request, log security incident
- Order not found → Log error, return 200 OK (prevent retries)
- Email delivery failure → Job marked FAILED with exponential backoff retry
```

### Context: Webhook Requirements (from stripe-setup.md)

```markdown
**Webhook Events to Handle:**
1. checkout.session.completed - Primary payment success event for hosted checkout flow
2. payment_intent.succeeded - Direct PaymentIntent success (alternative flow)
3. payment_intent.payment_failed - Payment declined
4. charge.refunded - Refund processed

**Webhook Security:**
- Signature validation is CRITICAL - never process webhooks without validation
- Use Stripe.net.Webhook.constructEvent() for automatic signature verification
- Webhook secret format: starts with whsec_
- Invalid signatures must return 400 Bad Request
- Valid webhooks return 200 OK to prevent retries

**Webhook Signature Validation:**
```java
Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
// Throws SignatureVerificationException if invalid
```

**Idempotency Requirements:**
- Stripe may send duplicate webhooks (network retries)
- Use event ID or payment intent ID to detect duplicates
- If payment already processed, return 200 OK without changes
- Log duplicate detection for monitoring
```

### Context: Stripe Integration (from StripeService.java analysis)

```markdown
**Existing StripeService Implementation:**
Located at: src/main/java/villagecompute/calendar/integration/stripe/StripeService.java

The service provides:
1. getPublishableKey() - Returns Stripe publishable key for frontend
2. getWebhookSecret() - Returns webhook secret for signature validation
3. Configuration via @ConfigProperty annotations:
   - stripe.api.key (secret key)
   - stripe.publishable.key (publishable key)
   - stripe.webhook.secret (webhook signing secret)

**Important:** StripeService already exists but does NOT contain webhook validation methods.
You will need to USE the getWebhookSecret() method in your webhook controller.
```

### Context: Order Status Management (from OrderService.java)

```markdown
**OrderService Status Transition Logic:**
File: src/main/java/villagecompute/calendar/services/OrderService.java

**Status Constants (from CalendarOrder.java):**
- STATUS_PENDING - Initial order state
- STATUS_PAID - Payment confirmed (target state for webhook)
- STATUS_PROCESSING - Order being prepared
- STATUS_SHIPPED - Order shipped to customer
- STATUS_DELIVERED - Order received
- STATUS_CANCELLED - Order cancelled

**Critical Methods:**
1. findByStripePaymentIntent(String paymentIntentId) - Find order by Stripe payment intent ID
   - Returns Optional<CalendarOrder>
   - Used by webhook handler to locate order

2. Order Status Updates:
   - Order has paidAt timestamp field (Instant)
   - Order has stripeChargeId field (String) for storing charge ID
   - Order has notes field (TEXT) for audit trail

**State Machine Validation:**
- validateStatusTransition(currentStatus, newStatus) enforces valid transitions
- PENDING → PAID transition is valid
- PAID → CANCELLED requires refund processing
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/services/PaymentService.java`
    *   **Summary:** This file **ALREADY EXISTS** and contains a complete PaymentService implementation. It includes createPaymentIntent, processPaymentSuccess, processRefund, and processRefundWebhook methods.
    *   **Recommendation:** You MUST NOT create this file from scratch. The PaymentService is already fully implemented with all required methods. However, you should REVIEW it to understand the implementation and ensure it meets the acceptance criteria. The task may be asking you to verify/test the existing implementation rather than create it.

*   **File:** `src/main/java/villagecompute/calendar/api/rest/WebhookResource.java`
    *   **Summary:** This file **ALREADY EXISTS** and contains a complete StripeWebhookController implementation at path /api/webhooks/stripe. It handles all required webhook events (checkout.session.completed, payment_intent.succeeded, payment_intent.payment_failed, charge.refunded) with signature validation and idempotent processing.
    *   **Recommendation:** You MUST NOT create this file from scratch. The webhook controller is already fully implemented. Your task is to CREATE INTEGRATION TESTS for this existing implementation.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** Order entity with Panache active record pattern. Contains status constants, foreign keys to User and Calendar, payment tracking fields (stripePaymentIntentId, stripeChargeId, paidAt), and finder methods.
    *   **Recommendation:** Use CalendarOrder.findByStripePaymentIntent(paymentIntentId) to locate orders in webhook handlers. Use the markAsPaid() helper method or manually set status/paidAt fields.

*   **File:** `src/main/java/villagecompute/calendar/services/OrderService.java`
    *   **Summary:** Service for order management with findByStripePaymentIntent() method already implemented.
    *   **Recommendation:** The PaymentService already uses OrderService.findByStripePaymentIntent() - no changes needed to OrderService.

*   **File:** `src/main/java/villagecompute/calendar/integration/stripe/StripeService.java`
    *   **Summary:** Stripe SDK integration service providing getWebhookSecret() and getPublishableKey() methods.
    *   **Recommendation:** The WebhookResource already uses PaymentService.getWebhookSecret() - the integration is complete.

### Implementation Tips & Notes

*   **CRITICAL FINDING:** Both PaymentService.java and WebhookResource.java **ALREADY EXIST** and are **FULLY IMPLEMENTED**. The task description asks you to "create" these files, but they are already complete. Your actual task is to:
    1. **VERIFY** the existing implementations meet all acceptance criteria
    2. **CREATE INTEGRATION TESTS** for the webhook handling (this is the missing piece)
    3. **DO NOT MODIFY** the existing PaymentService or WebhookResource unless you find bugs

*   **Integration Test Requirements:**
    - Test file location: `src/test/java/villagecompute/calendar/api/rest/StripeWebhookControllerTest.java`
    - Test must simulate Stripe webhook POST requests with valid signatures
    - Test idempotent processing (send same webhook twice, verify only one payment created)
    - Test signature validation failure (invalid signature returns 400)
    - Test checkout.session.completed → order status changes to PAID
    - Test charge.refunded → refund recorded in order notes
    - Use Quarkus @QuarkusTest annotation and REST Assured for HTTP testing
    - Use Testcontainers for PostgreSQL database

*   **Webhook Signature Testing:**
    - You will need to generate valid Stripe signatures for test webhooks
    - Use Stripe's Webhook.calculateSignature() method or mock the signature validation
    - Alternative: Use @MockBean to mock PaymentService.getWebhookSecret() and Webhook validation

*   **Idempotency Testing Strategy:**
    - Create order, send webhook event once → verify order PAID
    - Send same webhook event again (same event ID) → verify still only one payment record
    - Check logs for idempotent message "already marked as PAID, skipping update"

*   **Email Job Verification:**
    - After webhook processing, verify delayed_jobs table contains ORDER_CONFIRMATION job
    - Check job status is PENDING and payload contains order ID
    - Do NOT test actual email sending (that's covered in I3.T7 tests)

*   **Existing Test Patterns:**
    - Review `src/test/java/villagecompute/calendar/service/OrderServiceTest.java` for testing patterns
    - Use @Transactional annotation on test methods for automatic rollback
    - Use CalendarOrder.deleteAll() in @BeforeEach to clean test data

*   **Warning:** The PaymentService uses @Transactional annotation - ensure tests verify transaction behavior (rollback on error, commit on success).

*   **Note:** The WebhookResource uses Jackson ObjectMapper to parse Stripe event JSON. The existing implementation parses JSON manually (event.getData().toJson() → JsonNode) which works in both test and production. Do NOT change this approach.

### Testing Checklist

Based on the acceptance criteria, your integration tests MUST verify:

1. ✅ Webhook endpoint validates Stripe signature (test with invalid signature → 400)
2. ✅ Valid checkout.session.completed creates Payment record (verify in database)
3. ✅ Valid checkout.session.completed updates Order status to PAID
4. ✅ Duplicate webhook delivery does not create duplicate payments (idempotency)
5. ✅ Payment success triggers email job enqueue (verify delayed_jobs table)
6. ✅ Integration test simulates Stripe webhook POST (full end-to-end)
7. ✅ charge.refunded webhook updates Payment record with refund amount

### Files You Will Actually Create/Modify

**CREATE:**
- `src/test/java/villagecompute/calendar/api/rest/StripeWebhookControllerTest.java` - Integration tests for webhook handling

**DO NOT MODIFY (already complete):**
- `src/main/java/villagecompute/calendar/services/PaymentService.java`
- `src/main/java/villagecompute/calendar/api/rest/WebhookResource.java`

**MAY NEED TO REVIEW (for test setup):**
- Test fixtures for creating test orders, users, calendars
- Test utilities for generating Stripe webhook payloads
- Database cleanup utilities (@BeforeEach methods)

---

## Summary

This task is primarily a **TESTING TASK**, not an implementation task. The PaymentService and WebhookResource are already fully implemented. Your job is to create comprehensive integration tests that verify the webhook handling meets all acceptance criteria, particularly focusing on idempotent processing, signature validation, and order status updates.
