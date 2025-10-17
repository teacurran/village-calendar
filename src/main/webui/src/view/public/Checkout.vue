<template>
  <div class="checkout-page">
    <div class="container">
      <!-- Loading state -->
      <div v-if="pageLoading" class="loading-container">
        <ProgressSpinner />
        <p class="loading-text">Loading checkout...</p>
      </div>

      <!-- Main checkout content -->
      <div v-else-if="calendar" class="checkout-grid">
        <!-- Left: Checkout Form -->
        <Card class="checkout-form">
          <template #title>
            <h2>Checkout</h2>
          </template>
          <template #content>
            <!-- Order Summary Info -->
            <div class="order-info-panel">
              <h3>Order Details</h3>
              <div class="order-info-item">
                <span class="label">Calendar:</span>
                <span class="value">{{ calendar.name }}</span>
              </div>
              <div class="order-info-item">
                <span class="label">Year:</span>
                <span class="value">{{ calendar.year }}</span>
              </div>
            </div>

            <Divider />

            <!-- Quantity Selection -->
            <div class="form-group">
              <label for="quantity" class="form-label">Quantity</label>
              <InputNumber
                id="quantity"
                v-model="quantity"
                :min="1"
                :max="100"
                show-buttons
                class="w-full"
              />
            </div>

            <!-- Shipping Address Form -->
            <h3 class="section-heading">Shipping Address</h3>

            <div class="form-group">
              <label for="street" class="form-label">Street Address *</label>
              <InputText
                id="street"
                v-model="shippingAddress.street"
                placeholder="123 Main St"
                class="w-full"
                :class="{ 'p-invalid': validationErrors.street }"
              />
              <small v-if="validationErrors.street" class="p-error">{{
                validationErrors.street
              }}</small>
            </div>

            <div class="form-group">
              <label for="street2" class="form-label"
                >Apartment, Suite, etc. (optional)</label
              >
              <InputText
                id="street2"
                v-model="shippingAddress.street2"
                placeholder="Apt 4B"
                class="w-full"
              />
            </div>

            <div class="form-row">
              <div class="form-group">
                <label for="city" class="form-label">City *</label>
                <InputText
                  id="city"
                  v-model="shippingAddress.city"
                  placeholder="New York"
                  class="w-full"
                  :class="{ 'p-invalid': validationErrors.city }"
                />
                <small v-if="validationErrors.city" class="p-error">{{
                  validationErrors.city
                }}</small>
              </div>

              <div class="form-group">
                <label for="state" class="form-label">State *</label>
                <Dropdown
                  id="state"
                  v-model="shippingAddress.state"
                  :options="usStates"
                  option-label="name"
                  option-value="code"
                  placeholder="Select State"
                  class="w-full"
                  :class="{ 'p-invalid': validationErrors.state }"
                />
                <small v-if="validationErrors.state" class="p-error">{{
                  validationErrors.state
                }}</small>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label for="postalCode" class="form-label">ZIP Code *</label>
                <InputMask
                  id="postalCode"
                  v-model="shippingAddress.postalCode"
                  mask="99999"
                  placeholder="10001"
                  class="w-full"
                  :class="{ 'p-invalid': validationErrors.postalCode }"
                />
                <small v-if="validationErrors.postalCode" class="p-error">{{
                  validationErrors.postalCode
                }}</small>
              </div>

              <div class="form-group">
                <label for="country" class="form-label">Country *</label>
                <Dropdown
                  id="country"
                  v-model="shippingAddress.country"
                  :options="countries"
                  option-label="name"
                  option-value="code"
                  placeholder="Select Country"
                  class="w-full"
                  disabled
                />
              </div>
            </div>

            <Divider />

            <!-- Stripe Payment Element -->
            <h3 class="section-heading">Payment Information</h3>
            <div v-if="!paymentIntent" class="payment-loading">
              <ProgressSpinner style="width: 30px; height: 30px" />
              <span class="ml-2">Initializing payment...</span>
            </div>
            <div v-else-if="stripeElements" class="payment-section">
              <div ref="paymentElement" class="stripe-payment-element"></div>
              <div v-if="paymentError" class="payment-error">
                <Message severity="error" :closable="false">
                  {{ paymentError }}
                </Message>
              </div>
            </div>

            <Divider />

            <!-- Place Order Button -->
            <div class="form-actions">
              <Button
                label="Place Order"
                icon="pi pi-shopping-cart"
                size="large"
                class="w-full"
                :loading="processing"
                :disabled="!canPlaceOrder"
                @click="placeOrder"
              />
              <p class="payment-security-note">
                <i class="pi pi-lock mr-1"></i>
                Your payment information is secure and encrypted
              </p>
            </div>
          </template>
        </Card>

        <!-- Right: Order Summary Sidebar -->
        <Card class="order-summary">
          <template #title>
            <h3>Order Summary</h3>
          </template>
          <template #content>
            <!-- Calendar Preview -->
            <div class="summary-preview">
              <CalendarPreview
                :calendar-id="calendar.id"
                :scale-to-fit="true"
              />
            </div>

            <Divider />

            <!-- Price Breakdown -->
            <div class="price-breakdown">
              <div class="price-row">
                <span>Unit Price:</span>
                <span>{{ formatCurrency(unitPrice) }}</span>
              </div>
              <div class="price-row">
                <span>Quantity:</span>
                <span>{{ quantity }}</span>
              </div>
              <div class="price-row">
                <span>Subtotal:</span>
                <span>{{ formatCurrency(subtotal) }}</span>
              </div>
              <div class="price-row">
                <span>Shipping:</span>
                <span>{{
                  shippingCost > 0
                    ? formatCurrency(shippingCost)
                    : "Calculated at next step"
                }}</span>
              </div>
              <div class="price-row">
                <span>Tax:</span>
                <span>{{ formatCurrency(tax) }}</span>
              </div>
              <Divider />
              <div class="price-row total">
                <span>Total:</span>
                <span>{{ formatCurrency(total) }}</span>
              </div>
            </div>
          </template>
        </Card>
      </div>

      <!-- Error state -->
      <div v-else class="error-container">
        <Message severity="error" :closable="false">
          Failed to load calendar. Please try again.
        </Message>
        <Button
          label="Go Back"
          icon="pi pi-arrow-left"
          class="mt-4"
          @click="router.push('/')"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useToast } from "primevue/usetoast";
