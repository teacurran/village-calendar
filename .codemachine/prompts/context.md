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
  "deliverables": [
    "PaymentService with payment processing logic",
    "Webhook endpoint handling Stripe events",
    "Idempotent webhook processing (duplicate handling)",
    "Email job enqueuing on payment success",
    "Integration tests for webhook scenarios"
  ],
  "acceptance_criteria": [
    "Webhook endpoint validates Stripe signature, rejects invalid requests",
    "checkout.session.completed event creates Payment record, updates Order status to PAID",
    "Duplicate webhook delivery (same event ID) does not create duplicate payments",
    "Payment success triggers email job enqueue (order confirmation)",
    "Integration test simulates Stripe webhook POST, verifies order status change",
    "charge.refunded webhook updates Payment record with refund amount"
  ],
  "dependencies": ["I3.T1", "I3.T2"],
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

**Critical Workflow Steps:**
- Stripe sends webhook: checkout.session.completed (contains payment_intent ID)
- Webhook validates Stripe-Signature header using HMAC-SHA256
- Payment processing (within transaction):
  * UPDATE orders SET status=PAID, paid_at=NOW()
  * INSERT INTO delayed_jobs (queue=EMAIL_ORDER_CONFIRMATION, actor_id=order_id)
- Webhook responds 200 OK to acknowledge receipt
- Email worker picks up job asynchronously and sends confirmation
```

### Context: security-considerations (from 05_Operational_Architecture.md)

```markdown
#### 3.8.3. Security Considerations - Payment Security

**PCI DSS Compliance Requirements:**

- **Stripe Checkout**: Card data NEVER touches application servers (entered directly into Stripe-hosted form)
- **SAQ A Eligibility**: Lowest scope merchant self-assessment questionnaire
- **Webhook Signature Validation**: MANDATORY security control
  * Uses HMAC-SHA256 with webhook secret
  * Stripe SDK provides `Webhook.constructEvent(payload, signature, secret)` method
  * Invalid signatures MUST return 400 Bad Request
  * Valid signatures proceed with event processing
- **No Card Storage**: Application stores only `stripe_payment_intent_id` token, never raw card numbers

**Critical Security Threats:**

**Threat: Webhook Spoofing Attack**
- Attack Vector: Attacker POSTs fake `checkout.session.completed` webhooks to /api/webhooks/stripe
- Consequence: Fraudulent order confirmations, free products shipped without payment
- Mitigation: Validate `Stripe-Signature` header using webhook secret on EVERY request
- Severity: CRITICAL (direct financial loss)

**Idempotency Requirements:**
- Stripe retries failed webhooks with exponential backoff (up to 3 days)
- Application MUST handle duplicate event deliveries gracefully
- Implementation pattern: Check if payment already processed (order.status == PAID) before updating
- Duplicate webhooks should return 200 OK but skip re-processing
```

### Context: task-i3-t3 (from 02_Iteration_I3.md)

```markdown
### Task 3.3: Implement Payment Service and Webhook Handler

**Detailed Acceptance Criteria:**

1. Webhook signature validation: 400 response for invalid/missing `Stripe-Signature` header
2. checkout.session.completed event processing:
   - Locate order by stripe_payment_intent_id from session.payment_intent
   - Update order.status to PAID
   - Set order.paid_at timestamp
   - Append payment success note to order.notes
   - Enqueue EMAIL_ORDER_CONFIRMATION delayed job
3. Idempotent processing: Duplicate webhooks do NOT create duplicate email jobs
4. charge.refunded event processing:
   - Locate order by stripe_charge_id
   - Append refund details to order.notes (refund ID, amount)
   - Handle idempotently (check if refund ID already in notes)
5. Integration tests must verify all above scenarios with real webhook payloads

**Implementation Requirements:**

