# Code Refinement Task

The previous code submission did not pass verification. While you created all the required component files, there are **critical integration issues** that prevent the checkout workflow from functioning correctly.

---

## Original Task Description

**Task I3.T5: Create Vue.js components for the checkout workflow**

Create Vue.js components for the checkout workflow. Checkout.vue (main checkout page, displays order summary, shipping form, payment section), OrderSummary.vue (displays calendar preview, product details, pricing breakdown), ShippingForm.vue (PrimeVue form for shipping address input with validation), StripeCheckout.vue (component that redirects to Stripe Checkout Session on button click). Implement checkout flow in Pinia store (cartStore.ts - add to cart, update quantity, calculate totals). Integrate with GraphQL API: placeOrder mutation (get Stripe Checkout URL), redirect user to Stripe. Handle success/cancel callbacks (OrderSuccess.vue, OrderCancelled.vue pages). Add loading states, form validation, error handling.

**Target Files:**
- frontend/src/views/Checkout.vue
- frontend/src/views/OrderSuccess.vue ✅
- frontend/src/views/OrderCancelled.vue ✅
- frontend/src/components/checkout/OrderSummary.vue ✅
- frontend/src/components/checkout/ShippingForm.vue ✅
- frontend/src/components/checkout/StripeCheckout.vue ⚠️
- frontend/src/stores/cart.ts ✅ (already complete)
- frontend/src/graphql/order-mutations.ts ✅

**Acceptance Criteria:**
- Checkout page loads cart from Pinia store, displays order summary
- Shipping form validates required fields (shows error messages)
- Clicking "Pay with Stripe" calls placeOrder mutation, redirects to Stripe URL ❌
- After Stripe payment, user redirected to OrderSuccess page with order number ❌
- If user cancels at Stripe, redirected to OrderCancelled page ❌
- Loading spinner shown during placeOrder mutation
- Error messages displayed if mutation fails

---

## Issues Detected

### 1. **CRITICAL: StripeCheckout component doesn't actually redirect to Stripe**

**File:** `src/components/checkout/StripeCheckout.vue`
**Lines:** 101-109

**Problem:** The component calls the `placeOrder` GraphQL mutation successfully, but then **throws an error** instead of redirecting to the Stripe Checkout URL:

```typescript
// In a real implementation with Checkout Sessions, you would do:
// window.location.href = paymentIntent.checkoutUrl;

// For now, since the backend returns PaymentIntent (for Stripe Elements),
// we'll show an error asking to use the main checkout page
throw new Error(
  'Payment processing is currently handled through the main checkout page. ' +
  'Please use the existing checkout flow.'
);
```

**Expected behavior:** After calling the mutation, extract the checkout URL from the response and redirect:
```typescript
const checkoutUrl = paymentIntent.url; // or paymentIntent.checkoutUrl
if (!checkoutUrl) {
  throw new Error('No checkout URL returned from server');
}
window.location.href = checkoutUrl;
```

### 2. **MISSING: Main Checkout.vue page that integrates all components**

**File:** `src/views/Checkout.vue` - **DOES NOT EXIST**

**Problem:** You created standalone components (OrderSummary, ShippingForm, StripeCheckout) but never created a main Checkout.vue page in `src/views/` that uses them together. The acceptance criteria requires a "Checkout page loads cart from Pinia store" but there's no such page.

**Note:** There ARE existing Checkout.vue files at `src/view/Checkout.vue` and `src/view/public/Checkout.vue` (note: "view" singular, not "views" plural), but you did not modify them OR create a new one in `src/views/`.

### 3. **CRITICAL: GraphQL mutation response field mismatch**

**File:** `src/graphql/order-mutations.ts`
**Lines:** 17-28

**Problem:** The GraphQL mutation only queries for these fields:
```graphql
placeOrder(input: $input) {
  id
  clientSecret
  amount
  calendarId
  quantity
  status
}
```

But according to the backend analysis in the task context, the `placeOrder` mutation returns a `PaymentIntentResponse` that includes a **checkout URL field** (likely named `url`, `checkoutUrl`, or `sessionUrl`). You're not querying for it, so even if you fix the redirect code, it won't have the URL to redirect to.

**Fix:** Add the checkout URL field to the query:
```graphql
placeOrder(input: $input) {
  id
  clientSecret
  url          # ADD THIS - or checkoutUrl or sessionUrl
  amount
  calendarId
  quantity
  status
}
```

Then update the TypeScript interface:
```typescript
export interface PaymentIntentResponse {
  id: string;
  clientSecret: string;
  url: string;  # ADD THIS
  amount: number;
  calendarId: string;
  quantity: number;
  status: string;
}
```

### 4. **INTEGRATION: Components are not used anywhere**

**Problem:** You created beautiful, functional components, but they're not integrated into any checkout flow:
- `OrderSummary.vue` - not imported or used anywhere
- `ShippingForm.vue` - not imported or used anywhere
- `StripeCheckout.vue` - not imported or used anywhere
- `OrderSuccess.vue` - created but router path uses wrong import path
- `OrderCancelled.vue` - created but router path uses wrong import path

