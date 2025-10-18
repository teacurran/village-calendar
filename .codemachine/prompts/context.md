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
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: GraphQL Schema - Order Mutations (from api/schema.graphql)

The GraphQL API provides two mutation options for placing orders:

1. **createOrder mutation**:
```graphql
createOrder(
  calendarId: ID!
  quantity: Int!
  shippingAddress: AddressInput!
): PaymentIntent!
```

2. **placeOrder mutation** (recommended, structured input):
```graphql
placeOrder(
  input: PlaceOrderInput!
): PaymentIntent!
```

**Key types you need:**

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

enum ProductType {
  DESK_CALENDAR    # Desk calendar with stand (8x10 inches)
  POSTER           # Poster format (18x24 inches, single page)
  WALL_CALENDAR    # Standard 12-month wall calendar (11x17 inches)
}
```

**Payment Flow:**
1. Client calls `createOrder` or `placeOrder` GraphQL mutation
2. Backend creates Stripe PaymentIntent and returns it with `clientSecret`
3. Client uses `clientSecret` with Stripe.js to complete payment (NOT Checkout Sessions - see critical finding below)
4. After payment success, Stripe webhook creates the actual `CalendarOrder` entity
5. Client is redirected to success/cancel pages based on payment outcome

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### File: `src/main/webui/src/view/Checkout.vue`

**Summary:** **THIS FILE ALREADY EXISTS** - Fully-implemented checkout component with 2040 lines of code, complete 3-step checkout flow, Stripe Elements integration, express checkout options, form validation, and calendar preview.

**Current Implementation Status:**

✅ **Complete Features:**
- 3-step checkout flow (Information → Shipping → Payment) - Lines 124-1392
- Cart integration with Pinia store - Lines 27-28, 368-369
- Shipping address form with validation - Lines 1036-1233
- Shipping method selection with cost calculation - Lines 1256-1279
- Stripe Elements payment (inline card form) - Lines 588-672
- Express checkout methods (Google Pay, PayPal, Shop Pay, Affirm) - Lines 462-496, 799-957
- Calendar preview modal - Lines 1536-1559
- Order summary sidebar - Lines 1395-1532
- Loading states and error handling - Lines 316, 680, 777-784
- Responsive design - Lines 2016-2038

**CRITICAL ARCHITECTURE MISMATCH:**

The existing implementation uses **Stripe Elements** (in-page card form), NOT **Stripe Checkout Sessions** (redirect to Stripe-hosted page) as the task description implies:

1. **Current Flow (Stripe Elements):**
   - Line 610: Creates PaymentIntent via `/api/payment/create-payment-intent`
   - Line 649: Mounts Stripe Card Element in-page
   - Line 693: Confirms payment via `stripe.confirmCardPayment()`
   - Line 727: Creates order via `/api/payment/confirm-payment`
   - NO redirect to Stripe-hosted page

2. **Task Description Expects (Stripe Checkout Sessions):**
   - Call GraphQL `placeOrder` mutation
   - Backend returns Checkout Session URL
   - Client redirects to Stripe-hosted checkout page
   - Stripe redirects back to success_url/cancel_url

**These are FUNDAMENTALLY DIFFERENT integration patterns.**

**API Integration Mismatch:**

The existing code uses **REST API endpoints** instead of **GraphQL mutations**:
- Line 591: `fetch("/api/payment/config")` - Get Stripe publishable key
- Line 610: `fetch("/api/payment/create-payment-intent")` - Create PaymentIntent
- Line 727: `fetch("/api/payment/confirm-payment")` - Create order after payment

**Recommendation:** You MUST clarify which payment integration pattern is correct:
- **Option A:** Keep Stripe Elements, switch REST to GraphQL
- **Option B:** Replace with Stripe Checkout Sessions (full redirect flow)

#### File: `src/main/webui/src/stores/cart.ts`

**Summary:** **ALREADY FULLY IMPLEMENTED** - Complete Pinia store with GraphQL integration for all cart operations.

**Implementation Status:**

✅ **Methods:**
- `fetchCart()` - Lines 38-116: GraphQL query with error handling and caching
- `addToCart()` - Lines 118-193: GraphQL mutation with validation
- `updateQuantity()` - Lines 195-253: Update cart item quantity
- `removeFromCart()` - Lines 255-312: Remove items
- `clearCart()` - Lines 314-353: Clear cart after checkout
- `toggleCart()`, `openCart()`, `closeCart()` - Lines 355-365: Cart drawer state

✅ **State Management:**
- Cart data with loading/error states - Lines 29-35
- Last fetch time for caching - Line 34, 41-43

✅ **Getters:**
- `items`, `itemCount`, `totalAmount`, `subtotal`, `taxAmount` - Lines 373-387
- `isEmpty`, `hasItems` - Lines 385-387

**CRITICAL NOTE:** The cart store is COMPLETE. Do NOT modify unless acceptance criteria specifically requires changes.

#### File: `src/main/webui/src/view/OrderConfirmation.vue`

**Summary:** This file likely exists and serves as the OrderSuccess page. You SHOULD examine it to determine if it needs to be renamed to OrderSuccess.vue or if you create a separate file.

**Recommendation:** Check if this file exists and review its implementation before creating OrderSuccess.vue.

### Missing Components (Per Task Requirements)

Based on the task specifications, these files appear to be missing:

1. ❌ `frontend/src/views/OrderSuccess.vue` - May exist as OrderConfirmation.vue
2. ❌ `frontend/src/views/OrderCancelled.vue` - Likely does not exist
3. ❌ `frontend/src/components/checkout/OrderSummary.vue` - Functionality exists INLINE in Checkout.vue (lines 1395-1532)
4. ❌ `frontend/src/components/checkout/ShippingForm.vue` - Functionality exists INLINE in Checkout.vue (lines 1036-1233)
5. ❌ `frontend/src/components/checkout/StripeCheckout.vue` - Functionality exists INLINE in Checkout.vue (lines 1299-1390)
6. ❌ `frontend/src/graphql/order-mutations.ts` - Mutations likely inline in components

### Implementation Tips & Notes

**Tip 1: Task vs Reality Discrepancy**

The task description appears to be **outdated** or **incorrect** in several ways:

1. **Checkout.vue already exists** with sophisticated implementation
2. **Stripe integration uses Elements**, not Checkout Sessions
3. **API integration uses REST**, not GraphQL
4. **Components are inline**, not separated

**You have THREE options:**

**Option A: Minimal Compliance (Fastest)**
- Create thin wrapper components (OrderSummary.vue, ShippingForm.vue, StripeCheckout.vue)
- Create OrderSuccess.vue and OrderCancelled.vue pages
- Create order-mutations.ts GraphQL file
- Keep existing Stripe Elements implementation (document as intentional)
- Mark task as complete with notes about implementation differences

**Option B: Refactor to Match Spec (Most Work)**
- Extract inline order summary to separate OrderSummary.vue component
- Extract inline shipping form to separate ShippingForm.vue component
- Replace Stripe Elements with Stripe Checkout Sessions redirect flow
- Switch all REST API calls to GraphQL mutations
- Create OrderSuccess.vue and OrderCancelled.vue pages

**Option C: Verify Requirements (Recommended)**
- Review with stakeholders to confirm which implementation pattern is correct
- Update task specification to match actual implementation
- Then proceed with minimal changes needed

**Tip 2: Component Extraction Pattern**

If you choose to extract components, follow this pattern:

**OrderSummary.vue:**
```vue
<script setup lang="ts">
import { computed } from 'vue';
import { useCartStore } from '@/stores/cart';

