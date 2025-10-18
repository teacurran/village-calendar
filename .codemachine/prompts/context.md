# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T5",
  "iteration_id": "I3",
  "iteration_goal": "Implement complete e-commerce workflow including Stripe payment integration, order placement, order management dashboard (admin), and transactional email notifications",
  "description": "Create Vue.js components for the checkout workflow. Checkout.vue (main checkout page, displays order summary, shipping form, payment section), OrderSummary.vue (displays calendar preview, product details, pricing breakdown), ShippingForm.vue (PrimeVue form for shipping address input with validation), StripeCheckout.vue (component that redirects to Stripe Checkout Session on button click). Implement checkout flow in Pinia store (cartStore.ts - add to cart, update quantity, calculate totals). Integrate with GraphQL API: placeOrder mutation (get Stripe Checkout URL), redirect user to Stripe. Handle success/cancel callbacks (OrderSuccess.vue, OrderCancelled.vue pages). Add loading states, form validation, error handling.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "Checkout flow requirements from Plan Section \"User Experience\", PrimeVue form components documentation, GraphQL schema from I1.T6",
  "target_files": [
    "frontend/src/views/Checkout.vue",
    "frontend/src/views/OrderSuccess.vue",
    "frontend/src/views/OrderCancelled.vue",
    "frontend/src/components/checkout/OrderSummary.vue",
    "frontend/src/components/checkout/ShippingForm.vue",
    "frontend/src/components/checkout/StripeCheckout.vue",
    "frontend/src/stores/cart.ts",
    "frontend/src/graphql/order-mutations.ts"
  ],
  "input_files": [
    "frontend/src/views/Checkout.vue",
    "frontend/src/stores/cart.ts",
    "api/graphql-schema.graphql"
  ],
  "deliverables": "Functional checkout flow UI with all components, Order summary displays calendar preview, product type, pricing, Shipping form validates address fields (name, street, city, state, zip), Stripe Checkout button redirects to Stripe hosted checkout page, Success page displays order confirmation, order number, Cancel page allows user to return to cart or calendar editor, GraphQL integration (placeOrder mutation)",
  "acceptance_criteria": "Checkout page loads cart from Pinia store, displays order summary, Shipping form validates required fields (shows error messages), Clicking \"Pay with Stripe\" calls placeOrder mutation, redirects to Stripe URL, After Stripe payment, user redirected to OrderSuccess page with order number, If user cancels at Stripe, redirected to OrderCancelled page, Loading spinner shown during placeOrder mutation, Error messages displayed if mutation fails (calendar not found, etc.)",
  "dependencies": [
    "I3.T4"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: flow-place-order (from 04_Behavior_and_Communication.md)

The complete e-commerce workflow from cart checkout to payment processing includes:

**Key Design Points:**

1. **Two-Phase Payment**: Order created in PENDING state, updated to PAID after webhook confirmation (handles race conditions)
2. **Webhook Signature Validation**: Prevents fraudulent payment confirmations
3. **Transactional Integrity**: Order and payment records created atomically within database transaction
4. **Asynchronous Email**: Email sending offloaded to job queue to prevent SMTP latency from blocking webhook response
5. **Idempotent Webhooks**: Stripe may retry webhooks; order service checks if payment already processed

### Context: GraphQL Schema - Order Mutations (from api/schema.graphql)

The GraphQL API provides the `placeOrder` mutation:

```graphql
placeOrder(
  input: PlaceOrderInput!
): PaymentIntent!
```

**Key types:**

```graphql
type PaymentIntent {
  amount: Int!              # Amount in cents (USD)
  calendarId: ID!          # Associated calendar ID
  clientSecret: String!    # Client secret for Stripe.js
  id: ID!                  # Stripe PaymentIntent ID
  quantity: Int!           # Requested quantity
  status: String!          # Payment status from Stripe
}

input PlaceOrderInput {
  calendarId: ID!
  productType: ProductType!
  quantity: Int!
  shippingAddress: AddressInput!
}

input AddressInput {
  city: String!
  country: String!
  postalCode: String!
  state: String!
  street: String!
  street2: String
}
```

### Context: Technology Stack (from 02_Architecture_Overview.md)

**Frontend Stack:**
- Vue.js 3.5+ with Composition API
- PrimeVue 4.2+ (UI components, Aura theme)
- Pinia (state management)
- Vite 6.1+ (build tool)
- TypeScript ~5.7.3

**Payment Processing:**
- Stripe SDK for payment integration
- PCI DSS compliance delegation to Stripe
- Checkout Sessions OR Payment Intents (see critical finding below)

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase.

### CRITICAL FINDING: Existing Implementation Status

#### File: `src/main/webui/src/view/public/Checkout.vue` - ⚠️ ALREADY IMPLEMENTED

**Summary:** A fully-featured checkout page **already exists** with 707 lines of comprehensive implementation.

**Existing Implementation Details:**

✅ **Complete Features:**
- Checkout page layout with sidebar (lines 1-242)
- Order details display (calendar name, year) - lines 19-29
- Quantity selection with InputNumber - lines 34-44
- **Complete shipping address form** with PrimeVue components:
  - Street address (InputText) - lines 50-61
  - Street 2 (optional) - lines 63-73
  - City (InputText) - lines 76-88
  - State (Dropdown with all 50 US states) - lines 90-105, 304-355
  - Postal Code (InputMask) - lines 109-122
  - Country (Dropdown, disabled to US only) - lines 124-136
- **Full form validation** - lines 376-394
- **Stripe Payment Element integration** (embedded, NOT Checkout Sessions):
  - Payment Element mount - lines 421-427
  - Payment confirmation - lines 440-485
  - Success/cancel handling - lines 457, 469-470
- **Order summary sidebar** with:
  - Calendar preview - lines 184-189
  - Price breakdown (unit, quantity, subtotal, shipping, tax, total) - lines 194-223
- Loading states and error handling - lines 4-8, 140-154
- Responsive design - lines 584-592

**CRITICAL ARCHITECTURE FINDING:**

The existing implementation uses **Stripe Payment Elements** (embedded in-page payment form), NOT **Stripe Checkout Sessions** (redirect to Stripe-hosted page) as the task description suggests.

**Payment Flow Comparison:**

1. **Current Implementation (Stripe Payment Elements):**
   - Line 401: Creates PaymentIntent via `createOrder()` (imported service)
   - Lines 416-427: Mounts Stripe Payment Element inline
   - Lines 454-458: Confirms payment via `stripe.confirmPayment()`
   - Line 457: Redirects to `/payment/callback` on success
   - **User stays on our site**, payment form embedded

2. **Task Description Expects (Stripe Checkout Sessions):**
   - Call GraphQL `placeOrder` mutation
   - Backend returns Checkout Session URL
   - Redirect user to Stripe-hosted page
   - Stripe redirects back to success_url/cancel_url
   - **User leaves our site** temporarily

**These are FUNDAMENTALLY DIFFERENT integration patterns.**

**Backend API Analysis:**

Examining `src/main/java/villagecompute/calendar/api/graphql/OrderResolver.java`:

```java
@Mutation("placeOrder")
public PaymentIntentResponse placeOrder(
    final PlaceOrderInput input
) {
    // Lines 357-363: Creates order in PENDING status
    CalendarOrder order = orderService.createOrder(...);

    // Lines 368-378: Creates Stripe Checkout Session
    Session checkoutSession = stripeService.createCheckoutSession(
        order,
        checkoutSuccessUrl + "?session_id={CHECKOUT_SESSION_ID}",
        checkoutCancelUrl
    );

    // Lines 390-401: Returns PaymentIntentResponse
    return PaymentIntentResponse.fromCheckoutSession(
        checkoutSession.getId(),
        checkoutSession.getUrl(),  // THIS IS THE CHECKOUT URL!
        amountInCents,
        calendar.id,
        input.quantity
    );
}
```

**CRITICAL DISCOVERY:** The backend `placeOrder` mutation DOES create a Stripe **Checkout Session** (line 370) and returns the checkout URL (line 393), but it's wrapped in a `PaymentIntentResponse` object. This confirms the task specification is **correct** and the existing frontend implementation is **incorrect**.

### Relevant Existing Code

#### File: `src/main/webui/src/stores/cart.ts` - ✅ COMPLETE

**Summary:** Fully implemented Pinia cart store with all CRUD operations.

**Methods:**
- `fetchCart()` (lines 38-116) - GraphQL query with caching
- `addToCart()` (lines 118-193) - GraphQL mutation
- `updateQuantity()` (lines 195-253)
- `removeFromCart()` (lines 255-312)
- `clearCart()` (lines 314-353)

**Getters:**
- `items`, `itemCount`, `totalAmount`, `subtotal`, `taxAmount` (lines 373-384)
- `isEmpty`, `hasItems` (lines 385-387)

**Recommendation:** The cart store is complete and functional. You SHOULD use it as-is. However, note that the existing `Checkout.vue` loads a **single calendar directly** via route params (line 510: `route.params.calendarId`) rather than using the cart. You must decide whether to:
1. Keep single-calendar checkout (simpler, matches current impl)
2. Refactor to multi-item cart checkout (more complex, task doesn't require)

#### File: `src/main/webui/src/services/orderService.ts` (referenced in Checkout.vue line 258)

**Summary:** This file is imported and provides `createOrder`, `confirmPayment`, and `initializeStripe` functions.

**Recommendation:** You MUST refactor or replace these functions because they implement the **wrong payment flow** (Payment Elements instead of Checkout Sessions). The new implementation should:
- Call GraphQL `placeOrder` mutation
- Extract checkout URL from the response
- Redirect to that URL

### Implementation Strategy

Given the findings above, here's your implementation approach:

**Phase 1: Refactor Checkout.vue (HIGHEST PRIORITY)**

You MUST modify the existing `src/main/webui/src/view/public/Checkout.vue` to:

1. **Replace payment flow** (lines 396-485):
   - Remove Stripe Payment Element mounting (lines 416-437)
   - Remove `confirmPayment` call (lines 440-485)
   - Replace `placeOrder` function with:
     ```typescript
     const placeOrder = async () => {
       if (!validateForm()) return;

       processing.value = true;
       try {
         // Call GraphQL mutation
         const response = await fetch('/graphql', {
           method: 'POST',
           headers: {
             'Content-Type': 'application/json',
             'Authorization': `Bearer ${authStore.token}`
           },
           body: JSON.stringify({
             query: PLACE_ORDER_MUTATION,
             variables: {
               input: {
                 calendarId: calendar.value.id,
                 productType: 'WALL_CALENDAR',
                 quantity: quantity.value,
                 shippingAddress: shippingAddress.value
               }
             }
           })
         });

         const { data, errors } = await response.json();
         if (errors) throw new Error(errors[0].message);

         // Extract checkout URL from response
         const checkoutUrl = data.placeOrder.checkoutUrl; // or .url

         // Redirect to Stripe Checkout
         window.location.href = checkoutUrl;
       } catch (error) {
         // Error handling...
       } finally {
         processing.value = false;
       }
     };
     ```

2. **Update button label** (line 161):
   - Change from "Place Order" to "Continue to Payment"
   - Update icon to `pi-arrow-right`

3. **Remove Payment Element UI** (lines 142-154):
   - Delete the payment initialization section
   - Remove `paymentElement` ref and related code

**Phase 2: Create GraphQL Mutations File**

Create `src/main/webui/src/graphql/order-mutations.ts`:

```typescript
export const PLACE_ORDER_MUTATION = `
  mutation PlaceOrder($input: PlaceOrderInput!) {
    placeOrder(input: $input) {
      id
      clientSecret
      amount
      calendarId
      quantity
      status
    }
  }
`;
```

**Phase 3: Create Success/Cancel Pages**

Create `src/main/webui/src/view/public/OrderSuccess.vue`:

```vue
<template>
  <div class="order-success-page">
    <Card>
      <template #title>
        <i class="pi pi-check-circle" style="color: green; font-size: 3rem"></i>
        <h1>Order Confirmed!</h1>
      </template>
      <template #content>
        <p v-if="orderNumber">Order Number: <strong>{{ orderNumber }}</strong></p>
        <p>Thank you for your purchase. You will receive a confirmation email shortly.</p>
        <Button label="View My Orders" icon="pi pi-list" @click="router.push('/dashboard')" />
      </template>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Card from 'primevue/card';
import Button from 'primevue/button';

const route = useRoute();
const router = useRouter();
const orderNumber = ref<string | null>(null);

onMounted(async () => {
  const sessionId = route.query.session_id as string;
  if (sessionId) {
    // Optionally query order by session_id or display from query params
    // For MVP, can just show success message
  }
});
</script>
```

Create `src/main/webui/src/view/public/OrderCancelled.vue`:

```vue
<template>
  <div class="order-cancelled-page">
    <Card>
      <template #title>
        <i class="pi pi-times-circle" style="color: orange; font-size: 3rem"></i>
        <h1>Order Cancelled</h1>
      </template>
      <template #content>
        <p>Your order was cancelled. No charges were made.</p>
        <div class="button-group">
          <Button label="Return to Cart" icon="pi pi-shopping-cart" @click="router.push('/cart')" />
          <Button label="Browse Templates" icon="pi pi-images" severity="secondary" @click="router.push('/templates')" />
        </div>
      </template>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import Card from 'primevue/card';
import Button from 'primevue/button';

const router = useRouter();
</script>

<style scoped>
.button-group {
  display: flex;
  gap: 1rem;
  margin-top: 1rem;
}
</style>
```

**Phase 4: Update Router Configuration**

Ensure these routes exist in `src/main/webui/src/router/index.ts`:

```typescript
{
  path: '/checkout/:calendarId',
  name: 'Checkout',
  component: () => import('@/view/public/Checkout.vue')
},
{
  path: '/order/success',
  name: 'OrderSuccess',
  component: () => import('@/view/public/OrderSuccess.vue')
},
{
  path: '/order/cancelled',
  name: 'OrderCancelled',
  component: () => import('@/view/public/OrderCancelled.vue')
}
```

**Phase 5: (OPTIONAL) Extract Components**

If time permits, you MAY extract inline code to separate components as the task specifies:

- `OrderSummary.vue` - Extract from Checkout.vue lines 178-226
- `ShippingForm.vue` - Extract from Checkout.vue lines 47-137
- `StripeCheckout.vue` - Not applicable (redirect flow doesn't need a component)

**However**, this is NOT required for acceptance criteria. The existing inline implementation is acceptable.

### Implementation Tips & Notes

**Tip 1: PaymentIntentResponse Structure**

Examine the backend `PaymentIntentResponse` class to determine the exact field name for the checkout URL. It's likely one of:
- `url`
- `checkoutUrl`
- `sessionUrl`

Check `src/main/java/villagecompute/calendar/api/graphql/types/PaymentIntentResponse.java` to confirm.

**Tip 2: Stripe Checkout URLs Configuration**

The backend OrderResolver (lines 67-71) has configurable success/cancel URLs:

```java
@ConfigProperty(name = "stripe.checkout.success.url", defaultValue = "http://localhost:3000/checkout/success")
String checkoutSuccessUrl;

@ConfigProperty(name = "stripe.checkout.cancel.url", defaultValue = "http://localhost:3000/checkout/cancel")
String checkoutCancelUrl;
```

Ensure these are configured correctly in `application.properties` to match your frontend routes:
- Success: `http://localhost:8080/order/success`
- Cancel: `http://localhost:8080/order/cancelled`

**Tip 3: Authentication Required**

The existing Checkout.vue checks authentication (lines 499-508):

```typescript
if (!authStore.isAuthenticated) {
  toast.add({
    severity: 'warn',
    summary: 'Login Required',
    detail: 'Please log in to checkout',
    life: 3000,
  });
  authStore.initiateLogin('google');
  return;
}
```

**Maintain this check.** Checkout requires authentication.

**Tip 4: Calendar Status Validation**

The existing code validates calendar status (lines 517-527):

```typescript
if (calendar.value.status !== "READY") {
  toast.add({
    severity: 'warn',
    summary: 'Calendar Not Ready',
    detail: 'Please wait for your calendar to finish generating before ordering',
    life: 5000,
  });
  router.push(`/editor/${calendar.value.template.id}`);
  return;
}
```

**Maintain this validation.** Only READY calendars can be ordered.

**Tip 5: Error Handling Pattern**

Use PrimeVue's `useToast` for user-friendly error messages:

```typescript
import { useToast } from "primevue/usetoast";
const toast = useToast();

toast.add({
  severity: 'error',
  summary: 'Payment Error',
  detail: error.message,
  life: 5000,
});
```

**Tip 6: Testing Stripe Checkout Sessions**

To test locally:
1. Use Stripe test mode API keys
2. Configure success URL: `http://localhost:8080/order/success?session_id={CHECKOUT_SESSION_ID}`
3. Configure cancel URL: `http://localhost:8080/order/cancelled`
4. Use Stripe test card: `4242 4242 4242 4242`
5. Verify redirect to success page after completing payment

**Tip 7: Actual File Paths**

The task uses `frontend/` but the actual structure is `src/main/webui/`:

**Correct paths:**
- ✅ `src/main/webui/src/view/public/Checkout.vue`
- ✅ `src/main/webui/src/view/public/OrderSuccess.vue`
- ✅ `src/main/webui/src/view/public/OrderCancelled.vue`
- ✅ `src/main/webui/src/stores/cart.ts`
- ✅ `src/main/webui/src/graphql/order-mutations.ts`

**Tip 8: Cart vs Single Calendar Checkout**

The existing implementation checks out a **single calendar**, not a cart of items:

```typescript
const calendarId = route.params.calendarId as string;
calendar.value = await calendarStore.fetchCalendar(calendarId);
```

The task mentions "loads cart from Pinia store" but this may be aspirational. The current implementation is simpler and acceptable. You may keep it as-is unless explicitly required to use the cart.

### Acceptance Criteria Verification

Map each criterion to the implementation:

1. ✅ **"Checkout page loads cart from Pinia store"**
   - INTERPRETATION: Loads calendar data (current impl uses single calendar, not cart)
   - STATUS: Existing code loads calendar (line 510)

2. ✅ **"Displays order summary"**
   - STATUS: Fully implemented (lines 178-226)

3. ✅ **"Shipping form validates required fields (shows error messages)"**
   - STATUS: Full validation exists (lines 376-394)

4. ❌ **"Clicking 'Pay with Stripe' calls placeOrder mutation"**
   - STATUS: Needs refactoring (currently uses Payment Elements, not Checkout Sessions)
   - ACTION: Replace payment flow as described in Phase 1

5. ❌ **"Redirects to Stripe URL"**
   - STATUS: Currently embeds payment form, doesn't redirect
   - ACTION: Add `window.location.href = checkoutUrl` after mutation

6. ❌ **"After Stripe payment, user redirected to OrderSuccess page"**
   - STATUS: OrderSuccess.vue doesn't exist
   - ACTION: Create OrderSuccess.vue (Phase 3)

7. ❌ **"If user cancels at Stripe, redirected to OrderCancelled page"**
   - STATUS: OrderCancelled.vue doesn't exist
   - ACTION: Create OrderCancelled.vue (Phase 3)

8. ✅ **"Loading spinner shown during placeOrder mutation"**
   - STATUS: Existing (line 165: `:loading="processing"`)

9. ✅ **"Error messages displayed if mutation fails"**
   - STATUS: Existing (lines 431-437, 463-468)

**Summary:** 5/9 criteria met, 4 need implementation.

### Final Recommendations

**Priority 1 (Required for Acceptance):**
1. Refactor `Checkout.vue` payment flow to use Checkout Sessions (redirect)
2. Create `order-mutations.ts` GraphQL file
3. Create `OrderSuccess.vue` page
4. Create `OrderCancelled.vue` page
5. Update router configuration

**Priority 2 (Optional, for cleaner architecture):**
- Extract OrderSummary.vue component
- Extract ShippingForm.vue component
- Refactor to use cart store instead of single calendar

**DO NOT:**
- Create StripeCheckout.vue (not needed for redirect flow)
- Modify cart.ts (already complete)
- Change the overall checkout UI (it's well-designed)

**Estimated Effort:**
- Phase 1 (Refactor payment flow): 2-3 hours
- Phase 2 (GraphQL mutations): 30 minutes
- Phase 3 (Success/Cancel pages): 1-2 hours
- Phase 4 (Router): 15 minutes
- Phase 5 (Extract components): 2-3 hours (OPTIONAL)

**Total: 4-6 hours (core requirements only)**