### 5. **Router configuration has wrong import paths**

**File:** `src/router.ts`
**Lines:** You added these routes:

```typescript
{
  path: "/order/success",
  name: "order-success",
  component: () => import("./views/OrderSuccess.vue"),  // WRONG PATH
  meta: { requiresAuth: true },
},
{
  path: "/order/cancelled",
  name: "order-cancelled",
  component: () => import("./views/OrderCancelled.vue"),  // WRONG PATH
  meta: { requiresAuth: true },
},
```

**Problem:** The import paths are `./views/OrderSuccess.vue` but based on the existing router structure (which uses `./view/public/` for pages), these paths may not work correctly. The router.ts file is located at `src/main/webui/src/router.ts`, so `./views/` would resolve to `src/main/webui/src/views/` which IS where you created the files, but this is inconsistent with the rest of the app which uses `./view/public/` or `./view/`.

---

## Best Approach to Fix

You MUST complete these steps to make the checkout workflow functional:

### Step 1: Fix the StripeCheckout Component Redirect Logic

**File to modify:** `src/components/checkout/StripeCheckout.vue`

Replace lines 84-109 with:

```typescript
// Extract payment intent data
const paymentIntent = result.data?.placeOrder;

if (!paymentIntent) {
  throw new Error('No payment intent returned from server');
}

// Extract the checkout URL from the response
// Note: The field name might be 'url', 'checkoutUrl', or 'sessionUrl'
// Check the backend PaymentIntentResponse class to confirm
const checkoutUrl = paymentIntent.url || paymentIntent.checkoutUrl || paymentIntent.sessionUrl;

if (!checkoutUrl) {
  throw new Error('No checkout URL returned from server. Please contact support.');
}

// Display a brief message before redirecting
toast.add({
  severity: 'info',
  summary: 'Redirecting to Payment',
  detail: 'Taking you to Stripe checkout...',
  life: 2000,
});

// Redirect to Stripe Checkout Session
window.location.href = checkoutUrl;
```

### Step 2: Update GraphQL Mutation to Query for Checkout URL

**File to modify:** `src/graphql/order-mutations.ts`

Update the `PLACE_ORDER_MUTATION` to include the checkout URL field. First, you need to determine the correct field name by checking the backend `PaymentIntentResponse` class at `src/main/java/villagecompute/calendar/api/graphql/types/PaymentIntentResponse.java`.

Based on the task context which mentions the backend returns `checkoutSession.getUrl()`, the field is likely named `url`. Update lines 17-28:

```typescript
export const PLACE_ORDER_MUTATION = `
  mutation PlaceOrder($input: PlaceOrderInput!) {
    placeOrder(input: $input) {
      id
      clientSecret
      url
      amount
      calendarId
      quantity
      status
    }
  }
`;
```

And update the interface (lines 147-155):

```typescript
export interface PaymentIntentResponse {
  id: string;
  clientSecret: string;
  url: string;  // ADD THIS LINE
  amount: number;
  calendarId: string;
  quantity: number;
  status: string;
}
```

### Step 3: Create the Main Checkout.vue Page

**File to create:** `src/views/Checkout.vue`

This is the MOST IMPORTANT step. Create a complete checkout page that:
1. Loads a calendar from route params (e.g., `/checkout/:calendarId`)
2. Uses the `OrderSummary`, `ShippingForm`, and `StripeCheckout` components
3. Manages state for shipping address, quantity, and validation
4. Validates the form before allowing payment

Here's a starter implementation:

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useCalendarStore } from '@/stores/calendarStore';
import OrderSummary from '@/components/checkout/OrderSummary.vue';
import ShippingForm from '@/components/checkout/ShippingForm.vue';
import StripeCheckout from '@/components/checkout/StripeCheckout.vue';
import Card from 'primevue/card';
import ProgressSpinner from 'primevue/progressspinner';

const route = useRoute();
const router = useRouter();
const toast = useToast();
const calendarStore = useCalendarStore();

// State
const loading = ref(true);
const calendar = ref<any>(null);
const quantity = ref(1);
const shippingAddress = ref({
  firstName: '',
  lastName: '',
  company: '',
  street: '',
  street2: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'US',
  phone: '',
  email: '',
});
const shippingFormRef = ref<any>(null);
const stripeCheckoutRef = ref<any>(null);

// Computed
const unitPrice = computed(() => 2999); // $29.99 in cents
const subtotal = computed(() => quantity.value * unitPrice.value);
const shipping = computed(() => 500); // $5.00 in cents
const tax = computed(() => Math.round(subtotal.value * 0.07)); // 7% tax
const total = computed(() => subtotal.value + shipping.value + tax.value);

// Methods
const loadCalendar = async () => {
  try {
    const calendarId = route.params.calendarId as string;
    if (!calendarId) {
      throw new Error('No calendar ID provided');
    }

    calendar.value = await calendarStore.fetchCalendar(calendarId);

    if (calendar.value.status !== 'READY') {
      toast.add({
        severity: 'warn',
        summary: 'Calendar Not Ready',
        detail: 'This calendar must finish processing before ordering.',
        life: 5000,
      });
      router.push('/');
    }
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: 'Error Loading Calendar',
      detail: error.message,
      life: 5000,
    });
    router.push('/');
  } finally {
    loading.value = false;
  }
};

