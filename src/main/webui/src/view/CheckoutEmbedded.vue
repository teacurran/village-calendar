<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { useCartStore } from "../stores/cart";
import { useToast } from "primevue/usetoast";
import { ROUTE_NAMES } from "../navigation/routes";
import { homeBreadcrumb } from "../navigation/breadcrumbs";
import { Breadcrumb, Button, Card } from "primevue";
import Divider from "primevue/divider";
import Dialog from "primevue/dialog";
import { loadStripe, Stripe } from "@stripe/stripe-js";

const router = useRouter();
const cartStore = useCartStore();
const toast = useToast();

// Store for calendar SVGs
const calendarSvgs = ref<Record<string, string>>({});
const showPreviewModal = ref(false);
const previewCalendarSvg = ref("");
const previewCalendarName = ref("");

// Stripe state
const stripe = ref<Stripe | null>(null);
const checkoutClientSecret = ref<string | null>(null);
const checkoutSessionId = ref<string | null>(null);
const orderId = ref<string | null>(null);
const orderNumber = ref<string | null>(null);
const checkoutContainer = ref<HTMLElement | null>(null);
const checkoutMounted = ref(false);

// Loading states
const pageLoading = ref(true);
const creatingSession = ref(false);
const checkoutError = ref<string | null>(null);

// Check if item is a calendar (by productCode or configuration)
const isCalendarItem = (item: any) => {
  // Check productCode
  if (item.productCode === "print" || item.productCode === "pdf") {
    return true;
  }
  // Check configuration for calendar data
  if (item.configuration) {
    try {
      const config =
        typeof item.configuration === "string"
          ? JSON.parse(item.configuration)
          : item.configuration;
      return config.svgContent || config.year || config.productCode;
    } catch (e) {}
  }
  return false;
};

// Check if item is digital (PDF)
const isDigitalItem = (item: any) => {
  if (item.productCode === "pdf") return true;
  if (item.configuration) {
    try {
      const config =
        typeof item.configuration === "string"
          ? JSON.parse(item.configuration)
          : item.configuration;
      if (config.productType === "pdf" || config.productCode === "pdf") {
        return true;
      }
    } catch (e) {}
  }
  return false;
};

// Get calendar config from item
const getCalendarConfig = (item: any) => {
  if (item.configuration) {
    try {
      const config =
        typeof item.configuration === "string"
          ? JSON.parse(item.configuration)
          : item.configuration;
      return config;
    } catch (e) {
      console.error("Failed to parse calendar configuration:", e);
    }
  }
  return null;
};

// Computed
const cartItems = computed(() => cartStore.items);
const cartSubtotal = computed(() => cartStore.subtotal || 0);

// Breadcrumbs
const breadcrumbItems = computed(() => [
  { label: "Cart", url: "/cart" },
  { label: "Checkout" },
]);

// Load calendar SVGs
const loadCalendarSvgs = async () => {
  if (!cartStore.items || cartStore.items.length === 0) return;

  for (const item of cartStore.items) {
    const config = getCalendarConfig(item);
    if (config) {
      const calendarKey = item.id;
      if (config.svgContent) {
        calendarSvgs.value[calendarKey] = config.svgContent;
      } else if (config.generatedSvg) {
        calendarSvgs.value[calendarKey] = config.generatedSvg;
      }
    }
  }
};

// Show calendar preview
const showCalendarPreview = (item: any) => {
  const config = getCalendarConfig(item);
  if (config) {
    const calendarKey = item.id;
    if (calendarSvgs.value[calendarKey]) {
      previewCalendarSvg.value = calendarSvgs.value[calendarKey];
      previewCalendarName.value = config.name || `${config.year} Calendar`;
      showPreviewModal.value = true;
    }
  }
};

