# Calendar Service Frontend - Implementation Notes

## Overview

This document describes the fixes applied to Task I3.T4 (Calendar Service Frontend) to resolve critical issues and complete the customer-facing Vue.js application.

## Critical Issues Fixed

### 1. OAuth Authentication Flow ✅

**Problem:** OAuth callback handling was not implemented. Users could not log in because there was no route or component to capture the JWT token after OAuth consent.

**Solution:**
- Created `OAuthCallback.vue` component (`/view/public/OAuthCallback.vue`)
- Added route `/auth/callback` in `router.ts`
- Component extracts JWT from URL query params, hash fragment, or cookies
- Calls `authStore.handleOAuthCallback(token)` to store token and fetch user info
- Redirects to original destination after successful authentication

**Backend Integration Notes:**
- The backend OAuth endpoint (`/auth/login/{provider}`) should redirect to Google/Facebook OAuth
- After OAuth consent, the backend should redirect to `/auth/callback?token=JWT_TOKEN_HERE`
- Alternative: Backend can set an HTTP-only cookie named `auth_token`

---

### 2. Payment Flow with Webhook Integration ✅

**Problem:** The original implementation used `paymentIntent.id` as the order ID, but the PaymentIntent ID is NOT the order ID. The order is created asynchronously by a Stripe webhook after payment succeeds.

**Solution:**
- Created `PaymentCallback.vue` component (`/view/public/PaymentCallback.vue`)
- Added route `/payment/callback` in `router.ts`
- Updated `Checkout.vue` to redirect to `/payment/callback` after payment confirmation
- Created `fetchOrderByPaymentIntent()` function in `orderService.ts` to poll for order creation
- PaymentCallback polls for up to 30 seconds, checking every second for the order
- After order is found, redirects to `/order/{orderId}/confirmation`

**Flow:**
1. User completes Stripe payment
2. Stripe redirects to `/payment/callback?payment_intent=pi_xxx&redirect_status=succeeded`
3. PaymentCallback extracts `payment_intent` ID
4. Polls backend via `myOrders(status: PAID)` GraphQL query
5. Finds order by matching `stripePaymentIntentId` field
6. Redirects to order confirmation page with correct order ID

---

### 3. Stripe Configuration via Environment Variables ✅

**Problem:** The original code called `/api/payment/config` REST endpoint to fetch the Stripe publishable key, but this endpoint does not exist.

**Solution:**
- Removed `fetch('/api/payment/config')` call from `orderService.ts`
- Updated `initializeStripe()` to use `import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY`
- Created `.env` and `.env.example` files with Stripe configuration template
- Added clear error message if environment variable is not set

**Setup Instructions:**
```bash
cd village-calendar/src/main/webui
cp .env.example .env
# Edit .env and set VITE_STRIPE_PUBLISHABLE_KEY=pk_test_...
```

---

### 4. Calendar Preview GraphQL Migration ✅

**Problem:** `CalendarPreview.vue` called non-existent REST endpoints:
- `/api/calendar/generate` (POST)
- `/api/calendar/{calendarId}/preview` (GET)

**Solution:**
- Replaced `fetchCalendarSvg()` with GraphQL query: `calendar(id: $id) { generatedSvg status }`
- Added status checks for `GENERATING`, `FAILED`, `READY` states
- Updated `generatePreview()` with a placeholder (client-side preview generation not implemented)
- Added helpful error messages when preview is not available

**GraphQL Query Used:**
```graphql
query GetCalendarPreview($id: ID!) {
  calendar(id: $id) {
    id
    generatedSvg
    status
  }
}
```

---

### 5. TypeScript Type Safety ✅

**Problem:** Several Vue components used `ref(null)` without proper TypeScript type annotations, causing potential type inference issues and poor IDE support.

**Solution:**
- **Checkout.vue:**
  ```typescript
  const calendar = ref<UserCalendar | null>(null)
  const paymentIntent = ref<PaymentIntent | null>(null)
  ```

- **CalendarEditor.vue:**
  ```typescript
  const template = ref<CalendarTemplate | null>(null)
  const currentCalendar = ref<UserCalendar | null>(null)
  const calendarPreviewRef = ref<InstanceType<typeof CalendarPreview> | null>(null)
  ```