const handlePayment = () => {
  // Validate shipping form
  if (!shippingFormRef.value?.validate()) {
    toast.add({
      severity: 'error',
      summary: 'Validation Error',
      detail: 'Please fill in all required shipping information.',
      life: 5000,
    });
    return;
  }

  // Initiate Stripe checkout
  if (stripeCheckoutRef.value) {
    stripeCheckoutRef.value.initiateCheckout();
  }
};

onMounted(() => {
  loadCalendar();
});
</script>

<template>
  <div class="checkout-page">
    <div class="checkout-container">
      <!-- Loading State -->
      <div v-if="loading" class="loading-state">
        <ProgressSpinner />
        <p>Loading checkout...</p>
      </div>

      <!-- Checkout Content -->
      <div v-else class="checkout-content">
        <div class="checkout-main">
          <h1>Checkout</h1>

          <!-- Shipping Form -->
          <Card>
            <template #content>
              <ShippingForm
                ref="shippingFormRef"
                v-model="shippingAddress"
              />
            </template>
          </Card>

          <!-- Payment Section -->
          <Card class="payment-card">
            <template #title>Payment</template>
            <template #content>
              <StripeCheckout
                ref="stripeCheckoutRef"
                :calendar-id="calendar.id"
                :quantity="quantity"
                :shipping-address="shippingAddress"
                product-type="WALL_CALENDAR"
              />
            </template>
          </Card>
        </div>

        <div class="checkout-sidebar">
          <OrderSummary
            :calendar="{
              id: calendar.id,
              name: calendar.name,
              year: calendar.year,
              previewSvg: calendar.previewSvg,
              unitPrice: unitPrice / 100,
              quantity: quantity,
            }"
            :quantity="quantity"
            :subtotal="subtotal / 100"
            :shipping="shipping / 100"
            :tax="tax / 100"
            :total="total / 100"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.checkout-page {
  min-height: 100vh;
  background: #f9fafb;
  padding: 2rem;
}

.checkout-container {
  max-width: 1200px;
  margin: 0 auto;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 1rem;
}

.checkout-content {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 2rem;
}

.checkout-main {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.checkout-main h1 {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 1rem;
}

.payment-card {
  margin-top: 1rem;
}

@media (max-width: 1024px) {
  .checkout-content {
    grid-template-columns: 1fr;
  }

  .checkout-sidebar {
    order: -1;
  }
}
</style>
```

### Step 4: Add Checkout Route to Router

**File to modify:** `src/router.ts`

Add a route for the new checkout page:

```typescript
{
  path: '/checkout/:calendarId',
  name: 'checkout',
  component: () => import('./views/Checkout.vue'),
  meta: { requiresAuth: true },
},
```

### Step 5: Verify Backend Returns Correct Field Name

**Action Required:** Before testing, you MUST check the backend `PaymentIntentResponse` class to confirm the exact field name for the checkout URL.

**File to check:** `src/main/java/villagecompute/calendar/api/graphql/types/PaymentIntentResponse.java`

Look for the field that contains the Stripe Checkout Session URL. It might be:
- `url`
- `checkoutUrl`
- `sessionUrl`

Update Step 1 and Step 2 accordingly if it's not `url`.

### Step 6: Test the Complete Flow

After making all changes, test:

1. Navigate to `/checkout/:calendarId` (replace with actual calendar ID)
2. Verify order summary displays calendar details and pricing
3. Fill in shipping form - verify validation works
4. Click "Pay with Stripe" button
5. Verify you are redirected to Stripe's hosted checkout page
6. Complete payment on Stripe (use test card 4242 4242 4242 4242)
7. Verify you are redirected to `/order/success` page
8. Click cancel on Stripe checkout
9. Verify you are redirected to `/order/cancelled` page

---

## Testing Checklist

Before resubmitting, verify:

- [ ] `src/views/Checkout.vue` exists and uses all three child components
- [ ] `StripeCheckout.vue` actually redirects to Stripe (remove the `throw new Error` code)
- [ ] GraphQL mutation queries for the checkout URL field
- [ ] Frontend builds without errors (`npm run build`)
- [ ] No ESLint errors (`npm run lint`)
- [ ] Router has correct paths for all pages
- [ ] Checkout page loads when navigating to `/checkout/:calendarId`
- [ ] Shipping form validation works
- [ ] Clicking "Pay with Stripe" redirects to Stripe (not just showing error)

---

## Important Notes

- The cart store at `src/stores/cart.ts` is already complete - you don't need to modify it
- Use PrimeVue components (Card, Button, InputText, etc.) as shown in existing code
- The task context mentioned that the backend DOES support Stripe Checkout Sessions via the `placeOrder` mutation - you just need to extract and use the URL from the response
- The existing `src/view/Checkout.vue` and `src/view/public/Checkout.vue` files use a different payment flow (Stripe Elements) - DO NOT modify them. Create a new checkout in `src/views/Checkout.vue` as specified.