- Endpoint: POST /api/webhooks/stripe with @PermitAll security (auth via signature, not JWT)
- Transaction boundary: @Transactional on webhook handler method
- Event routing: switch on event.getType() for different webhook types
- Error responses:
  * 400 for signature validation failures
  * 200 for successfully processed events (even unknown types)
  * 500 only for unexpected exceptions
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/services/PaymentService.java`
    *   **Summary:** This file ALREADY EXISTS and contains a complete PaymentService implementation.
    *   **CRITICAL STATUS:** The PaymentService is FULLY IMPLEMENTED with:
        - `createPaymentIntent()` - creates Stripe PaymentIntent for orders
        - `processPaymentSuccess(String paymentIntentId, String chargeId)` - handles payment confirmation
        - `processRefund()` - creates refunds via Stripe API
        - `processRefundWebhook()` - handles charge.refunded webhook events
    *   **Key Feature:** processPaymentSuccess() ALREADY implements:
        - Idempotent processing (checks if order.status == PAID before updating)
        - Order status update to PAID with paid_at timestamp
        - Email job enqueueing via `DelayedJob.createDelayedJob(orderId, EMAIL_ORDER_CONFIRMATION, Instant.now())`
        - Transaction boundary (called from @Transactional webhook handler)
    *   **Recommendation:** You MUST review this existing implementation. The task is ALREADY COMPLETE for PaymentService. Verify it matches all acceptance criteria and add missing functionality only if gaps exist.

*   **File:** `src/main/java/villagecompute/calendar/api/rest/WebhookResource.java`
    *   **Summary:** This file ALREADY EXISTS as the REST webhook controller (named WebhookResource, not StripeWebhookController).
    *   **CRITICAL STATUS:** The webhook handler is FULLY IMPLEMENTED with:
        - Endpoint: POST /api/webhooks/stripe with @PermitAll access
        - Signature validation using `Webhook.constructEvent(payload, signatureHeader, webhookSecret)`
        - Event routing for: checkout.session.completed, payment_intent.succeeded, payment_intent.payment_failed, charge.refunded
        - Delegation to PaymentService.processPaymentSuccess() and processRefundWebhook()
        - Proper error handling (400 for bad signature, 200 for success, 500 for internal errors)
    *   **File Naming Note:** The task specifies "StripeWebhookController.java" but the codebase uses "WebhookResource.java". This follows JAX-RS naming conventions. You should KEEP this name for consistency with other resources (CalendarResource, AuthResource, etc.).
    *   **Recommendation:** The webhook controller EXISTS and is functional. You should verify it handles all required event types and matches acceptance criteria. No new file creation needed.

*   **File:** `src/test/java/villagecompute/calendar/api/rest/StripeWebhookControllerTest.java`
    *   **Summary:** This file ALREADY EXISTS and contains comprehensive integration tests.
    *   **CRITICAL STATUS:** The test suite ALREADY covers:
        - Invalid signature rejection: `testWebhook_RejectInvalidSignature()`
        - Missing signature rejection: `testWebhook_RejectMissingSignature()`
        - checkout.session.completed processing: `testWebhook_CheckoutSessionCompleted_UpdatesOrderStatus()`
        - Email job enqueueing: `testWebhook_CheckoutSessionCompleted_EnqueuesEmailJob()`
        - Idempotent processing: `testWebhook_IdempotentProcessing_NoDuplicateUpdates()`
        - payment_intent.succeeded event processing (alternative flow)
        - charge.refunded event processing with idempotency
        - Unknown event type handling (returns 200)
    *   **Test Quality:** Tests use proper setup/teardown with @BeforeEach/@AfterEach, mock Stripe webhook signatures using HMAC-SHA256, and verify database state changes.
    *   **Recommendation:** RUN THE EXISTING TESTS with `./mvnw test -Dtest=StripeWebhookControllerTest` to verify all pass. If any fail, debug and fix the implementation. If tests are missing edge cases, add them.

*   **File:** `src/main/java/villagecompute/calendar/integration/stripe/StripeService.java`
    *   **Summary:** Contains Stripe SDK integration for Checkout Sessions and webhook validation.
    *   **Key Methods:**
        - `createCheckoutSession(order, successUrl, cancelUrl)` - creates Stripe Checkout Session
        - `retrieveSession(sessionId)` - fetches session details from Stripe
        - `validateWebhookSignature(payload, signature)` - validates webhook HMAC signature
    *   **Recommendation:** The WebhookResource currently duplicates signature validation logic. It should be refactored to use `StripeService.validateWebhookSignature()` for better code reuse, but this is a minor improvement, not a blocker.

*   **File:** `src/main/java/villagecompute/calendar/services/OrderService.java`
    *   **Summary:** Contains complete order lifecycle management.
    *   **Key Methods:**
        - `createOrder()` - creates order in PENDING status
        - `updateOrderStatus()` - transitions order through state machine
        - `findByStripePaymentIntent()` - locates order by payment intent ID
        - `cancelOrder()` - cancels orders with authorization checks
    *   **Recommendation:** The PaymentService correctly uses `OrderService.findByStripePaymentIntent()` to locate orders during webhook processing. The state machine validates allowed transitions (PENDING → PAID is valid).

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** JPA entity for e-commerce orders with Panache active record pattern.
    *   **Critical Fields:**
        - `stripePaymentIntentId` (String) - correlates order with Stripe payment
        - `stripeChargeId` (String) - used for refund processing
        - `status` (String) - order lifecycle state (use `CalendarOrder.STATUS_PAID` constant)
        - `paidAt` (Instant) - payment confirmation timestamp
        - `notes` (String, TEXT) - append payment processing notes
    *   **Helper Methods:**
        - `markAsPaid()` - sets status=PAID, paidAt=now, persists
        - `isTerminal()` - checks if order is DELIVERED or CANCELLED
    *   **Recommendation:** The PaymentService correctly updates order.status and order.paidAt. The helper method `markAsPaid()` provides a convenient alternative to manual field updates.

*   **File:** `src/main/java/villagecompute/calendar/data/models/DelayedJob.java`
    *   **Summary:** JPA entity for asynchronous job processing with retry logic.
    *   **Factory Method:** `DelayedJob.createDelayedJob(actorId, queue, runAt)` - creates and persists job
    *   **Recommendation:** The PaymentService correctly enqueues email jobs using: `DelayedJob.createDelayedJob(order.id.toString(), DelayedJobQueue.EMAIL_ORDER_CONFIRMATION, Instant.now())` within the same transaction.

### Implementation Tips & Notes

*   **CRITICAL: Task is ALREADY COMPLETE:** All three target files (PaymentService.java, WebhookResource.java, StripeWebhookControllerTest.java) EXIST and appear to be fully functional. Your primary responsibility is to VERIFY that the implementation matches all acceptance criteria, RUN the existing tests, and FIX any gaps or failures.

*   **File Naming Clarification:** The task specifies "StripeWebhookController.java" but the actual implementation is "WebhookResource.java". This is NOT an error - it's a naming convention difference. JAX-RS resources typically use the "Resource" suffix. You should either:
    1. Rename WebhookResource.java to StripeWebhookController.java (requires updating imports in tests)
    2. OR document that WebhookResource fulfills the StripeWebhookController requirement (RECOMMENDED)

*   **Payment Entity Mystery:** The task mentions a "Payment" entity from I1.T8, but NO SUCH ENTITY EXISTS in the codebase. The CalendarOrder entity has embedded payment fields (stripePaymentIntentId, stripeChargeId, paidAt) instead of a separate Payment table. This architectural decision simplifies the data model for MVP. You should NOT create a separate Payment entity unless explicitly requested.

*   **Idempotency Pattern (CRITICAL):** The PaymentService.processPaymentSuccess() implements the correct pattern:
    ```java
    // Idempotent check: if already paid, skip processing
    if (CalendarOrder.STATUS_PAID.equals(order.status)) {
        LOG.infof("Order %s already marked as PAID, skipping update (idempotent)", order.id);
        return false; // Indicates no processing occurred
    }
    ```
    This prevents duplicate email jobs when Stripe retries webhooks. DO NOT modify this logic unless you find a bug.

*   **Email Job Transactional Consistency:** The email job is enqueued WITHIN THE SAME TRANSACTION as the order status update in PaymentService.processPaymentSuccess():
    ```java
    // Enqueue confirmation email (within same transaction for consistency)
    DelayedJob emailJob = DelayedJob.createDelayedJob(
        order.id.toString(),
        DelayedJobQueue.EMAIL_ORDER_CONFIRMATION,
        Instant.now() // Send immediately
    );
    LOG.infof("Enqueued order confirmation email job %s for order %s", emailJob.id, order.id);
    ```
    This ensures ACID properties - either both order update AND email job are committed, or neither. This is correct behavior per the architecture.

*   **Webhook Signature Validation:** The WebhookResource uses the correct Stripe SDK method:
    ```java
    Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
    ```
    This validates the HMAC-SHA256 signature and prevents webhook spoofing. The method throws `SignatureVerificationException` if invalid, which the webhook handler catches and returns 400 Bad Request.

*   **Error Response Codes (Stripe Best Practices):** The WebhookResource correctly implements:
    - 400 Bad Request: Invalid/missing signature, malformed payload
    - 200 OK: Successfully processed webhooks (even unknown event types)
    - 500 Internal Server Error: Unexpected exceptions during processing

    This ensures Stripe doesn't retry client errors (400) but does retry server errors (500).

*   **Refund Processing Idempotency:** The PaymentService.processRefundWebhook() implements idempotent refund recording:
    ```java
    // Check if refund already recorded (idempotent)
    if (order.notes != null && order.notes.contains("Refund ID: " + refundId)) {
        LOG.infof("Refund %s already recorded for order %s, skipping (idempotent)", refundId, order.id);
        return;
    }
    ```
    This prevents duplicate refund notes when Stripe sends multiple charge.refunded webhooks.

*   **Test Execution Command:** Run the integration tests with:
    ```bash
    ./mvnw test -Dtest=StripeWebhookControllerTest
    ```
    This executes all 9 test methods in the suite. If any fail, investigate the logs and fix the implementation.

*   **Test Signature Generation:** The tests generate valid Stripe webhook signatures using HMAC-SHA256:
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
    This replicates Stripe's signature format: `t=<timestamp>,v1=<signature>`. The webhook endpoint must validate this format.

### Verification Checklist

Before marking this task complete, you MUST verify:

1. ✅ PaymentService.processPaymentSuccess() exists and implements idempotent payment processing
2. ✅ WebhookResource exists at POST /api/webhooks/stripe with @PermitAll access
3. ✅ Webhook signature validation rejects invalid/missing signatures with 400 response
4. ✅ checkout.session.completed event updates order to PAID status
5. ✅ payment_intent.succeeded event updates order to PAID status (alternative flow)
6. ✅ Email job is enqueued on payment success within the same transaction
7. ✅ Duplicate webhook delivery does NOT create duplicate email jobs (idempotent)
8. ✅ charge.refunded event appends refund details to order.notes
9. ✅ Refund webhook processing is idempotent (duplicate refunds NOT re-recorded)
10. ✅ Integration tests exist for all above scenarios
11. ✅ RUN TESTS: `./mvnw test -Dtest=StripeWebhookControllerTest` - all tests PASS
12. ✅ Unknown event types return 200 OK (Stripe best practice)

### Recommended Actions

**PRIMARY TASK: RUN AND VERIFY EXISTING TESTS**

1. **Execute Test Suite:**
   ```bash
   ./mvnw test -Dtest=StripeWebhookControllerTest
   ```
   This is your FIRST action. The tests will reveal if the implementation is correct.

2. **Analyze Test Results:**
   - If all tests PASS: Task is COMPLETE. Proceed to step 6 (documentation).
   - If tests FAIL: Analyze failure logs, identify root cause, proceed to step 3.

3. **Debug Failed Tests (if applicable):**
   - Check order status updates in database
   - Verify email job creation in delayed_jobs table
   - Inspect webhook signature validation logic
   - Review transaction boundaries and commit behavior

4. **Fix Implementation Issues (if applicable):**
   - Update PaymentService or WebhookResource to fix identified bugs
   - Re-run tests after each fix until all pass
   - DO NOT refactor working code unnecessarily

5. **Add Missing Test Coverage (if applicable):**
   - Review acceptance criteria against existing tests
   - If any criteria lack coverage, add new test methods
   - Examples: Test payment failure handling, test webhook with missing order, test concurrent webhook delivery

6. **Document Task Completion:**
   - Update task status to done=true in tasks_I3.json
   - Document file naming discrepancy (WebhookResource vs StripeWebhookController)
   - Document Payment entity decision (embedded in CalendarOrder, not separate entity)
   - Note any deviations from original task specification with rationale

7. **Prepare for Next Task (I3.T4):**
   - Verify PaymentService can be injected into OrderGraphQL resolver
   - Confirm webhook processing doesn't interfere with GraphQL mutation responses
   - Review any TODO comments left in PaymentService or WebhookResource

### Critical Warnings

*   **DO NOT reimplement existing functionality.** The PaymentService and WebhookResource appear to be fully functional based on code review. Your role is to VERIFY, TEST, and FIX any gaps, NOT to rewrite working code from scratch.

*   **DO NOT create duplicate webhook endpoints.** The WebhookResource already handles POST /api/webhooks/stripe. Creating a new StripeWebhookController would result in duplicate endpoint registration and runtime errors.

*   **DO NOT create a separate Payment entity** unless you find explicit requirements for it. The current architecture stores payment data in CalendarOrder fields, which is a valid design choice for MVP scope.

*   **DO NOT modify the idempotency logic** in processPaymentSuccess() or processRefundWebhook() unless you find a bug. This logic is security-critical for preventing duplicate charges and fraudulent confirmations.

*   **DO NOT skip test execution.** The tests are the acceptance criteria verification mechanism. Passing tests = task complete. Failing tests = implementation issues to fix.

### Known Issues to Investigate

*   **Potential Issue:** The WebhookResource duplicates signature validation code instead of using StripeService.validateWebhookSignature(). This is a code smell but not a functional bug. Consider refactoring to use StripeService for better maintainability.

*   **Potential Issue:** The task specifies "create Payment record" but no Payment entity exists. This may be an oversight in the task description, or the Payment entity was supposed to be created in I1.T8 but wasn't. You should clarify this with the user if it blocks progress.

*   **Potential Issue:** The tests use a mock webhook secret from application.properties. Verify that the test configuration (`application-test.properties` or similar) has a valid `stripe.webhook.secret` value, otherwise signature validation will fail in tests.

---

**END OF BRIEFING PACKAGE**

This briefing package provides all information needed to verify and complete task I3.T3. The implementation appears to be largely complete - your primary responsibility is test execution and verification, not new development.
