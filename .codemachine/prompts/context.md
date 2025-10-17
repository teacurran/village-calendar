# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T1",
  "iteration_id": "I3",
  "iteration_goal": "Implement complete e-commerce workflow including Stripe payment integration, order placement, order management dashboard (admin), and transactional email notifications",
  "description": "Add Stripe Java SDK dependency to Maven POM. Configure Stripe API key in application.properties (from environment variable STRIPE_API_KEY). Create StripeService with methods: createCheckoutSession (create Stripe Checkout Session for order, include line items, success/cancel URLs), retrieveSession (get session details), validateWebhookSignature (verify Stripe webhook signature for security). Implement Stripe configuration for test mode (use Stripe test API keys). Document Stripe account setup in docs/guides/stripe-setup.md (creating Stripe account, obtaining API keys, configuring webhook endpoints).",
  "agent_type_hint": "BackendAgent",
  "inputs": "Stripe integration requirements from Plan Section \"Payment Processing\", Stripe Java SDK documentation, Order entity from I1.T8",
  "target_files": [
    "pom.xml",
    "src/main/resources/application.properties",
    "src/main/java/com/villagecompute/calendar/integration/stripe/StripeService.java",
    "docs/guides/stripe-setup.md"
  ],
  "input_files": [
    "pom.xml",
    "src/main/resources/application.properties",
    "src/main/java/com/villagecompute/calendar/model/Order.java"
  ],
  "deliverables": "Stripe SDK integrated into project, StripeService class with checkout session creation, Webhook signature validation method, Stripe setup guide for developers",
  "acceptance_criteria": "StripeService.createCheckoutSession() returns valid Stripe Checkout Session URL, Webhook signature validation accepts valid Stripe signatures, rejects invalid ones, Stripe test mode uses test API keys (sk_test_...), Stripe setup guide tested with new Stripe account creation, No production API keys committed to repository (env variables only)",
  "dependencies": ["I1.T8"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Payment Processing Technology Stack (from 02_Architecture_Overview.md)

```markdown
| **Payment Processing** | Stripe SDK (Java) | Latest | PCI DSS compliance delegation. Checkout Sessions for secure payment flow. Webhooks for order updates. |

**Key Technology Decisions:**

4. **Stripe over Custom Payment**: PCI compliance is complex and risky. Stripe Checkout delegates payment form rendering, card data handling, and compliance to Stripe. 2.9% + $0.30 fee justified by reduced risk and development time.
```

### Context: Order Placement Flow Architecture (from sequence_order_placement.puml)

```plantuml
== Stripe Checkout Session Creation ==

OrderSvc -> StripeClient : createCheckoutSession(order)
activate StripeClient

StripeClient -> Stripe : POST /v1/checkout/sessions
activate Stripe
note right of StripeClient
  Request body:
  {
    line_items: [{
      name: "Custom Calendar 2025"
      amount: 3739 (cents)
      quantity: 1
    }]
    mode: "payment"
    success_url: "https://app.village.com/orders/456/success"
    cancel_url: "https://app.village.com/orders/456/cancel"
    metadata: {
      order_id: "456"
      user_id: "{user_id}"
    }
  }
end note

Stripe -> StripeClient : Checkout Session\n{ id: "cs_...", url: "https://checkout.stripe.com/..." }
deactivate Stripe

StripeClient -> OrderSvc : { sessionId, checkoutUrl }
deactivate StripeClient

OrderSvc -> DB : UPDATE calendar_orders\nSET stripe_checkout_session_id='cs_...'\nWHERE id=456
```

### Context: Webhook Signature Validation (from sequence_order_placement.puml)

```plantuml
== Webhook Signature Validation ==

API -> API : Validate Stripe-Signature header
note right of API
  Uses Stripe SDK:
  Webhook.constructEvent(
    payload,
    signature,
    webhookSecret
  )

  Prevents fraudulent webhooks
  by verifying HMAC signature.
end note

alt Signature Invalid
  API --> Stripe : 400 Bad Request\n{ error: "Invalid signature" }

  note right of API
    Malicious webhook attempt blocked.
    Log security incident.
  end note

else Signature Valid
  API -> OrderSvc : handlePaymentSuccess(checkoutSessionId)
```

### Context: PCI DSS Compliance Requirements (from 05_Operational_Architecture.md)

```markdown
**8. PCI DSS Compliance (Payment Security)**

- **Stripe Checkout**: Card data never touches application servers (entered directly into Stripe-hosted form)
- **SAQ A Eligibility**: Merchant self-assessment questionnaire (lowest scope, <100 questions)
- **Webhook Signature Validation**: Stripe webhooks verified via `Stripe-Signature` header (prevents spoofing)
- **No Card Storage**: Application stores only `stripe_payment_intent_id` (token, not card numbers)
```

### Context: Secrets Management (from 05_Operational_Architecture.md)

```markdown
**6. Secrets Management**

- **Environment Variables**: OAuth client secrets, Stripe API keys, database passwords injected via Kubernetes secrets
- **No Hardcoded Secrets**: Code repository contains only placeholder values (`${STRIPE_API_KEY}`)
- **Access Control**: Kubernetes RBAC limits secret access to application service account
- **Rotation**: Secrets rotated annually (automated via Terraform for cloud resources)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `pom.xml`
    *   **Summary:** Maven project configuration with Quarkus BOM, dependencies, and build plugins. **CRITICAL FINDING: Stripe Java SDK is ALREADY added at lines 128-132 (version 29.5.0)**
    *   **Recommendation:** You do NOT need to add the Stripe dependency again. It's already configured. Verify the version is appropriate (29.5.0 is current as of the project snapshot).

*   **File:** `src/main/resources/application.properties`
    *   **Summary:** Quarkus application configuration including database, OAuth, mailer, R2 storage. **CRITICAL FINDING: Stripe configuration is ALREADY present at lines 132-135**
    *   **Recommendation:** The Stripe configuration keys are already defined:
        ```properties
        stripe.api.key=${STRIPE_SECRET_KEY:sk_test_placeholder}
        stripe.publishable.key=${STRIPE_PUBLISHABLE_KEY:pk_test_placeholder}
        stripe.webhook.secret=${STRIPE_WEBHOOK_SECRET:whsec_placeholder}
        ```
        You do NOT need to add these properties. They follow the correct pattern of environment variable injection with placeholder defaults.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** JPA entity for e-commerce orders with Panache active record pattern. Contains all order fields including `stripePaymentIntentId`, `stripeChargeId`, status constants, and helper methods like `markAsPaid()`.
    *   **Recommendation:** This is the Order entity referenced in the task. You MUST use this entity class when creating the StripeService. Key fields to note:
        - `stripePaymentIntentId` (String, line 71) - stores Stripe PaymentIntent ID
        - `stripeChargeId` (String, line 74) - stores Stripe Charge ID
        - Status constants defined (lines 87-92): `STATUS_PENDING`, `STATUS_PAID`, `STATUS_PROCESSING`, `STATUS_SHIPPED`, `STATUS_DELIVERED`, `STATUS_CANCELLED`
        - `findByStripePaymentIntent()` static method (line 133) - already implemented for webhook processing

*   **File:** `src/main/java/villagecompute/calendar/services/PaymentService.java`
    *   **Summary:** **CRITICAL FINDING: A PaymentService already exists!** This service handles PaymentIntent creation, updates, and confirmations. It has methods: `createPaymentIntent()`, `updatePaymentIntent()`, `confirmPayment()`, `getPaymentIntent()`, `cancelPayment()`, plus helper methods `getPublishableKey()` and `getWebhookSecret()`.
    *   **Recommendation:** **THIS IS CRITICAL!** The task asks you to create a `StripeService`, but a `PaymentService` already exists. You have two options:
        1. **Option A (Recommended):** Create the new `StripeService` class at the specified path (`src/main/java/villagecompute/calendar/integration/stripe/StripeService.java`) with Stripe Checkout Session methods (as the task specifies), and treat the existing `PaymentService` as a complementary service that handles PaymentIntent operations. The `StripeService` would focus on Checkout Sessions specifically.
        2. **Option B:** Extend the existing `PaymentService` to include the Checkout Session methods instead of creating a new service. However, this conflicts with the task's target file path specification.

        **I STRONGLY RECOMMEND Option A** - create the new `StripeService` in the integration package as specified, and let it focus on Checkout Sessions while the existing PaymentService handles PaymentIntent operations. This follows separation of concerns and matches the task specification.

*   **File:** `src/main/java/villagecompute/calendar/api/rest/WebhookResource.java`
    *   **Summary:** **CRITICAL FINDING: Webhook handling is ALREADY IMPLEMENTED!** This REST resource handles Stripe webhooks at `/api/webhooks/stripe`, validates signatures using `Webhook.constructEvent()`, and processes `payment_intent.succeeded` and `payment_intent.payment_failed` events.
    *   **Recommendation:** The webhook signature validation logic already exists in `handleStripeWebhook()` method (lines 101-154). The validation code at lines 129-142 uses the exact pattern the architecture requires:
        ```java
        Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        ```
        You do NOT need to reimplement this. Your task should focus on creating the `StripeService` with Checkout Session methods. The `validateWebhookSignature()` method requirement in the task may already be satisfied by this existing implementation.

### Implementation Tips & Notes

*   **Tip:** The project uses `@ConfigProperty` injection pattern for configuration values. The existing `PaymentService` shows the correct pattern:
    ```java
    @ConfigProperty(name = "stripe.api.key")
    String stripeApiKey;
    ```
    You SHOULD use this same pattern in your `StripeService`.

*   **Tip:** The Stripe SDK is initialized in `PaymentService` with a `@PostConstruct` method:
    ```java
    @PostConstruct
    void init() {
        Stripe.apiKey = stripeApiKey;
        LOG.info("Payment service initialized with Stripe API");
    }
    ```
    Since Stripe SDK uses a static API key, this only needs to be set once. You DO NOT need to reinitialize it in `StripeService` if `PaymentService` is already doing it. However, if you want `StripeService` to be independently usable, you MAY include the same initialization pattern.

*   **Note:** The architecture emphasizes that "Stripe Checkout" mode delegates the payment form to Stripe's hosted page, which is different from the PaymentIntent flow. Your `StripeService.createCheckoutSession()` should create a Checkout Session using `com.stripe.param.checkout.SessionCreateParams`, NOT a PaymentIntent. The sequence diagram shows the checkout flow creates a `checkout/sessions` resource, not a `payment_intents` resource directly.

*   **Note:** The existing webhook handler processes `payment_intent.succeeded` events. For Checkout Sessions, Stripe typically sends `checkout.session.completed` events. You may need to coordinate with the webhook implementation to handle both event types or understand that Checkout Sessions create PaymentIntents under the hood.

*   **Warning:** The task specifies test mode configuration. The existing `application.properties` uses placeholders like `sk_test_placeholder`. You MUST ensure your documentation guide (`stripe-setup.md`) explains:
    1. How to obtain test API keys from Stripe dashboard (keys starting with `sk_test_` and `pk_test_`)
    2. How to configure webhook secrets for test mode
    3. How to test webhook delivery using Stripe CLI or ngrok
    4. That production keys must NEVER be committed to the repository

*   **Tip:** The project already has documentation guides in `docs/guides/` following a consistent format. Reference the existing `oauth-setup.md` and `database-setup.md` for the documentation style and structure. Your `stripe-setup.md` should follow the same format.

*   **Tip:** The sequence diagram shows the Checkout Session should include:
    - `line_items` with calendar details (name, amount in cents, quantity)
    - `mode: "payment"` (one-time payment, not subscription)
    - `success_url` and `cancel_url` for redirect after payment
    - `metadata` with `order_id` and `user_id` for webhook correlation

    Make sure your `createCheckoutSession()` method signature accepts these parameters.

*   **Note:** The architecture mentions idempotency keys for preventing duplicate charges. The existing `PaymentService.createPaymentIntent()` shows how to implement this:
    ```java
    String idempotencyKey = "order_" + orderId + "_" + System.currentTimeMillis();
    PaymentIntent.create(params, RequestOptions.builder().setIdempotencyKey(idempotencyKey).build());
    ```
    Consider whether `createCheckoutSession()` should also use idempotency keys (Stripe supports them for all API operations).

### Task-Specific Guidance

Based on the complete analysis, here's what you actually need to implement:

1. **Create New File:** `src/main/java/villagecompute/calendar/integration/stripe/StripeService.java`
   - This should be a new `@ApplicationScoped` service
   - Implement `createCheckoutSession(CalendarOrder order, String successUrl, String cancelUrl)` method
   - Implement `retrieveSession(String sessionId)` method
   - You MAY implement `validateWebhookSignature(String payload, String signature)` as a public method, but note that webhook validation is already functional in `WebhookResource`
   - Use `@ConfigProperty` to inject Stripe config values
   - Follow the same patterns as the existing `PaymentService` for initialization and error handling

2. **Modify Existing File:** `pom.xml`
   - **NO CHANGES NEEDED** - Stripe SDK already present

3. **Modify Existing File:** `src/main/resources/application.properties`
   - **NO CHANGES NEEDED** - Stripe configuration already present

4. **Create New File:** `docs/guides/stripe-setup.md`
   - Document Stripe account creation process
   - Explain how to obtain test API keys (secret key, publishable key, webhook secret)
   - Provide step-by-step instructions for configuring webhook endpoints
   - Include testing instructions (Stripe CLI or ngrok for local development)
   - Emphasize security: never commit production keys
   - Follow the format of existing guides in `docs/guides/`

### Testing Recommendations

- Write unit tests for `StripeService` methods
- Mock Stripe API calls to avoid hitting real Stripe during tests
- Test both success and failure scenarios for checkout session creation
- Verify webhook signature validation rejects invalid signatures
- Ensure idempotency (if implemented) prevents duplicate session creation
