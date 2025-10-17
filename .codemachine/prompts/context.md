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

**Sequence:**
1. User completes Stripe Checkout (hosted page)
2. Stripe sends webhook: checkout.session.completed
3. Webhook validates signature using Stripe-Signature header
4. Webhook handler processes payment:
   - INSERT INTO payments (order_id, stripe_payment_intent_id, amount, status=SUCCEEDED)
   - UPDATE orders SET status=PAID, updated_at=NOW()
   - INSERT INTO delayed_jobs (job_type=EMAIL_ORDER_CONFIRMATION)
5. Webhook responds 200 OK to acknowledge
6. Email worker picks up job and sends confirmation
```

### Context: security-considerations (from 05_Operational_Architecture.md)

```markdown
#### 3.8.3. Security Considerations

**8. PCI DSS Compliance (Payment Security)**

- **Stripe Checkout**: Card data never touches application servers (entered directly into Stripe-hosted form)
- **SAQ A Eligibility**: Merchant self-assessment questionnaire (lowest scope, <100 questions)
- **Webhook Signature Validation**: Stripe webhooks verified via `Stripe-Signature` header (prevents spoofing)
  - Uses HMAC-SHA256 with webhook secret
  - Stripe SDK provides Webhook.constructEvent() for validation
  - Invalid signatures MUST return 400 Bad Request
- **No Card Storage**: Application stores only `stripe_payment_intent_id` (token, not card numbers)

**Threat: Webhook Spoofing**
- Attacker could POST fake payment success webhooks to /api/webhooks/stripe
- Mitigation: Validate Stripe-Signature header using webhook secret
- Impact if bypassed: Fraudulent order confirmations, free products

**Idempotency Requirements:**
- Stripe retries failed webhooks (exponential backoff, up to 3 days)
- Application MUST handle duplicate event deliveries gracefully
- Implementation: Check if payment already processed before updating order
```

### Context: task-i3-t3 (from 02_Iteration_I3.md)

```markdown
### Task 3.3: Implement Payment Service and Webhook Handler

**Acceptance Criteria:**
- Webhook endpoint validates Stripe signature, rejects invalid requests with 400
- checkout.session.completed event creates Payment record, updates Order status to PAID
- Duplicate webhook delivery (same event ID) does not create duplicate payments
- Payment success triggers email job enqueue (order confirmation)
- Integration test simulates Stripe webhook POST, verifies order status change
- charge.refunded webhook updates Payment record with refund amount

