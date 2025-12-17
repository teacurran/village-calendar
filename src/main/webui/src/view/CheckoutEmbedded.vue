<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from "vue";
import { useRouter } from "vue-router";
import {
  useCartStore,
  type CartItem,
  type CalendarConfiguration,
} from "../stores/cart";
import { useToast } from "primevue/usetoast";
import { ROUTE_NAMES } from "../navigation/routes";
import { homeBreadcrumb } from "../navigation/breadcrumbs";
import { Breadcrumb, Button, Card } from "primevue";
import Dialog from "primevue/dialog";
import { loadStripe, Stripe } from "@stripe/stripe-js";

const router = useRouter();
const cartStore = useCartStore();
const toast = useToast();

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

// Calendar preview state
const calendarSvgs = ref<Record<string, string>>({});
const showPreviewModal = ref(false);
const previewCalendarSvg = ref("");
const previewCalendarName = ref("");

// Breadcrumbs
const breadcrumbItems = computed(() => [
  { label: "Cart", url: "/cart" },
  { label: "Checkout" },
]);

// Cart items
const cartItems = computed(() => cartStore.items);

// Check if any items are calendars
const hasCalendarItems = computed(() => {
  return cartItems.value.some((item: CartItem) => {
    if (item.productCode === "print" || item.productCode === "pdf") return true;
    if (item.configuration) {
      try {
        const config: CalendarConfiguration =
          typeof item.configuration === "string"
            ? JSON.parse(item.configuration)
            : item.configuration;
        return !!(config.svgContent || config.year);
      } catch {
        return false;
      }
    }
    return false;
  });
});

// Get calendar config from item
const getCalendarConfig = (item: CartItem): CalendarConfiguration | null => {
  if (item.configuration) {
    try {
      const config: CalendarConfiguration =
        typeof item.configuration === "string"
          ? JSON.parse(item.configuration)
          : item.configuration;
      return config;
    } catch {
      return null;
    }
  }
  return null;
};

// Load calendar SVGs from cart items
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

