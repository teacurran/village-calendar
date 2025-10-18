<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useToast } from "primevue/usetoast";
import { useCalendarStore } from "@/stores/calendarStore";
import { useAuthStore } from "@/stores/authStore";
import OrderSummary from "@/components/checkout/OrderSummary.vue";
import ShippingForm from "@/components/checkout/ShippingForm.vue";
import StripeCheckout from "@/components/checkout/StripeCheckout.vue";
import Card from "primevue/card";
import ProgressSpinner from "primevue/progressspinner";
import InputNumber from "primevue/inputnumber";
import type { AddressInput } from "@/graphql/order-mutations";

const route = useRoute();
const router = useRouter();
const toast = useToast();
const calendarStore = useCalendarStore();
const authStore = useAuthStore();

// State
const loading = ref(true);
const calendar = ref<any>(null);
const quantity = ref(1);
const shippingAddress = ref<
  AddressInput & {
    firstName: string;
    lastName: string;
    company?: string;
    phone: string;
    email: string;
  }
>({
  firstName: "",
  lastName: "",
  company: "",
  street: "",
  street2: "",
  city: "",
  state: "",
  postalCode: "",
  country: "US",
  phone: "",
  email: "",
});
const validationErrors = ref<Record<string, string>>({});
const shippingFormRef = ref<any>(null);
const stripeCheckoutRef = ref<any>(null);
const paymentProcessing = ref(false);

// Pricing calculations (in dollars for display)
const UNIT_PRICE = 29.99;
const SHIPPING_COST = 5.0;
const TAX_RATE = 0.07; // 7% tax

const unitPrice = computed(() => UNIT_PRICE);
const subtotal = computed(() => quantity.value * UNIT_PRICE);
const shipping = computed(() => SHIPPING_COST);
const tax = computed(() => subtotal.value * TAX_RATE);
const total = computed(() => subtotal.value + shipping.value + tax.value);

// Methods
const loadCalendar = async () => {
  try {
    // Check authentication
    if (!authStore.isAuthenticated) {
      toast.add({
        severity: "warn",
        summary: "Login Required",
        detail: "Please log in to checkout",
        life: 3000,
      });
      // Store the intended destination
      router.push(`/login?redirect=/checkout/${route.params.calendarId}`);
      return;
    }

    const calendarId = route.params.calendarId as string;
    if (!calendarId) {
      throw new Error("No calendar ID provided");
    }

    const fetchedCalendar = await calendarStore.fetchCalendar(calendarId);

    if (!fetchedCalendar) {
      throw new Error("Calendar not found");
    }

    // Validate calendar status
    if (fetchedCalendar.status !== "READY") {
      toast.add({
        severity: "warn",
        summary: "Calendar Not Ready",
        detail: "This calendar must finish processing before ordering.",
        life: 5000,
      });
      router.push(`/editor/${calendarId}`);
      return;
    }

    calendar.value = fetchedCalendar;

    // Pre-fill email if user is authenticated
    if (authStore.user?.email) {
      shippingAddress.value.email = authStore.user.email;
    }
  } catch (error: any) {
    console.error("Error loading calendar:", error);
    toast.add({
      severity: "error",
      summary: "Error Loading Calendar",
      detail: error.message || "Failed to load calendar",
      life: 5000,
    });
    router.push("/");
  } finally {
    loading.value = false;
  }
};

const validateShippingForm = (): boolean => {
  if (!shippingFormRef.value) {
    return false;
  }

  const isValid = shippingFormRef.value.validate();

  if (!isValid) {
    validationErrors.value = {
      form: "Please fill in all required fields",
    };
  } else {
    validationErrors.value = {};
  }

  return isValid;
};

const handlePayment = () => {
  // Clear previous errors
  validationErrors.value = {};

  // Validate shipping form
  if (!validateShippingForm()) {
    toast.add({
      severity: "error",
      summary: "Validation Error",
      detail: "Please fill in all required shipping information.",
      life: 5000,
    });

    // Scroll to top to show errors
    window.scrollTo({ top: 0, behavior: "smooth" });
    return;
  }

  // Validate quantity
  if (quantity.value < 1) {
    toast.add({
      severity: "error",
      summary: "Invalid Quantity",
      detail: "Please select at least 1 calendar.",
      life: 3000,
    });
    return;
  }

  // Initiate Stripe checkout
  if (stripeCheckoutRef.value) {
    stripeCheckoutRef.value.initiateCheckout();
  }
};

const handlePaymentInitiated = () => {
  paymentProcessing.value = true;
};

const handlePaymentError = (error: Error) => {
  paymentProcessing.value = false;
  console.error("Payment error:", error);

  // Error toast is already shown by StripeCheckout component
  // Just scroll to show the error message
  window.scrollTo({ top: document.body.scrollHeight, behavior: "smooth" });
};