import {
  useCalendarStore,
  type UserCalendar,
} from "../../stores/calendarStore";
import { useAuthStore } from "../../stores/authStore";
import {
  initializeStripe,
  createOrder,
  confirmPayment,
  formatCurrency,
  type PaymentIntent,
} from "../../services/orderService";
import CalendarPreview from "../../components/CalendarPreview.vue";
import Card from "primevue/card";
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import InputNumber from "primevue/inputnumber";
import InputMask from "primevue/inputmask";
import Dropdown from "primevue/dropdown";
import ProgressSpinner from "primevue/progressspinner";
import Message from "primevue/message";
import Divider from "primevue/divider";
import type { StripeElements } from "@stripe/stripe-js";

const router = useRouter();
const route = useRoute();
const toast = useToast();
const calendarStore = useCalendarStore();
const authStore = useAuthStore();

// State
const pageLoading = ref(true);
const calendar = ref<UserCalendar | null>(null);
const quantity = ref(1);
const shippingAddress = ref({
  street: "",
  street2: "",
  city: "",
  state: "",
  postalCode: "",
  country: "US",
});
const validationErrors = ref<Record<string, string>>({});
const paymentElement = ref<HTMLElement | null>(null);
const stripeElements = ref<StripeElements | null>(null);
const paymentIntent = ref<PaymentIntent | null>(null);
const paymentError = ref<string | null>(null);
const processing = ref(false);

// Constants
const unitPrice = 29.99; // Default price - should come from backend
const shippingCost = ref(5.99);

const countries = [{ code: "US", name: "United States" }];