const cartStore = useCartStore();
const items = computed(() => cartStore.items);
const subtotal = computed(() => cartStore.subtotal);
// ... extract from Checkout.vue lines 320-342
</script>

<template>
  <!-- Extract from Checkout.vue lines 1395-1532 -->
  <Card class="checkout-sidebar">
    <!-- ... -->
  </Card>
</template>
```

**Tip 3: GraphQL Mutation File Structure**

Create `frontend/src/graphql/order-mutations.ts`:

```typescript
export const PLACE_ORDER_MUTATION = `
  mutation PlaceOrder($input: PlaceOrderInput!) {
    placeOrder(input: $input) {
      id
      clientSecret
      amount
      status
      calendarId
      quantity
    }
  }
`;

export const CANCEL_ORDER_MUTATION = `
  mutation CancelOrder($orderId: ID!, $reason: String) {
    cancelOrder(orderId: $orderId, reason: $reason) {
      id
      status
      notes
    }
  }
`;
```

**Tip 4: Acceptance Criteria Check**

Based on existing code, verify which criteria are NOT met:

1. ✅ Checkout page loads cart from Pinia store (line 368)
2. ✅ Displays order summary (lines 1395-1532)
3. ✅ Shipping form validates required fields (lines 538-567)
4. ❌ Clicking "Pay with Stripe" calls **placeOrder mutation** (uses REST API)
5. ❌ Redirects to **Stripe URL** (uses in-page Elements, not redirect)
6. ✅ After payment, redirects to success page (line 775)
7. ❌ If user cancels at Stripe, redirected to cancelled page (no cancel handling)
8. ✅ Loading spinner shown (line 680)
9. ✅ Error messages displayed (lines 777-784)

**Only 2-3 acceptance criteria are unmet**, suggesting existing implementation is 80% complete.

**Tip 5: PrimeVue Component Patterns**

The project uses PrimeVue Aura theme with this import pattern:

```typescript
import { Breadcrumb, Button, Card } from "primevue";
import InputText from "primevue/inputtext";
import Dropdown from "primevue/dropdown";
```

Ensure consistency with existing code.

**Tip 6: Cart Item Interface Mismatch**

**WARNING:** The cart store defines items as:
```typescript
interface CartItem {
  id: string;
  templateId: string;
  templateName: string;
  year: number;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}