// Show calendar preview modal
const showCalendarPreview = (item: CartItem) => {
  const config = getCalendarConfig(item);
  if (config && calendarSvgs.value[item.id]) {
    previewCalendarSvg.value = calendarSvgs.value[item.id];
    previewCalendarName.value = config.name || `${config.year} Calendar`;
    showPreviewModal.value = true;
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
    const items = cartStore.items.map((item: CartItem) => ({
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
      console.error("GraphQL errors:", JSON.stringify(result.errors, null, 2));
      throw new Error(result.errors[0].message);
    }

    const sessionData = result.data.createCheckoutSession;
    console.log("Checkout session created:", sessionData);
    checkoutClientSecret.value = sessionData.clientSecret;
    checkoutSessionId.value = sessionData.sessionId;
    orderId.value = sessionData.orderId;
    orderNumber.value = sessionData.orderNumber;

    // Session created, stop showing spinner so container becomes visible
    creatingSession.value = false;

    // Wait for DOM to be ready before mounting
    await nextTick();

    // Mount embedded checkout
    if (checkoutContainer.value && stripe.value && checkoutClientSecret.value) {
      const checkout = await stripe.value.initEmbeddedCheckout({
        clientSecret: checkoutClientSecret.value,
      });

      checkout.mount(checkoutContainer.value);
      checkoutMounted.value = true;
    } else {
      console.error("Missing required elements for checkout mount");
    }
  } catch (error: unknown) {
    console.error("Error initializing embedded checkout:", error);
    const errorMessage =
      error instanceof Error ? error.message : "Failed to initialize checkout";
    checkoutError.value = errorMessage;
    toast.add({
      severity: "error",
      summary: "Checkout Error",
      detail: errorMessage,
      life: 5000,
    });
    creatingSession.value = false;
  }
}

// Handle checkout completion (called when user returns after redirect)
async function handleCheckoutCompletion() {
  const urlParams = new URLSearchParams(window.location.search);
  const sessionId = urlParams.get("session_id");

  if (sessionId) {
    await cartStore.clearCart();
    const storedOrderNumber = sessionStorage.getItem("pendingOrderNumber");
    router.push({
      name: ROUTE_NAMES.ORDER_CONFIRMATION,
      params: { orderId: storedOrderNumber || sessionId },
    });
  }
}

// Initialize
onMounted(async () => {
  try {
    await handleCheckoutCompletion();
    // Force fresh fetch to avoid race condition with appInit's cached empty cart
    await cartStore.fetchCart(true);
    await loadCalendarSvgs();

    if (cartStore.isEmpty) {
      router.push({ name: "templates" });
      return;
    }

    pageLoading.value = false;
    await initializeEmbeddedCheckout();
  } catch (error) {
    console.error("Error during checkout initialization:", error);
    pageLoading.value = false;
  }
});

// Watch for cart changes to reload SVGs
watch(
  cartItems,
  async () => {
    await loadCalendarSvgs();
  },
  { immediate: false },
);
</script>

<template>
  <Breadcrumb :home="homeBreadcrumb" :model="breadcrumbItems" class="mb-4" />

  <!-- Loading state -->
  <div v-if="pageLoading" class="loading-container">
    <i class="pi pi-spin pi-spinner text-4xl"></i>
    <p class="mt-3">Loading checkout...</p>
  </div>

  <!-- Main checkout layout -->
  <div
    v-else
    :class="['checkout-layout', { 'with-sidebar': hasCalendarItems }]"
  >
    <!-- Stripe Checkout (main area) -->
    <Card class="checkout-card">
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

    <!-- Calendar Thumbnails Sidebar (only if there are calendars) -->
    <div v-if="hasCalendarItems" class="preview-sidebar">
      <h3 class="sidebar-title">Your Calendars</h3>
      <div class="thumbnail-list">
        <div
          v-for="item in cartItems"
          :key="item.id"
          class="thumbnail-item"
          @click="showCalendarPreview(item)"
        >
          <div v-if="calendarSvgs[item.id]" class="thumbnail-container">
            <div class="thumbnail-svg" v-html="calendarSvgs[item.id]"></div>
          </div>
          <div v-else class="thumbnail-placeholder">
            <i class="pi pi-calendar"></i>
          </div>
          <span class="thumbnail-label">{{
            getCalendarConfig(item)?.year || item.year
          }}</span>
        </div>
      </div>
      <p class="sidebar-hint">Click to preview</p>
    </div>
  </div>

  <!-- Calendar Preview Modal -->
  <Dialog
    v-model:visible="showPreviewModal"
    :header="previewCalendarName"
    :style="{ width: '90vw', maxWidth: '1000px' }"
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

.checkout-layout {
  display: block;
  width: fit-content;
  margin: 0 0 3rem 2rem;
  padding: 0;
  box-sizing: border-box;
}

.checkout-layout.with-sidebar {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2rem;
  width: 100%;
  max-width: 1200px;
  align-items: start;
}

.checkout-card {
  width: fit-content;
}

.checkout-card :deep(.p-card-body) {
  padding: 0.75rem;
}

.checkout-card :deep(.p-card-content) {
  padding: 0;
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
  min-height: 500px;
}

/* Preview Sidebar - Large calendar preview */
.preview-sidebar {
  position: sticky;
  top: 1rem;
  text-align: center;
}

.sidebar-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-color-secondary);
  margin: 0 0 1rem 0;
  text-align: left;
}

.thumbnail-list {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.thumbnail-item {
  cursor: pointer;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.thumbnail-item:hover {
  transform: scale(1.01);
}

.thumbnail-container {
  width: 100%;
  aspect-ratio: 35 / 23;
  background: white;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.thumbnail-container:hover {
  border-color: #1976d2;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

.thumbnail-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.thumbnail-svg :deep(svg) {
  width: 100%;
  height: 100%;
}

.thumbnail-placeholder {
  width: 100%;
  aspect-ratio: 35 / 23;
  background: #f5f5f5;
  border: 2px solid #e1e1e1;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 3rem;
}

.thumbnail-label {
  display: block;
  font-size: 0.875rem;
  color: var(--text-color);
  margin-top: 0.5rem;
  font-weight: 500;
  text-align: left;
}

.sidebar-hint {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  margin: 1rem 0 0 0;
  text-align: left;
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
  max-height: 75vh;
}

/* Responsive - hide sidebar on small screens */
@media (max-width: 600px) {
  .checkout-layout.with-sidebar {
    display: block;
  }

  .preview-sidebar {
    display: none;
  }
}
</style>