- **OrderConfirmation.vue:**
  ```typescript
  interface CalendarOrder { /* ... */ }
  const order = ref<CalendarOrder | null>(null)
  ```

---

## New Files Created

### Components
- `village-calendar/src/main/webui/src/view/public/OAuthCallback.vue`
- `village-calendar/src/main/webui/src/view/public/PaymentCallback.vue`

### Configuration
- `village-calendar/src/main/webui/.env`
- `village-calendar/src/main/webui/.env.example`

### Documentation
- `village-calendar/src/main/webui/IMPLEMENTATION_NOTES.md` (this file)

---

## Modified Files

### Services
- `village-calendar/src/main/webui/src/services/orderService.ts`
  - Removed REST `/api/payment/config` endpoint
  - Added `fetchOrderByPaymentIntent()` function
  - Updated `initializeStripe()` to use environment variables

### Components
- `village-calendar/src/main/webui/src/components/CalendarPreview.vue`
  - Replaced REST calls with GraphQL queries
  - Added status checks for calendar generation states

### Views
- `village-calendar/src/main/webui/src/view/public/Checkout.vue`
  - Updated payment return URL to `/payment/callback`
  - Added proper TypeScript types

- `village-calendar/src/main/webui/src/view/public/CalendarEditor.vue`
  - Added proper TypeScript types

- `village-calendar/src/main/webui/src/view/public/OrderConfirmation.vue`
  - Added proper TypeScript types and interface definition

### Router
- `village-calendar/src/main/webui/src/router.ts`
  - Added `/auth/callback` route
  - Added `/payment/callback` route

---

## Testing Checklist

### Prerequisites
1. Backend is running with GraphQL API at `/graphql`
2. OAuth endpoints configured: `/auth/login/google`, `/auth/login/facebook`
3. Stripe webhook endpoint configured for order creation
4. Environment variable `VITE_STRIPE_PUBLISHABLE_KEY` is set

### Manual Testing Steps

#### 1. OAuth Flow
- [ ] Navigate to `/editor/{templateId}`
- [ ] Verify redirect to OAuth login
- [ ] Complete OAuth consent
- [ ] Verify redirect to `/auth/callback?token=...`
- [ ] Verify automatic redirect back to editor
- [ ] Check that user is logged in (name/avatar in header)

#### 2. Calendar Creation & PDF Generation
- [ ] Create a calendar with name and year
- [ ] Verify polling starts (shows "GENERATING" status)
- [ ] Wait for PDF generation to complete
- [ ] Verify status changes to "READY"
- [ ] Verify preview appears
- [ ] Verify "Download PDF" button works

#### 3. Checkout Flow
- [ ] Click "Order Calendar" button
- [ ] Verify redirect to `/checkout/{calendarId}`
- [ ] Fill in shipping address
- [ ] Set quantity
- [ ] Verify Stripe Payment Element loads
- [ ] Enter test card: `4242 4242 4242 4242`, any future date, any CVC
- [ ] Click "Place Order"
- [ ] Verify redirect to `/payment/callback`
- [ ] Verify polling message appears
- [ ] Wait for order creation (webhook processing)
- [ ] Verify redirect to `/order/{orderId}/confirmation`
- [ ] Verify order details display correctly

#### 4. Order Confirmation
- [ ] Verify order ID, status, calendar info
- [ ] Verify shipping address displays
- [ ] Verify price breakdown
- [ ] Verify "Download PDF" button (if PDF ready)
- [ ] Verify delivery timeline

#### 5. Responsive Design
- [ ] Test on mobile viewport (375px width)
- [ ] Test on tablet viewport (768px width)
- [ ] Test on desktop viewport (1440px width)
- [ ] Verify all layouts adapt correctly

#### 6. Error Handling
- [ ] Test with invalid calendar ID
- [ ] Test checkout with calendar not ready
- [ ] Test payment with invalid card number
- [ ] Test payment callback with missing payment_intent
- [ ] Test OAuth callback with missing token

#### 7. TypeScript Compilation
```bash
cd village-calendar/src/main/webui
npm run type-check
```
- [ ] Verify no TypeScript errors

---

## Known Limitations & Future Improvements