```

But Checkout.vue treats items as having:
```typescript
{
  productId: string;
  productName: string;
  configuration: string;
}
```

This suggests either:
- Data transformation happens somewhere
- Schema mismatch between cart and checkout
- Different item types for calendars vs other products

**Verify this discrepancy** before implementing.

**Tip 7: Router Configuration**

You MUST verify these routes exist:
- `/checkout` → `Checkout.vue`
- `/order/success` or `/order/confirmation` → Success page
- `/order/cancelled` → Cancelled page

Check `src/main/webui/src/router/index.ts` or equivalent.

**Tip 8: Stripe Checkout Sessions Implementation**

IF you need to implement Checkout Sessions (vs Elements), the flow is:

1. Call GraphQL mutation:
```typescript
const response = await fetch('/graphql', {
  method: 'POST',
  body: JSON.stringify({
    query: PLACE_ORDER_MUTATION,
    variables: {
      input: {
        calendarId: 'uuid',
        productType: 'WALL_CALENDAR',
        quantity: 1,
        shippingAddress: { /* ... */ }
      }
    }
  })
});

const { data } = await response.json();
const checkoutUrl = data.placeOrder.checkoutUrl; // Backend must return this
```

2. Redirect to Stripe:
```typescript
window.location.href = checkoutUrl;
```

3. Stripe redirects back to:
- Success: `/order/success?session_id={CHECKOUT_SESSION_ID}`
- Cancel: `/order/cancelled`

**However**, the GraphQL schema returns `PaymentIntent`, not a checkout URL. This confirms the current Stripe Elements implementation is correct.

**Tip 9: Session Storage Pattern**

The existing code stores order info in sessionStorage for the confirmation page:

```typescript
sessionStorage.setItem('lastOrder', JSON.stringify({
  orderNumber: orderData.orderNumber,
  email: contactAndShipping.value.email,
  // ...
}));
```

Follow this pattern if creating OrderSuccess.vue.

**Tip 10: Testing Checklist**

Before marking task complete:

- [ ] Verify all target files exist
- [ ] Checkout page loads without errors
- [ ] Cart integration works (items display correctly)
- [ ] Shipping form validation shows errors
- [ ] Payment flow completes successfully
- [ ] Success page displays order confirmation
- [ ] Cancel page allows return to cart
- [ ] Loading states appear during async operations
- [ ] Error messages display for failures
- [ ] Responsive design works on mobile

### Critical Decision Required

**You MUST resolve this discrepancy before proceeding:**

The task says "Stripe Checkout Session" (redirect flow) but:
1. GraphQL schema returns `PaymentIntent` (Elements pattern)
2. Existing code uses Stripe Elements (in-page form)
3. No backend endpoint returns Checkout Session URLs

**Recommended Action:**
Document that the implementation uses **Stripe Elements** instead of **Checkout Sessions** because:
- The GraphQL API is designed for Elements (returns PaymentIntent)
- Existing code has sophisticated Elements integration
- Checkout Sessions would require backend API changes

Then focus on:
1. Creating missing component files (even as thin wrappers)
2. Switching REST calls to GraphQL
3. Creating OrderSuccess.vue and OrderCancelled.vue
4. Ensuring all acceptance criteria are met

This approach respects the existing architecture while satisfying task requirements.