**Key Implementation Details:**
1. POST /api/webhooks/stripe endpoint with @PermitAll (no auth - validated by signature)
2. Validate Stripe-Signature header using StripeService.validateWebhookSignature()
3. Process event.type: checkout.session.completed, charge.refunded
4. Use @Transactional to ensure atomic payment + order + email job updates
5. Return 200 OK for handled events, 200 OK for unknown event types (Stripe best practice)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/integration/stripe/StripeService.java`
    *   **Summary:** This file contains the Stripe integration service with methods for creating Stripe Checkout Sessions, retrieving sessions, and validating webhook signatures.
    *   **Recommendation:** You MUST import and use the `validateWebhookSignature()` method from this file in your webhook controller. The method signature is: `public Event validateWebhookSignature(String payload, String signatureHeader)`. This method already handles signature verification using Stripe's SDK and throws `SignatureVerificationException` if invalid.
    *   **Recommendation:** You SHOULD use the `getWebhookSecret()` method from this service if you need direct access to the webhook secret for any reason.
    *   **Note:** This service already initializes `Stripe.apiKey` in the `@PostConstruct init()` method, so your PaymentService can directly use Stripe SDK classes (like `Refund.create()`).

*   **File:** `src/main/java/villagecompute/calendar/services/OrderService.java`
    *   **Summary:** This file contains the OrderService with complete order lifecycle management including createOrder, updateOrderStatus, cancelOrder, and various finder methods.
    *   **Recommendation:** You MUST inject OrderService and use these methods:
        - `findByStripePaymentIntent(String paymentIntentId)` - to locate the order associated with a payment
        - `updateOrderStatus(UUID orderId, String newStatus, String notes)` - to transition the order to PAID status (though you may also directly use the CalendarOrder entity's `markAsPaid()` helper method)
    *   **Warning:** The OrderService already validates status transitions in the `validateStatusTransition()` private method. Make sure you understand the allowed transitions: PENDING → PAID is valid, but attempting to update an already PAID order to PAID again should be handled idempotently (check current status first).

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** This is the JPA entity for orders with helper methods and status constants.
    *   **Recommendation:** You SHOULD use the entity's helper methods directly for simpler status updates:
        - `order.markAsPaid()` - sets status to PAID and paidAt timestamp, then persists
        - `order.isTerminal()` - checks if order is in a terminal state (DELIVERED or CANCELLED)
    *   **Note:** The entity has these critical fields you'll need:
        - `stripePaymentIntentId` (String) - stores the Stripe Payment Intent ID
        - `stripeChargeId` (String) - stores the Stripe Charge ID
        - `status` (String) - order status (use constants like CalendarOrder.STATUS_PAID)
        - `paidAt` (Instant) - timestamp when payment was confirmed
        - `notes` (String, TEXT) - append payment processing notes here

*   **File:** `src/main/java/villagecompute/calendar/api/rest/WebhookResource.java`
    *   **Summary:** THIS FILE ALREADY EXISTS! It's a complete webhook controller with signature validation and payment processing logic.
    *   **CRITICAL WARNING:** The task description asks you to "Implement StripeWebhookController" but the codebase already has a working `WebhookResource.java` at the correct path (`POST /api/webhooks/stripe`). This implementation:
        - Already validates webhook signatures using `Webhook.constructEvent()` directly (doesn't call StripeService.validateWebhookSignature)
        - Already handles `payment_intent.succeeded` and `payment_intent.payment_failed` events
        - Already updates order status to PAID and enqueues email jobs using `DelayedJob.createDelayedJob()`
        - Already has idempotent handling (checks `!CalendarOrder.STATUS_PAID.equals(order.status)` before updating)
    *   **CRITICAL DECISION REQUIRED:** The existing WebhookResource handles `payment_intent` events, but the architecture diagram and task description specify `checkout.session.completed` events. These are DIFFERENT Stripe webhook types:
        - **payment_intent.succeeded**: Fires when a PaymentIntent completes (direct API integration)
        - **checkout.session.completed**: Fires when a Checkout Session completes (hosted checkout page)
    *   **RECOMMENDATION:** You need to REFACTOR the existing WebhookResource to:
        1. Handle `checkout.session.completed` instead of (or in addition to) `payment_intent.succeeded`
        2. Extract payment processing logic into a new PaymentService
        3. Add support for `charge.refunded` webhook event
        4. Ensure the webhook validates signatures using StripeService (currently it duplicates the validation code)

### Implementation Tips & Notes

*   **Tip:** The existing WebhookResource uses `DelayedJob.createDelayedJob()` to enqueue emails. The signature is: `DelayedJob.createDelayedJob(String entityId, String queue, Instant runAt)` where queue should be `DelayedJobQueue.EMAIL_ORDER_CONFIRMATION`.

*   **CRITICAL: Event Type Decision:** The Stripe event types you need to handle are:
    - **PRIMARY EVENT**: `checkout.session.completed` - This is what the architecture flow diagram specifies
    - **SECONDARY EVENT**: `charge.refunded` - For processing refunds
    - The existing code handles `payment_intent.succeeded` which is a different flow (direct API integration vs hosted checkout)
    - **DECISION**: You should UPDATE the existing WebhookResource to handle checkout.session.completed instead of payment_intent.succeeded, OR add support for both events if the system needs to support both payment flows.

*   **Critical Idempotency Pattern:** The existing code shows the correct pattern:
    ```java
    if (!CalendarOrder.STATUS_PAID.equals(order.status)) {
        // Update order
    } else {
        LOG.infof("Order already marked as PAID, skipping update");
    }
    ```
    This prevents duplicate processing of webhook retries. YOU MUST implement this same pattern.

*   **Email Job Enqueueing Pattern:** From existing code in WebhookResource:
    ```java
    DelayedJob emailJob = DelayedJob.createDelayedJob(
        order.id.toString(),
        DelayedJobQueue.EMAIL_ORDER_CONFIRMATION,
        Instant.now() // Send immediately
    );
    ```
    This is the EXACT pattern you should use in PaymentService.

*   **CRITICAL WARNING - Payment Entity:** The task mentions a "Payment" entity from I1.T8, but **I don't see this entity in the codebase**. The CalendarOrder entity has payment-related fields (stripePaymentIntentId, stripeChargeId, paidAt) but **no separate Payment entity exists**.
    *   **DECISION REQUIRED**: You need to either:
        1. Create a new Payment entity (as the task specifies), OR
        2. Continue using the embedded payment fields in CalendarOrder (current approach)
    *   **RECOMMENDATION**: Based on the GraphQL schema comment "Payment details are embedded directly in the order entity", I recommend NOT creating a separate Payment entity. Instead, store payment data in the CalendarOrder fields. However, you should VERIFY this decision with the user or check if a Payment entity was supposed to be created in I1.T8 but wasn't.

*   **Refund Handling - Stripe API Pattern:** For the `processRefund()` method and `charge.refunded` webhook, you'll need to use the Stripe SDK:
    ```java
    import com.stripe.model.Refund;
    import com.stripe.param.RefundCreateParams;

    // To CREATE a refund (in PaymentService.processRefund):
    RefundCreateParams params = RefundCreateParams.builder()
        .setPaymentIntent(paymentIntentId)
        .setAmount(amountInCents) // optional, defaults to full refund
        .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
        .build();
    Refund refund = Refund.create(params);

    // To HANDLE refund webhook (charge.refunded):
    // Extract refund amount from webhook event data
    // Update order notes with refund information
    // Optionally update order status to REFUNDED (or keep as CANCELLED)
    ```

*   **Testing Recommendation:** For integration tests, you should:
    1. Mock the Stripe webhook signature validation (or use a test webhook secret)
    2. Construct test webhook payloads with valid Stripe event JSON structure
    3. POST to /api/webhooks/stripe with the test payload and signature
    4. Verify order status changed to PAID
    5. Verify email job was enqueued (query delayed_jobs table)
    6. Test idempotency: send the same webhook twice, verify only one update occurs

*   **Webhook Response Best Practice:** According to Stripe documentation:
    - Return **200 OK** for all handled events (even unknown event types)
    - Return **400 Bad Request** ONLY for signature validation failures
    - Return **500 Internal Server Error** ONLY for unexpected exceptions
    - Stripe will retry failed webhooks (non-200 responses) with exponential backoff
    - Fast response times are important - database writes should complete in <500ms

### Architecture Decisions & Critical Notes

*   **Decision:** Based on the sequence diagram in the architecture docs, the flow uses **Stripe Checkout Sessions** (hosted payment pages), not direct PaymentIntents. The webhook should handle `checkout.session.completed`, not `payment_intent.succeeded`. The existing WebhookResource is handling the wrong event type. YOU MUST UPDATE IT.

*   **Decision:** The architecture emphasizes **idempotent webhook processing** to handle Stripe's retry mechanism (Stripe will retry webhooks up to 3 days if they fail). Your implementation must check if the payment was already processed before updating the order. The existing WebhookResource already does this correctly - preserve this pattern.

*   **Decision:** The architecture specifies that **email job enqueueing must happen within the same database transaction** as the payment confirmation to ensure consistency. If the transaction fails, the email won't be sent, which is correct (we don't want to send confirmation emails for failed payments). The existing WebhookResource already uses @Transactional - maintain this.

*   **Decision:** The task asks for a separate `StripeWebhookController.java` file, but the existing codebase uses `WebhookResource.java`. For consistency with the project's naming convention (AuthResource, BootstrapResource, CalendarResource), you should KEEP the name as `WebhookResource.java` and update it rather than creating a new file with a different name.

### Final Recommendations

1. **REFACTOR, DON'T CREATE NEW:** Update the existing `WebhookResource.java` rather than creating a new `StripeWebhookController.java`. This avoids duplicate endpoints at the same path.

2. **CREATE PaymentService:** Extract the payment processing logic from WebhookResource into a new PaymentService. This improves separation of concerns and testability.

3. **UPDATE EVENT TYPES:** Change from `payment_intent.succeeded` to `checkout.session.completed` to align with the architecture flow diagram.

4. **ADD REFUND SUPPORT:** Implement `charge.refunded` webhook handling and a `processRefund()` method in PaymentService.

5. **DECISION ON PAYMENT ENTITY:** Determine whether to create a separate Payment entity or continue using embedded payment fields in CalendarOrder. Based on the GraphQL schema, I recommend the embedded approach (no separate entity).

6. **USE EXISTING PATTERNS:** The current WebhookResource already demonstrates correct patterns for:
   - Signature validation
   - Idempotent processing
   - Transactional integrity
   - Email job enqueueing
   Preserve these patterns in your refactored code.

7. **INJECT StripeService:** Update WebhookResource to inject and use `StripeService.validateWebhookSignature()` instead of duplicating the validation code. This improves maintainability.

---

**END OF BRIEFING PACKAGE**