// Create checkout session and initialize embedded checkout
async function initializeEmbeddedCheckout() {
  creatingSession.value = true;
  checkoutError.value = null;

  try {
    // Fetch Stripe config
    const configResponse = await fetch("/api/payment/config");
    if (!configResponse.ok) {
      throw new Error("Failed to fetch payment configuration");
    }
    const config = await configResponse.json();

    // Load Stripe
    stripe.value = await loadStripe(config.publishableKey);
    if (!stripe.value) {
      throw new Error("Failed to load Stripe");
    }

    // Prepare items for checkout session
    const items = cartStore.items.map((item: any) => ({
      name: item.templateName || `Calendar ${item.year}`,
      description: `${item.year} Calendar`,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      year: item.year,
      productCode: item.productCode || "print",
      templateId: item.templateId,
      configuration: item.configuration,
    }));

    // Create checkout session via GraphQL
    const response = await fetch("/graphql", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        query: `
          mutation CreateCheckoutSession($input: CheckoutSessionInput!) {
            createCheckoutSession(input: $input) {
              clientSecret
              sessionId
              orderId
              orderNumber
            }
          }
        `,
        variables: {
          input: {
            items,
            returnUrl: window.location.origin,
          },
        },
      }),
    });

    const result = await response.json();
    if (result.errors) {
      throw new Error(result.errors[0].message);
    }

    const sessionData = result.data.createCheckoutSession;
    checkoutClientSecret.value = sessionData.clientSecret;
    checkoutSessionId.value = sessionData.sessionId;
    orderId.value = sessionData.orderId;
    orderNumber.value = sessionData.orderNumber;

    // Mount embedded checkout
    if (checkoutContainer.value && stripe.value && checkoutClientSecret.value) {
      const checkout = await stripe.value.initEmbeddedCheckout({
        clientSecret: checkoutClientSecret.value,
      });

      checkout.mount(checkoutContainer.value);
      checkoutMounted.value = true;
    }
  } catch (error: any) {
    console.error("Error initializing embedded checkout:", error);
    checkoutError.value = error.message || "Failed to initialize checkout";
    toast.add({
      severity: "error",
      summary: "Checkout Error",
      detail: error.message || "Failed to initialize checkout",
      life: 5000,
    });
  } finally {
    creatingSession.value = false;
  }
}

// Handle checkout completion (called when user returns after redirect)
async function handleCheckoutCompletion() {
  const urlParams = new URLSearchParams(window.location.search);
  const sessionId = urlParams.get("session_id");

  if (sessionId) {
    // Payment completed, redirect to confirmation
    // Clear cart
    await cartStore.clearCart();

    // Get order number from session storage or URL
    const storedOrderNumber = sessionStorage.getItem("pendingOrderNumber");

    router.push({
      name: ROUTE_NAMES.ORDER_CONFIRMATION,
      params: { orderId: storedOrderNumber || sessionId },
    });
  }
}

// Format currency
function formatCurrency(amount: number) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(amount);
}

// Initialize
onMounted(async () => {
  try {
    // Check for return from Stripe
    await handleCheckoutCompletion();

    // Fetch cart
    await cartStore.fetchCart();
    await loadCalendarSvgs();

    if (cartStore.isEmpty) {
      router.push({ name: "templates" });
      return;
    }

    // Initialize embedded checkout
    await initializeEmbeddedCheckout();
  } finally {
    pageLoading.value = false;
  }
});

// Watch cart changes
watch(
  cartItems,
  async () => {
    await loadCalendarSvgs();
  },
  { immediate: false }
);
</script>

<template>
  <Breadcrumb :home="homeBreadcrumb" :model="breadcrumbItems" class="mb-4" />

  <!-- Loading state -->
  <div v-if="pageLoading" class="loading-container">
    <i class="pi pi-spin pi-spinner text-4xl"></i>
    <p class="mt-3">Loading checkout...</p>
  </div>

  <!-- Main checkout grid -->
  <div v-else class="checkout-grid">
    <!-- Left: Stripe Embedded Checkout -->
    <Card class="checkout-form">
      <template #content>
        <!-- Creating session spinner -->
        <div v-if="creatingSession" class="checkout-loading">
          <i class="pi pi-spin pi-spinner text-3xl"></i>
          <p class="mt-2">Preparing secure checkout...</p>
        </div>

        <!-- Error state -->
        <div v-else-if="checkoutError" class="checkout-error">
          <i class="pi pi-exclamation-triangle text-3xl text-orange-500"></i>
          <p class="mt-2">{{ checkoutError }}</p>
          <Button
            label="Try Again"
            icon="pi pi-refresh"
            class="mt-3"
            @click="initializeEmbeddedCheckout"
          />
        </div>

        <!-- Embedded checkout container -->
        <div
          v-show="!creatingSession && !checkoutError"
          ref="checkoutContainer"
          class="stripe-embedded-checkout"
        ></div>
      </template>
    </Card>

    <!-- Right: Order summary -->
    <Card class="checkout-sidebar">
      <template #title>Order summary</template>

      <template #content>
        <!-- Cart items -->
        <div class="cart-items">
          <div v-for="item in cartItems" :key="item.id" class="cart-item">
            <div class="item-image">
              <div
                v-if="isCalendarItem(item) && getCalendarConfig(item)"
                class="image-placeholder calendar-preview"
                @click="showCalendarPreview(item)"
              >
                <div class="preview-container">
                  <div
                    v-if="calendarSvgs[item.id]"
                    class="svg-preview"
                    v-html="calendarSvgs[item.id]"
                  ></div>
                  <div v-else class="calendar-icon">
                    <div class="calendar-year">
                      {{ getCalendarConfig(item)?.year }}
                    </div>
                    <i class="pi pi-calendar"></i>
                  </div>
                </div>
                <span class="item-quantity">{{ item.quantity }}</span>
              </div>
              <div v-else class="image-placeholder">
                <span class="item-quantity">{{ item.quantity }}</span>
              </div>
            </div>
            <div class="item-details">
              <div class="item-name">
                {{ item.templateName }}
                <span
                  v-if="isDigitalItem(item)"
                  class="product-badge pdf-badge"
                >
                  PDF
                </span>
                <span v-else class="product-badge print-badge">Print</span>
              </div>
              <div
                v-if="isCalendarItem(item) && getCalendarConfig(item)"
                class="item-variant"
              >
                <i class="pi pi-calendar mr-1"></i>
                {{ getCalendarConfig(item).year }} Calendar
              </div>
            </div>
            <div class="item-price">{{ formatCurrency(item.lineTotal) }}</div>
          </div>
        </div>

        <Divider />

        <!-- Subtotal -->
        <div class="summary-totals">
          <div class="total-row">
            <span>Subtotal</span>
            <span>{{ formatCurrency(cartSubtotal) }}</span>
          </div>
          <div class="total-row hint">
            <span>Shipping & taxes</span>
            <span>Calculated at checkout</span>
          </div>
        </div>
      </template>
    </Card>
  </div>

  <!-- Calendar Preview Modal -->
  <Dialog
    v-model:visible="showPreviewModal"
    :header="previewCalendarName"
    :style="{ width: '90vw', maxWidth: '900px' }"
    :modal="true"
    :dismissable-mask="true"
  >
    <div
      v-if="previewCalendarSvg"
      class="calendar-preview-content"
      v-html="previewCalendarSvg"
    />
  </Dialog>