const usStates = [
  { code: "AL", name: "Alabama" },
  { code: "AK", name: "Alaska" },
  { code: "AZ", name: "Arizona" },
  { code: "AR", name: "Arkansas" },
  { code: "CA", name: "California" },
  { code: "CO", name: "Colorado" },
  { code: "CT", name: "Connecticut" },
  { code: "DE", name: "Delaware" },
  { code: "FL", name: "Florida" },
  { code: "GA", name: "Georgia" },
  { code: "HI", name: "Hawaii" },
  { code: "ID", name: "Idaho" },
  { code: "IL", name: "Illinois" },
  { code: "IN", name: "Indiana" },
  { code: "IA", name: "Iowa" },
  { code: "KS", name: "Kansas" },
  { code: "KY", name: "Kentucky" },
  { code: "LA", name: "Louisiana" },
  { code: "ME", name: "Maine" },
  { code: "MD", name: "Maryland" },
  { code: "MA", name: "Massachusetts" },
  { code: "MI", name: "Michigan" },
  { code: "MN", name: "Minnesota" },
  { code: "MS", name: "Mississippi" },
  { code: "MO", name: "Missouri" },
  { code: "MT", name: "Montana" },
  { code: "NE", name: "Nebraska" },
  { code: "NV", name: "Nevada" },
  { code: "NH", name: "New Hampshire" },
  { code: "NJ", name: "New Jersey" },
  { code: "NM", name: "New Mexico" },
  { code: "NY", name: "New York" },
  { code: "NC", name: "North Carolina" },
  { code: "ND", name: "North Dakota" },
  { code: "OH", name: "Ohio" },
  { code: "OK", name: "Oklahoma" },
  { code: "OR", name: "Oregon" },
  { code: "PA", name: "Pennsylvania" },
  { code: "RI", name: "Rhode Island" },
  { code: "SC", name: "South Carolina" },
  { code: "SD", name: "South Dakota" },
  { code: "TN", name: "Tennessee" },
  { code: "TX", name: "Texas" },
  { code: "UT", name: "Utah" },
  { code: "VT", name: "Vermont" },
  { code: "VA", name: "Virginia" },
  { code: "WA", name: "Washington" },
  { code: "WV", name: "West Virginia" },
  { code: "WI", name: "Wisconsin" },
  { code: "WY", name: "Wyoming" },
];

// Computed
const subtotal = computed(() => unitPrice * quantity.value);
const tax = computed(() => {
  // Simple tax calculation - 6.25% for MA
  const taxRate = shippingAddress.value.state === "MA" ? 0.0625 : 0;
  return (subtotal.value + shippingCost.value) * taxRate;
});
const total = computed(() => subtotal.value + shippingCost.value + tax.value);

const canPlaceOrder = computed(() => {
  return (
    !processing.value &&
    paymentIntent.value &&
    stripeElements.value &&
    validateForm()
  );
});

// Methods
const validateForm = (): boolean => {
  const errors: Record<string, string> = {};

  if (!shippingAddress.value.street.trim()) {
    errors.street = "Street address is required";
  }
  if (!shippingAddress.value.city.trim()) {
    errors.city = "City is required";
  }
  if (!shippingAddress.value.state) {
    errors.state = "State is required";
  }
  if (!shippingAddress.value.postalCode.trim()) {
    errors.postalCode = "ZIP code is required";
  }

  validationErrors.value = errors;
  return Object.keys(errors).length === 0;
};

const initializePayment = async () => {
  if (!authStore.token || !calendar.value) return;

  try {
    // Initialize Stripe
    const stripe = await initializeStripe();

    // Create payment intent via GraphQL
    const intent = await createOrder(
      {
        calendarId: calendar.value.id,
        quantity: quantity.value,
        shippingAddress: shippingAddress.value,
      },
      authStore.token,
    );

    paymentIntent.value = intent;

    // Create Stripe Elements
    stripeElements.value = stripe.elements({
      clientSecret: intent.clientSecret,
    });

    // Create and mount Payment Element
    const elements = stripeElements.value.create("payment", {
      layout: "tabs",
    });

    if (paymentElement.value) {
      elements.mount(paymentElement.value);
    }
  } catch (error: any) {
    console.error("Error initializing payment:", error);
    paymentError.value = error.message || "Failed to initialize payment";
    toast.add({
      severity: "error",
      summary: "Payment Error",
      detail: error.message || "Failed to initialize payment",
      life: 3000,
    });
  }
};