// Prepare shipping address for GraphQL (only include fields that match AddressInput)
const graphqlShippingAddress = computed(
  (): AddressInput => ({
    street: shippingAddress.value.street,
    street2: shippingAddress.value.street2,
    city: shippingAddress.value.city,
    state: shippingAddress.value.state,
    postalCode: shippingAddress.value.postalCode,
    country: shippingAddress.value.country,
  }),
);

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
      <div v-else-if="calendar" class="checkout-content">
        <div class="checkout-main">
          <div class="checkout-header">
            <h1>Checkout</h1>
            <p class="checkout-subtitle">
              Complete your order for {{ calendar.name }}
            </p>
          </div>

          <!-- Order Details -->
          <Card class="details-card">
            <template #title>Order Details</template>
            <template #content>
              <div class="order-details">
                <div class="detail-row">
                  <label>Calendar</label>
                  <span>{{ calendar.name }} ({{ calendar.year }})</span>
                </div>
                <div class="detail-row">
                  <label for="quantity">Quantity</label>
                  <InputNumber
                    id="quantity"
                    v-model="quantity"
                    :min="1"
                    :max="100"
                    show-buttons
                    :disabled="paymentProcessing"
                  />
                </div>
              </div>
            </template>
          </Card>

          <!-- Shipping Form -->
          <Card class="shipping-card">
            <template #title>Shipping Information</template>
            <template #content>
              <ShippingForm
                ref="shippingFormRef"
                v-model="shippingAddress"
                :errors="validationErrors"
                :disabled="paymentProcessing"
              />
            </template>
          </Card>

          <!-- Payment Section -->
          <Card class="payment-card">
            <template #title>
              <div class="payment-title">
                <i class="pi pi-credit-card"></i>
                Payment
              </div>
            </template>
            <template #content>
              <div class="payment-content">
                <p class="payment-description">
                  You will be redirected to Stripe's secure checkout page to
                  complete your payment. Your payment information is never
                  stored on our servers.
                </p>

                <StripeCheckout
                  ref="stripeCheckoutRef"
                  :calendar-id="calendar.id"
                  :quantity="quantity"
                  :shipping-address="graphqlShippingAddress"
                  product-type="WALL_CALENDAR"
                  :disabled="paymentProcessing"
                  @payment-initiated="handlePaymentInitiated"
                  @payment-error="handlePaymentError"
                />
              </div>
            </template>
          </Card>
        </div>

        <!-- Order Summary Sidebar -->
        <div class="checkout-sidebar">
          <OrderSummary
            :calendar="{
              id: calendar.id,
              name: calendar.name,
              year: calendar.year,
              templateName: calendar.template?.name,
              previewSvg: calendar.previewSvg,
              quantity: quantity,
              unitPrice: unitPrice,
            }"
            :quantity="quantity"
            :subtotal="subtotal"
            :shipping="shipping"
            :tax="tax"
            :total="total"
            :show-discount-code="false"
          />
        </div>
      </div>

      <!-- Error State -->
      <div v-else class="error-state">
        <i class="pi pi-exclamation-triangle"></i>
        <h2>Calendar Not Found</h2>
        <p>The calendar you're trying to order could not be found.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.checkout-page {
  min-height: 100vh;
  background: #f9fafb;
  padding: 2rem 1rem;
}

.checkout-container {
  max-width: 1400px;
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

.loading-state p {
  color: #6b7280;
  font-size: 1.125rem;
}

.checkout-content {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 2rem;
  align-items: start;
}

.checkout-main {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.checkout-header {
  margin-bottom: 0.5rem;
}

.checkout-header h1 {
  font-size: 2rem;
  font-weight: 700;
  color: #111827;
  margin: 0 0 0.5rem 0;
}

.checkout-subtitle {
  color: #6b7280;
  font-size: 1rem;
  margin: 0;
}

.details-card,
.shipping-card,
.payment-card {
  background: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.order-details {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-row label {
  font-weight: 600;
  color: #374151;
}

.detail-row span {
  color: #1f2937;
}

.payment-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.payment-title i {
  color: #3b82f6;
}

.payment-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.payment-description {
  color: #6b7280;
  font-size: 0.875rem;
  line-height: 1.5;
  margin: 0;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 8px;
  border-left: 3px solid #3b82f6;
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  text-align: center;
  gap: 1rem;
}

.error-state i {
  font-size: 4rem;
  color: #ef4444;
}

.error-state h2 {
  font-size: 1.875rem;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.error-state p {
  color: #6b7280;
  font-size: 1.125rem;
  margin: 0;
}

/* Responsive */
@media (max-width: 1024px) {
  .checkout-content {
    grid-template-columns: 1fr;
  }

  .checkout-sidebar {
    order: -1;
  }
}

@media (max-width: 640px) {
  .checkout-page {
    padding: 1rem 0.5rem;
  }

  .checkout-header h1 {
    font-size: 1.5rem;
  }

  .checkout-subtitle {
    font-size: 0.875rem;
  }
}
</style>