### 1. Hardcoded Unit Price
The unit price is hardcoded at $29.99 in `Checkout.vue:276`. This should be fetched from the backend or calendar template configuration.

**Suggested Fix:**
- Add `price` field to `CalendarTemplate` type
- Fetch price when loading calendar for checkout
- Or: Create a separate pricing configuration query

### 2. Client-Side Preview Generation
The `generatePreview()` function in `CalendarPreview.vue` is not implemented. Currently, previews only work for saved calendars with `generatedSvg`.

**Suggested Approaches:**
- Implement client-side SVG generation using template rendering logic
- Add GraphQL mutation for server-side preview generation
- Use a preview cache to avoid regenerating on every change

### 3. Order Polling Timeout
The payment callback polls for 30 seconds. If the webhook takes longer, the user sees an error message (but the order will still be created and emailed).

**Suggested Improvements:**
- Increase timeout to 60 seconds
- Add background polling that continues after timeout
- Show a "check your email" message instead of error

### 4. OAuth Provider Hardcoded
The authentication flow currently defaults to Google (`authStore.initiateLogin('google')`). There should be a login page with provider selection.

**Suggested Fix:**
- Create `Login.vue` component with Google and Facebook buttons
- Update router guard to redirect to `/login` instead of calling `initiateLogin()` directly

### 5. Missing Error Boundaries
Vue error boundaries are not implemented. Uncaught errors will crash the entire component tree.

**Suggested Fix:**
- Add `ErrorBoundary.vue` component
- Wrap main routes with error boundary
- Log errors to monitoring service (e.g., Sentry)

---

## Backend Requirements

For this frontend to work correctly, the backend must implement:

### 1. OAuth Endpoints
- `GET /auth/login/{provider}` - Initiates OAuth flow, redirects to provider
- Backend callback: Provider redirects back to backend
- Backend validates OAuth code, creates JWT, redirects to frontend with token:
  - Option A: `GET /auth/callback?token={jwt}` (redirect to frontend)
  - Option B: Set HTTP-only cookie `auth_token={jwt}` and redirect

### 2. GraphQL Schema (Already Implemented)
- Query: `me: CalendarUser`
- Query: `calendar(id: ID!): UserCalendar` with `generatedSvg` field
- Query: `myOrders(status: OrderStatus): [CalendarOrder!]!` with `stripePaymentIntentId` field
- Mutation: `createOrder(calendarId, quantity, shippingAddress): PaymentIntent!`

### 3. Stripe Webhook
- Listen for `payment_intent.succeeded` events
- Create `CalendarOrder` entity with:
  - Extract `calendarId`, `quantity`, `shippingAddress` from PaymentIntent metadata
  - Set `stripePaymentIntentId` to `paymentIntent.id`
  - Set `status` to `PAID`
  - Set `paidAt` to current timestamp

### 4. CORS Configuration
Allow frontend origin (e.g., `http://localhost:5173` for development)

---

## Environment Variables

### Frontend (.env)
```bash
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_...  # Stripe publishable key
```

### Backend
```bash
STRIPE_SECRET_KEY=sk_test_...           # Stripe secret key
GOOGLE_CLIENT_ID=...                     # Google OAuth client ID
GOOGLE_CLIENT_SECRET=...                 # Google OAuth client secret
FACEBOOK_APP_ID=...                      # Facebook OAuth app ID
FACEBOOK_APP_SECRET=...                  # Facebook OAuth app secret
JWT_SECRET=...                           # JWT signing secret
```

---

## Summary

All critical issues identified in the code review have been resolved:

✅ OAuth callback handling implemented
✅ Payment flow fixed with webhook polling
✅ Stripe configuration via environment variables
✅ Calendar preview migrated to GraphQL
✅ TypeScript types added for type safety
✅ Router configured with new callback routes

The application now supports the complete end-to-end flow:
1. Browse templates (public)
2. OAuth login (Google/Facebook)
3. Customize calendar
4. Preview and wait for PDF generation
5. Checkout with Stripe payment
6. Payment confirmation with webhook polling
7. Order confirmation page

**Status:** READY FOR TESTING

Next steps:
1. Configure environment variables (.env file)
2. Set up backend OAuth endpoints
3. Configure Stripe webhook for order creation
4. Run manual testing checklist
5. Deploy to staging environment