const placeOrder = async () => {
  if (!validateForm() || !stripeElements.value) {
    return;
  }

  processing.value = true;
  paymentError.value = null;

  try {
    const stripe = await initializeStripe();

    // Confirm payment with Stripe
    // Note: Stripe will redirect to the return_url with query parameters:
    // ?payment_intent=pi_xxx&payment_intent_client_secret=xxx&redirect_status=succeeded
    const { error } = await stripe.confirmPayment({
      elements: stripeElements.value,
      confirmParams: {
        return_url: `${window.location.origin}/payment/callback`,
      },
    });

    if (error) {
      paymentError.value = error.message || "Payment failed";
      toast.add({
        severity: "error",
        summary: "Payment Failed",
        detail: error.message,
        life: 5000,
      });
      return;
    }

    // Payment successful - Stripe will redirect to return_url
  } catch (error: any) {
    console.error("Error placing order:", error);
    paymentError.value = error.message || "Failed to place order";
    toast.add({
      severity: "error",
      summary: "Error",
      detail: error.message || "Failed to place order",
      life: 3000,
    });
  } finally {
    processing.value = false;
  }
};

// Watch quantity changes to update payment intent
watch(quantity, async () => {
  // Reinitialize payment with new amount
  if (paymentIntent.value) {
    await initializePayment();
  }
});

// Initialize
onMounted(async () => {
  try {
    // Check authentication
    if (!authStore.isAuthenticated) {
      toast.add({
        severity: "warn",
        summary: "Login Required",
        detail: "Please log in to checkout",
        life: 3000,
      });
      authStore.initiateLogin("google");
      return;
    }

    const calendarId = route.params.calendarId as string;
    calendar.value = await calendarStore.fetchCalendar(calendarId);

    if (!calendar.value) {
      throw new Error("Calendar not found");
    }

    if (calendar.value.status !== "READY") {
      toast.add({
        severity: "warn",
        summary: "Calendar Not Ready",
        detail:
          "Please wait for your calendar to finish generating before ordering",
        life: 5000,
      });
      router.push(`/editor/${calendar.value.template.id}`);
      return;
    }

    // Initialize payment
    await initializePayment();
  } catch (error: any) {
    console.error("Error loading checkout:", error);
    toast.add({
      severity: "error",
      summary: "Error",
      detail: error.message || "Failed to load checkout",
      life: 3000,
    });
  } finally {
    pageLoading.value = false;
  }
});
</script>

<style scoped>
.checkout-page {
  min-height: 100vh;
  background: #f9fafb;
  padding: 2rem 0;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1rem;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 4rem 0;
}

.loading-text {
  color: #6b7280;
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem;
}

.checkout-grid {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 2rem;
  align-items: start;
}

@media (max-width: 1024px) {
  .checkout-grid {
    grid-template-columns: 1fr;
  }

  .order-summary {
    order: -1;
  }
}

.order-info-panel {
  background: #f3f4f6;
  padding: 1rem;
  border-radius: 8px;
  margin-bottom: 1rem;
}

.order-info-panel h3 {
  margin: 0 0 0.75rem 0;
  font-size: 1rem;
  font-weight: 600;
}

.order-info-item {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0;
}

.order-info-item .label {
  color: #6b7280;
}

.order-info-item .value {
  font-weight: 500;
}

.section-heading {
  margin: 1.5rem 0 1rem 0;
  font-size: 1.25rem;
  font-weight: 600;
}

.form-group {
  margin-bottom: 1rem;
}

.form-label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #374151;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.payment-loading {
  display: flex;
  align-items: center;
  padding: 2rem;
  background: #f3f4f6;
  border-radius: 8px;
}

.payment-section {
  margin: 1rem 0;
}

.stripe-payment-element {
  padding: 1rem;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.payment-error {
  margin-top: 1rem;
}

.form-actions {
  margin-top: 2rem;
}

.payment-security-note {
  text-align: center;
  color: #6b7280;
  font-size: 0.875rem;
  margin-top: 1rem;
}

.order-summary {
  position: sticky;
  top: 2rem;
}

.summary-preview {
  max-height: 300px;
  overflow: hidden;
  border-radius: 8px;
  background: #f3f4f6;
}

.price-breakdown {
  margin-top: 1rem;
}

.price-row {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0;
  color: #374151;
}

.price-row.total {
  font-size: 1.25rem;
  font-weight: 600;
  color: #111827;
}
</style>