</template>

<style scoped>
.loading-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
  color: var(--text-color-secondary);
}

.checkout-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 1.5rem;
  margin: 0 auto 3rem;
  padding: 0 1rem;
  max-width: 1200px;
  align-items: start;
}

.checkout-form {
  min-width: 0;
}

.checkout-sidebar {
  position: sticky;
  top: 2rem;
  height: fit-content;
}

.checkout-loading,
.checkout-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  text-align: center;
  color: var(--text-color-secondary);
}

.stripe-embedded-checkout {
  min-height: 400px;
}

/* Cart items */
.cart-items {
  margin-bottom: 1rem;
}

.cart-item {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
}

.item-image {
  position: relative;
  padding: 8px 8px 0 0;
}

.image-placeholder {
  width: 64px;
  height: 64px;
  background: #f5f5f5;
  border: 1px solid #e1e1e1;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
}

.image-placeholder.calendar-preview {
  background: var(--p-surface-0);
  border-color: #90caf9;
}

.image-placeholder.calendar-preview:hover {
  border-color: #1976d2;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.preview-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: 4px;
}

.svg-preview {
  width: 800px;
  height: 800px;
  transform: scale(0.08);
  transform-origin: top left;
  pointer-events: none;
}

.calendar-icon {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #1565c0;
  width: 100%;
  height: 100%;
}

.calendar-year {
  font-size: 1.2rem;
  font-weight: bold;
  line-height: 1;
  margin-bottom: 2px;
}

.item-quantity {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #737373;
  color: white;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
}

.item-details {
  flex: 1;
}

.item-name {
  font-weight: 500;
  color: #333;
  margin-bottom: 0.25rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.product-badge {
  font-size: 0.7rem;
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
  font-weight: 600;
  text-transform: uppercase;
}

.print-badge {
  background: #e3f2fd;
  color: #1565c0;
}

.pdf-badge {
  background: #fce4ec;
  color: #c62828;
}

.item-variant {
  font-size: 0.875rem;
  color: #737373;
}

.item-price {
  font-weight: 500;
  color: #333;
}

/* Totals */
.summary-totals {
  margin-top: 1rem;
}

.total-row {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0;
  font-size: 0.875rem;
}

.total-row.hint {
  color: var(--text-color-secondary);
  font-size: 0.8rem;
}

/* Calendar preview modal */
.calendar-preview-content {
  background: white;
  padding: 1rem;
  border-radius: 0.5rem;
}

.calendar-preview-content :deep(svg) {
  width: 100%;
  height: auto;
  max-height: 70vh;
}

/* Responsive */
@media (max-width: 968px) {
  .checkout-grid {
    grid-template-columns: 1fr;
  }

  .checkout-sidebar {
    position: static;
    order: -1;
  }
}
</style>
