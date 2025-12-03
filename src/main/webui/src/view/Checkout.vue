<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from "vue";
import { useRouter } from "vue-router";
import { useCartStore } from "../stores/cart";
import { useUserStore } from "../stores/user";
import { useToast } from "primevue/usetoast";
import { ROUTE_NAMES } from "../navigation/routes";
import { homeBreadcrumb } from "../navigation/breadcrumbs";
import { Breadcrumb, Button, Card } from "primevue";
import InputText from "primevue/inputtext";
import InputMask from "primevue/inputmask";
import Dropdown from "primevue/dropdown";
import Checkbox from "primevue/checkbox";
import RadioButton from "primevue/radiobutton";
import Divider from "primevue/divider";
import Dialog from "primevue/dialog";
import {
  loadStripe,
  Stripe,
  StripeElements,
  StripeCardElement,
} from "@stripe/stripe-js";

// const { t } = useI18n({ useScope: 'global' })
const router = useRouter();
const cartStore = useCartStore();
const userStore = useUserStore();
const toast = useToast();

// Calendar product IDs
const CALENDAR_PRINT_PRODUCT_ID = "ca1e0da2-0000-0000-0000-000000000001";
const CALENDAR_PDF_PRODUCT_ID = "ca1e0da2-0000-0000-0000-000000000002";

// Store for calendar SVGs
const calendarSvgs = ref<Record<string, string>>({});
const showPreviewModal = ref(false);
const previewCalendarSvg = ref("");
const previewCalendarName = ref("");

// Check if item is a calendar (print or PDF)
const isCalendarItem = (item: any) => {
  return (
    item.templateId === CALENDAR_PRINT_PRODUCT_ID ||
    item.templateId === CALENDAR_PDF_PRODUCT_ID
  );
};

// Parse configuration and get calendar details
const getCalendarConfig = (item: any) => {
  if (isCalendarItem(item) && item.configuration) {
    try {
      return JSON.parse(item.configuration);
    } catch (e) {
      console.error("Failed to parse calendar configuration:", e);
    }
  }
  return null;
};

// Get month name from number
const getMonthName = (month: number) => {
  const months = [
    "Jan",
    "Feb",
    "Mar",
    "Apr",
    "May",
    "Jun",
    "Jul",
    "Aug",
    "Sep",
    "Oct",
    "Nov",
    "Dec",
  ];
  return months[month - 1] || "Jan";
};

// Get calendar SVG for a specific calendar ID
const fetchCalendarSvg = async (calendarId: string) => {
  if (calendarSvgs.value[calendarId]) {
    return calendarSvgs.value[calendarId];
  }

  try {
    // Fetch only the specific calendar's SVG
    const response = await fetch(
      `/api/calendar-templates/user/calendars/${calendarId}/preview`,
    );
    if (response.ok) {
      const svg = await response.text();
      calendarSvgs.value[calendarId] = svg;
      return svg;
    }
  } catch (error) {
    console.error(`Failed to fetch calendar SVG for ${calendarId}:`, error);
  }

  return null;
};

// Load calendar SVGs when cart items change
const loadCalendarSvgs = async () => {
  if (!cartStore.items || cartStore.items.length === 0) return;

  // Process each cart item
  for (const item of cartStore.items) {
    const config = getCalendarConfig(item);
    if (config && config.calendarId) {
      // This will now use the cached calendars
      await fetchCalendarSvg(config.calendarId);
    }
  }
};

// Show calendar preview
const showCalendarPreview = (item: any) => {
  const config = getCalendarConfig(item);
  if (config && config.calendarId && calendarSvgs.value[config.calendarId]) {
    previewCalendarSvg.value = calendarSvgs.value[config.calendarId];
    previewCalendarName.value = config.name || `${config.year} Calendar`;
    showPreviewModal.value = true;
  }
};

// Current step (1: Information, 2: Shipping, 3: Payment)
const currentStep = ref(1);

// Account section

// Contact & Shipping address
const contactAndShipping = ref({
  email: "",
  newsletter: false,
  firstName: "",
  lastName: "",
  company: "",
  address1: "",
  address2: "",
  city: "",
  state: "",
  postalCode: "",
  country: "US",
  phone: "",
  saveInfo: false,
});

// Billing address
const sameAsShipping = ref(true);
const billingAddress = ref({
  firstName: "",
  lastName: "",
  company: "",
  address1: "",
  address2: "",
  city: "",
  state: "",
  postalCode: "",
  country: "US",
});

// Shipping options
interface ShippingMethod {
  id: string;
  name: string;
  description: string;
  price: number;
}

const selectedShippingMethod = ref<ShippingMethod | null>(null);
const shippingMethods = ref<ShippingMethod[]>([]);
const loadingShipping = ref(false);

// Payment - Stripe
const stripe = ref<Stripe | null>(null);
const stripeElements = ref<StripeElements | null>(null);
const stripeCardElement = ref<StripeCardElement | null>(null);
const stripeElementsContainer = ref<HTMLElement | null>(null);
const paymentIntentClientSecret = ref<string | null>(null);
const stripePublishableKey = ref<string | null>(null);


// Countries list
const countries = [
  { code: "US", name: "United States" },
  { code: "CA", name: "Canada" },
  { code: "MX", name: "Mexico" },
];

// US States
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

// Canadian Provinces
const canadianProvinces = [
  { code: "AB", name: "Alberta" },
  { code: "BC", name: "British Columbia" },
  { code: "MB", name: "Manitoba" },
  { code: "NB", name: "New Brunswick" },
  { code: "NL", name: "Newfoundland and Labrador" },
  { code: "NT", name: "Northwest Territories" },
  { code: "NS", name: "Nova Scotia" },
  { code: "NU", name: "Nunavut" },
  { code: "ON", name: "Ontario" },
  { code: "PE", name: "Prince Edward Island" },
  { code: "QC", name: "Quebec" },
  { code: "SK", name: "Saskatchewan" },
  { code: "YT", name: "Yukon" },
];

// Mexican States
const mexicanStates = [
  { code: "AGU", name: "Aguascalientes" },
  { code: "BCN", name: "Baja California" },
  { code: "BCS", name: "Baja California Sur" },
  { code: "CAM", name: "Campeche" },
  { code: "CHP", name: "Chiapas" },
  { code: "CHH", name: "Chihuahua" },
  { code: "CMX", name: "Ciudad de México" },
  { code: "COA", name: "Coahuila" },
  { code: "COL", name: "Colima" },
  { code: "DUR", name: "Durango" },
  { code: "GUA", name: "Guanajuato" },
  { code: "GRO", name: "Guerrero" },
  { code: "HID", name: "Hidalgo" },
  { code: "JAL", name: "Jalisco" },
  { code: "MEX", name: "Estado de México" },
  { code: "MIC", name: "Michoacán" },
  { code: "MOR", name: "Morelos" },
  { code: "NAY", name: "Nayarit" },
  { code: "NLE", name: "Nuevo León" },
  { code: "OAX", name: "Oaxaca" },
  { code: "PUE", name: "Puebla" },
  { code: "QUE", name: "Querétaro" },
  { code: "ROO", name: "Quintana Roo" },
  { code: "SLP", name: "San Luis Potosí" },
  { code: "SIN", name: "Sinaloa" },
  { code: "SON", name: "Sonora" },
  { code: "TAB", name: "Tabasco" },
  { code: "TAM", name: "Tamaulipas" },
  { code: "TLA", name: "Tlaxcala" },
  { code: "VER", name: "Veracruz" },
  { code: "YUC", name: "Yucatán" },
  { code: "ZAC", name: "Zacatecas" },
];

// Computed property for states/provinces based on country
const states = computed(() => {
  switch (contactAndShipping.value.country) {
    case "CA":
      return canadianProvinces;
    case "MX":
      return mexicanStates;
    default:
      return usStates;
  }
});

// Processing state
const processing = ref(false);
const pageLoading = ref(true);
const validationErrors = ref<Record<string, string>>({});

// Computed properties
const isLoggedIn = computed(() => userStore.isLoggedIn);
const cartItems = computed(() => cartStore.items);
const cartSubtotal = computed(() => cartStore.subtotal || 0);

// Watch for cart changes to load calendar SVGs
watch(
  cartItems,
  async () => {
    await loadCalendarSvgs();
  },
  { immediate: false },
);
const shippingCost = computed(
  () => (selectedShippingMethod.value as ShippingMethod | null)?.price || 0,
);
const taxAmount = computed(() => {
  const taxRate = contactAndShipping.value.state === "MA" ? 0.0625 : 0;
  return (cartSubtotal.value + shippingCost.value) * taxRate;
});
const orderTotal = computed(
  () => cartSubtotal.value + shippingCost.value + taxAmount.value,
);

// Breadcrumbs
const breadcrumbItems = computed(() => {
  const items = [
    { label: "Store", url: "/store" },
    { label: "Cart", url: "/store/cart" },
  ];

  if (currentStep.value === 1) {
    items.push({ label: "Information" });
  } else if (currentStep.value === 2) {
    items.push({ label: "Information", url: "#", command: () => goToStep(1) });
    items.push({ label: "Shipping" });
  } else if (currentStep.value === 3) {
    items.push({ label: "Information", url: "#", command: () => goToStep(1) });
    items.push({ label: "Shipping", url: "#", command: () => goToStep(2) });
    items.push({ label: "Payment" });
  }

  return items;
});

// Initialize
onMounted(async () => {
  try {
    await cartStore.fetchCart();
    await loadCalendarSvgs();

    if (cartStore.isEmpty) {
      router.push({ name: ROUTE_NAMES.STORE_PRODUCTS });
      return;
    }

    // Pre-select first shipping method
    if (shippingMethods.value.length > 0) {
      selectedShippingMethod.value = shippingMethods.value[0] as ShippingMethod;
    }
  } finally {
    pageLoading.value = false;
  }
});

// Fetch shipping options from backend
async function fetchShippingOptions() {
  if (!contactAndShipping.value.country || !contactAndShipping.value.state) {
    return;
  }

  loadingShipping.value = true;
  try {
    // Check if cart has calendars
    const hasCalendars = cartStore.items?.some((item) => isCalendarItem(item));

    if (hasCalendars) {
      // Use calendar-specific shipping endpoint
      const response = await fetch("/api/shipping/calculate-calendar", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          country: contactAndShipping.value.country,
          state: contactAndShipping.value.state,
          postalCode: contactAndShipping.value.postalCode,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        shippingMethods.value = data.options.map((opt: any) => ({
          id: opt.id,
          name: opt.name,
          description: opt.description,
          price: opt.price,
        }));

        // Pre-select first option
        if (shippingMethods.value.length > 0) {
          selectedShippingMethod.value = shippingMethods.value[0];
        }
      } else if (response.status === 400) {
        const error = await response.text();
        toast.add({
          severity: "error",
          summary: "Shipping Not Available",
          detail: error,
          life: 5000,
        });
      }
    } else {
      // Use regular shipping calculation for other products
      // For now, use hardcoded standard shipping
      shippingMethods.value = [
        {
          id: "standard",
          name: "Standard Shipping",
          description: "5-7 business days",
          price: 5.99,
        },
      ];
      selectedShippingMethod.value = shippingMethods.value[0];
    }
  } catch (error) {
    console.error("Error fetching shipping options:", error);
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Failed to load shipping options",
      life: 3000,
    });
  } finally {
    loadingShipping.value = false;
  }
}

// Navigation
function goToStep(step: number) {
  if (step < currentStep.value) {
    currentStep.value = step;
  } else if (step === currentStep.value + 1) {
    if (validateCurrentStep()) {
      currentStep.value = step;
    }
  }
}

async function continueToShipping() {
  if (validateInformation()) {
    await fetchShippingOptions();
    currentStep.value = 2;
  }
}

function continueToPayment() {
  if (validateShipping()) {
    currentStep.value = 3;
    // Initialize Stripe when entering payment step
    setTimeout(() => initializeStripe(), 100);
  }
}

// Validation
function validateCurrentStep() {
  switch (currentStep.value) {
    case 1:
      return validateInformation();
    case 2:
      return validateShipping();
    case 3:
      return validatePayment();
    default:
      return true;
  }
}

function validateInformation() {
  const errors: Record<string, string> = {};
  if (!contactAndShipping.value.email) {
    errors.email = "Email is required";
  }
  if (!contactAndShipping.value.firstName) {
    errors.firstName = "First name is required";
  }
  if (!contactAndShipping.value.lastName) {
    errors.lastName = "Last name is required";
  }
  if (!contactAndShipping.value.address1) {
    errors.address1 = "Address is required";
  }
  if (!contactAndShipping.value.city) {
    errors.city = "City is required";
  }
  if (!contactAndShipping.value.state) {
    errors.state = "State is required";
  }
  if (!contactAndShipping.value.postalCode) {
    errors.postalCode = "ZIP code is required";
  }
  if (!contactAndShipping.value.phone) {
    errors.phone = "Phone is required for delivery";
  }

  validationErrors.value = errors;
  return Object.keys(errors).length === 0;
}

function validateShipping() {
  if (!selectedShippingMethod.value) {
    toast.add({
      severity: "error",
      summary: "Shipping Required",
      detail: "Please select a shipping method",
      life: 3000,
    });
    return false;
  }
  return true;
}

function validatePayment() {
  // Stripe will handle validation
  return true;
}

// Initialize Stripe
async function initializeStripe() {
  try {
    // Fetch Stripe configuration from backend
    const configResponse = await fetch("/api/payment/config");
    if (!configResponse.ok) {
      throw new Error("Failed to fetch payment configuration");
    }
    const config = await configResponse.json();

    // Store the publishable key
    stripePublishableKey.value = config.publishableKey;

    // Load Stripe.js
    stripe.value = await loadStripe(config.publishableKey);
    if (!stripe.value) {
      throw new Error("Failed to load Stripe");
    }

    // Create Elements instance
    stripeElements.value = stripe.value.elements();

    // Create a Payment Intent
    const intentResponse = await fetch("/api/payment/create-payment-intent", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        amount: orderTotal.value,
        currency: "usd",
      }),
    });

    if (!intentResponse.ok) {
      throw new Error("Failed to create payment intent");
    }

    const intentData = await intentResponse.json();
    paymentIntentClientSecret.value = intentData.clientSecret;

    // Create and mount the Card Element
    await nextTick();
    if (stripeElementsContainer.value && stripeElements.value) {
      const cardElementOptions = {
        style: {
          base: {
            fontSize: "16px",
            color: "#374151",
            "::placeholder": {
              color: "#9CA3AF",
            },
          },
          invalid: {
            color: "#EF4444",
            iconColor: "#EF4444",
          },
        },
      };

      stripeCardElement.value = stripeElements.value.create(
        "card",
        cardElementOptions,
      );
      stripeCardElement.value.mount(stripeElementsContainer.value);

      // Add event listener for errors
      stripeCardElement.value.on("change", (event) => {
        const displayError = document.getElementById("card-errors");
        if (displayError) {
          if (event.error) {
            displayError.textContent = event.error.message;
          } else {
            displayError.textContent = "";
          }
        }
      });
    }
  } catch (error) {
    console.error("Error initializing Stripe:", error);
    toast.add({
      severity: "error",
      summary: "Payment Error",
      detail: "Failed to initialize payment system",
      life: 3000,
    });
  }
}

// Process order
async function submitOrder() {
  if (!validatePayment()) {
    return;
  }

  processing.value = true;

  try {
    // Check if Stripe is initialized
    if (
      !stripe.value ||
      !stripeCardElement.value ||
      !paymentIntentClientSecret.value
    ) {
      throw new Error("Payment system not initialized");
    }

    // Confirm the payment with Stripe
    const { error, paymentIntent } = await stripe.value.confirmCardPayment(
      paymentIntentClientSecret.value,
      {
        payment_method: {
          card: stripeCardElement.value,
          billing_details: {
            name: `${contactAndShipping.value.firstName} ${contactAndShipping.value.lastName}`,
            email: contactAndShipping.value.email,
            phone: contactAndShipping.value.phone,
            address: {
              line1: contactAndShipping.value.address1,
              line2: contactAndShipping.value.address2,
              city: contactAndShipping.value.city,
              state: contactAndShipping.value.state,
              postal_code: contactAndShipping.value.postalCode,
              country: contactAndShipping.value.country,
            },
          },
        },
      },
    );

    if (error) {
      // Show error to customer
      toast.add({
        severity: "error",
        summary: "Payment Failed",
        detail: error.message,
        life: 5000,
      });
      return;
    }

    // Payment succeeded, create order in backend
    const orderResponse = await fetch("/api/payment/confirm-payment", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        paymentIntentId: paymentIntent.id,
        orderDetails: {
          email: contactAndShipping.value.email,
          shippingAddress: contactAndShipping.value,
          billingAddress: sameAsShipping.value
            ? contactAndShipping.value
            : billingAddress.value,
          shippingMethod: selectedShippingMethod.value,
          items: cartItems.value,
          subtotal: cartSubtotal.value,
          shippingCost: shippingCost.value,
          taxAmount: taxAmount.value,
          totalAmount: orderTotal.value,
        },
      }),
    });

    if (!orderResponse.ok) {
      throw new Error("Failed to create order");
    }

    const orderData = await orderResponse.json();

    // Store order info for confirmation page
    sessionStorage.setItem(
      "lastOrder",
      JSON.stringify({
        orderNumber: orderData.orderNumber || "VC-" + Date.now(),
        email: contactAndShipping.value.email,
        shippingAddress: contactAndShipping.value,
        billingAddress: sameAsShipping.value
          ? contactAndShipping.value
          : billingAddress.value,
        shippingMethod: selectedShippingMethod.value,
        items: cartItems.value,
        subtotal: cartSubtotal.value,
        shippingCost: shippingCost.value,
        taxAmount: taxAmount.value,
        totalAmount: orderTotal.value,
      }),
    );

    // Clear cart and redirect to confirmation
    await cartStore.clearCart();
    router.push({ name: ROUTE_NAMES.ORDER_CONFIRMATION });
  } catch (error: any) {
    console.error("Order submission error:", error);
    toast.add({
      severity: "error",
      summary: "Order Failed",
      detail:
        error.message || "Failed to process your order. Please try again.",
      life: 5000,
    });
  } finally {
    processing.value = false;
  }
}

// Format currency
function formatCurrency(amount: number) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(amount);
}

// Watch for billing address changes
watch(sameAsShipping, (newVal) => {
  if (newVal) {
    billingAddress.value = { ...contactAndShipping.value };
  }
});

// Clear state when country changes
watch(
  () => contactAndShipping.value.country,
  () => {
    contactAndShipping.value.state = "";
  },
);
</script>

<template>
  <Breadcrumb :home="homeBreadcrumb" :model="breadcrumbItems" class="mb-4" />

  <!-- Loading state -->
  <div v-if="pageLoading" class="loading-container">
    <i class="pi pi-spin pi-spinner text-4xl"></i>
  </div>

  <!-- Main checkout grid container -->
  <div class="checkout-grid">
    <!-- Left: Main checkout form -->
    <Card class="checkout-form">
      <template #content>
        <!-- Step 1: Contact Information -->
        <div v-show="currentStep === 1">
          <div class="section-header">
            <h2>Contact information</h2>
            <div v-if="!isLoggedIn" class="login-prompt">
              Already have an account? <a href="/login">Log in</a>
            </div>
          </div>

          <div class="form-group">
            <InputText
              v-model="contactAndShipping.email"
              type="email"
              placeholder="Email"
              class="w-full"
              :class="{ 'p-invalid': validationErrors.email }"
            />
            <small v-if="validationErrors.email" class="p-error">{{
              validationErrors.email
            }}</small>
          </div>

          <div class="form-group">
            <Checkbox
              v-model="contactAndShipping.newsletter"
              input-id="newsletter"
              binary
            />
            <label for="newsletter" class="ml-2"
              >Email me with news and offers</label
            >
          </div>

          <h2 class="mt-5">Shipping address</h2>

          <div class="form-row">
            <div class="form-group half">
              <InputText
                v-model="contactAndShipping.firstName"
                placeholder="First name"
                class="w-full"
                :class="{ 'p-invalid': validationErrors.firstName }"
              />
              <small v-if="validationErrors.firstName" class="p-error">{{
                validationErrors.firstName
              }}</small>
            </div>
            <div class="form-group half">
              <InputText
                v-model="contactAndShipping.lastName"
                placeholder="Last name"
                class="w-full"
                :class="{ 'p-invalid': validationErrors.lastName }"
              />
              <small v-if="validationErrors.lastName" class="p-error">{{
                validationErrors.lastName
              }}</small>
            </div>
          </div>

          <div class="form-group">
            <InputText
              v-model="contactAndShipping.company"
              placeholder="Company (optional)"
              class="w-full"
            />
          </div>

          <div class="form-group">
            <InputText
              v-model="contactAndShipping.address1"
              placeholder="Address"
              class="w-full"
              :class="{ 'p-invalid': validationErrors.address1 }"
            />
            <small v-if="validationErrors.address1" class="p-error">{{
              validationErrors.address1
            }}</small>
          </div>

          <div class="form-group">
            <InputText
              v-model="contactAndShipping.address2"
              placeholder="Apartment, suite, etc. (optional)"
              class="w-full"
            />
          </div>

          <div class="form-group">
            <Dropdown
              v-model="contactAndShipping.country"
              :options="countries"
              option-label="name"
              option-value="code"
              placeholder="Country"
              class="w-full"
              :class="{ 'p-invalid': validationErrors.country }"
            />
            <small v-if="validationErrors.country" class="p-error">{{
              validationErrors.country
            }}</small>
          </div>

          <div class="form-row three">
            <div class="form-group">
              <InputText
                v-model="contactAndShipping.city"
                placeholder="City"
                class="w-full"
                :class="{ 'p-invalid': validationErrors.city }"
              />
              <small v-if="validationErrors.city" class="p-error">{{
                validationErrors.city
              }}</small>
            </div>
            <div class="form-group">
              <Dropdown
                v-model="contactAndShipping.state"
                :options="states"
                option-label="name"
                option-value="code"
                :placeholder="
                  contactAndShipping.country === 'CA'
                    ? 'Province'
                    : contactAndShipping.country === 'MX'
                      ? 'State'
                      : 'State'
                "
                class="w-full"
                :class="{ 'p-invalid': validationErrors.state }"
              />
              <small v-if="validationErrors.state" class="p-error">{{
                validationErrors.state
              }}</small>
            </div>
            <div class="form-group">
              <InputMask
                v-if="contactAndShipping.country === 'US'"
                v-model="contactAndShipping.postalCode"
                mask="99999"
                placeholder="ZIP code"
                class="w-full"
                :class="{ 'p-invalid': validationErrors.postalCode }"
              />
              <InputMask
                v-else-if="contactAndShipping.country === 'CA'"
                v-model="contactAndShipping.postalCode"
                mask="a9a 9a9"
                placeholder="Postal code"
                class="w-full"
                :class="{ 'p-invalid': validationErrors.postalCode }"
              />
              <InputText
                v-else
                v-model="contactAndShipping.postalCode"
                placeholder="Postal code"
                class="w-full"
                :class="{ 'p-invalid': validationErrors.postalCode }"
              />
              <small v-if="validationErrors.postalCode" class="p-error">{{
                validationErrors.postalCode
              }}</small>
            </div>
          </div>

          <div class="form-group">
            <InputMask
              v-model="contactAndShipping.phone"
              mask="(999) 999-9999"
              placeholder="Phone"
              class="w-full"
              :class="{ 'p-invalid': validationErrors.phone }"
            />
            <small v-if="validationErrors.phone" class="p-error">{{
              validationErrors.phone
            }}</small>
          </div>

          <div class="form-group">
            <Checkbox
              v-model="contactAndShipping.saveInfo"
              input-id="save-info"
              binary
            />
            <label for="save-info" class="ml-2"
              >Save this information for next time</label
            >
          </div>

          <div class="form-actions">
            <Button
              label="Continue to shipping"
              class="continue-btn"
              icon="pi pi-arrow-right"
              icon-pos="right"
              @click="continueToShipping"
            />
          </div>
        </div>

        <!-- Step 2: Shipping Method -->
        <div v-show="currentStep === 2">
          <!-- Contact summary -->
          <div class="info-summary">
            <div class="summary-row">
              <span class="summary-label">Contact</span>
              <span class="summary-value">{{ contactAndShipping.email }}</span>
              <a class="summary-change" @click="currentStep = 1">Change</a>
            </div>
            <div class="summary-row">
              <span class="summary-label">Ship to</span>
              <span class="summary-value">
                {{ contactAndShipping.address1 }},
                {{ contactAndShipping.city }}, {{ contactAndShipping.state }}
                {{ contactAndShipping.postalCode }}
              </span>
              <a class="summary-change" @click="currentStep = 1">Change</a>
            </div>
          </div>

          <h2>Shipping method</h2>

          <div class="shipping-methods">
            <div
              v-for="method in shippingMethods"
              :key="method.id"
              class="shipping-option"
              :class="{ selected: selectedShippingMethod?.id === method.id }"
              @click="selectedShippingMethod = method"
            >
              <RadioButton
                :model-value="selectedShippingMethod?.id"
                :value="method.id"
                :input-id="`ship-${method.id}`"
              />
              <div class="shipping-details">
                <div class="shipping-name">{{ method.name }}</div>
                <div class="shipping-desc">{{ method.description }}</div>
              </div>
              <div class="shipping-price">
                {{ formatCurrency(method.price) }}
              </div>
            </div>
          </div>

          <div class="form-actions">
            <Button
              label="Return to information"
              class="back-btn"
              text
              icon="pi pi-angle-left"
              @click="currentStep = 1"
            />
            <Button
              label="Continue to payment"
              class="continue-btn"
              icon="pi pi-arrow-right"
              icon-pos="right"
              @click="continueToPayment"
            />
          </div>
        </div>

        <!-- Step 3: Payment -->
        <div v-show="currentStep === 3">
          <!-- Summary -->
          <div class="info-summary">
            <div class="summary-row">
              <span class="summary-label">Contact</span>
              <span class="summary-value">{{ contactAndShipping.email }}</span>
              <a class="summary-change" @click="currentStep = 1">Change</a>
            </div>
            <div class="summary-row">
              <span class="summary-label">Ship to</span>
              <span class="summary-value">
                {{ contactAndShipping.address1 }},
                {{ contactAndShipping.city }}, {{ contactAndShipping.state }}
                {{ contactAndShipping.postalCode }}
              </span>
              <a class="summary-change" @click="currentStep = 1">Change</a>
            </div>
            <div class="summary-row">
              <span class="summary-label">Method</span>
              <span class="summary-value"
                >{{ selectedShippingMethod?.name }} ·
                {{ formatCurrency(selectedShippingMethod?.price || 0) }}</span
              >
              <a class="summary-change" @click="currentStep = 2">Change</a>
            </div>
          </div>

          <h2>Payment</h2>
          <p class="payment-desc">All transactions are secure and encrypted.</p>

          <div class="payment-method">
            <div class="payment-option selected">
              <RadioButton
                model-value="card"
                value="card"
                input-id="pay-card"
                checked
              />
              <label for="pay-card" class="ml-2">Credit card</label>
              <div class="payment-icons">
                <i class="pi pi-credit-card"></i>
              </div>
            </div>

            <!-- Stripe Card Element -->
            <div class="card-fields">
              <div ref="stripeElementsContainer" class="stripe-element"></div>
              <div id="card-errors" class="text-red-500 text-sm mt-2"></div>
              <p v-if="!stripe" class="text-sm text-gray-600 mt-2">
                <i class="pi pi-spinner pi-spin mr-1"></i>
                Loading payment form...
              </p>
            </div>
          </div>

          <h2 class="mt-4">Billing address</h2>
          <div class="billing-option">
            <RadioButton
              v-model="sameAsShipping"
              :value="true"
              input-id="same-addr"
            />
            <label for="same-addr" class="ml-2">Same as shipping address</label>
          </div>
          <div class="billing-option">
            <RadioButton
              v-model="sameAsShipping"
              :value="false"
              input-id="diff-addr"
            />
            <label for="diff-addr" class="ml-2"
              >Use a different billing address</label
            >
          </div>

          <div class="form-actions">
            <Button
              label="Return to shipping"
              class="back-btn"
              text
              icon="pi pi-angle-left"
              @click="currentStep = 2"
            />
            <Button
              label="Complete order"
              class="continue-btn complete-btn"
              :loading="processing"
              :disabled="processing"
              @click="submitOrder"
            />
          </div>
        </div>
      </template>
    </Card>

    <!-- Right: Order summary -->
    <Card class="checkout-sidebar">
      <template #title> Order summary </template>

      <template #content>
        <!-- Cart items -->
        <div class="cart-items">
          <div v-for="item in cartItems" :key="item.id" class="cart-item">
            <div class="item-image">
              <!-- Calendar item display -->
              <div
                v-if="isCalendarItem(item) && getCalendarConfig(item)"
                class="image-placeholder calendar-preview"
                :title="`Click to preview ${getCalendarConfig(item).name || 'calendar'}`"
                style="cursor: pointer; position: relative"
                @click="showCalendarPreview(item)"
              >
                <div
                  style="
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    border-radius: 4px;
                  "
                >
                  <div
                    v-if="calendarSvgs[getCalendarConfig(item).calendarId]"
                    style="
                      width: 800px;
                      height: 800px;
                      transform: scale(0.08);
                      transform-origin: top left;
                      pointer-events: none;
                      position: absolute;
                      top: 0;
                      left: 0;
                    "
                    v-html="calendarSvgs[getCalendarConfig(item).calendarId]"
                  ></div>
                  <div
                    v-else
                    class="calendar-icon"
                    style="
                      position: relative;
                      width: 100%;
                      height: 100%;
                      display: flex;
                      flex-direction: column;
                      align-items: center;
                      justify-content: center;
                    "
                  >
                    <div class="calendar-year">
                      {{ getCalendarConfig(item).year }}
                    </div>
                    <i class="pi pi-calendar"></i>
                  </div>
                </div>
                <span class="item-quantity">{{ item.quantity }}</span>
              </div>
              <!-- Regular item display -->
              <div v-else class="image-placeholder">
                <span class="item-quantity">{{ item.quantity }}</span>
              </div>
            </div>
            <div class="item-details">
              <div class="item-name">
                {{ item.productName }}
                <span
                  v-if="isCalendarItem(item) && getCalendarConfig(item)"
                  class="calendar-name"
                >
                  -
                  {{
                    getCalendarConfig(item).name || getCalendarConfig(item).year
                  }}
                </span>
              </div>
              <div
                v-if="isCalendarItem(item) && getCalendarConfig(item)"
                class="item-variant"
              >
                <i class="pi pi-calendar mr-1"></i
                >{{ getCalendarConfig(item).year }} Calendar
              </div>
              <div
                v-else-if="item.configuration && !isCalendarItem(item)"
                class="item-variant"
              >
                {{ item.configuration }}
              </div>
            </div>
            <div class="item-price">{{ formatCurrency(item.lineTotal) }}</div>
          </div>
        </div>

        <Divider />

        <!-- Discount code -->
        <div class="discount-code">
          <div class="discount-input">
            <InputText placeholder="Discount code" class="w-full" />
            <Button label="Apply" outlined />
          </div>
        </div>

        <Divider />

        <!-- Totals -->
        <div class="summary-totals">
          <div class="total-row">
            <span>Subtotal</span>
            <span>{{ formatCurrency(cartSubtotal) }}</span>
          </div>
          <div class="total-row">
            <span>Shipping</span>
            <span>{{
              shippingCost > 0 ? formatCurrency(shippingCost) : "—"
            }}</span>
          </div>
          <div class="total-row">
            <span>Taxes</span>
            <span>{{ formatCurrency(taxAmount) }}</span>
          </div>
          <Divider />
          <div class="total-row total-final">
            <span>Total</span>
            <span class="total-amount">
              <small class="currency">USD</small>
              {{ formatCurrency(orderTotal) }}
            </span>
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
    modal
    dismissable-mask
  >
    <div
      class="calendar-preview-container"
      style="
        width: 100%;
        height: 70vh;
        overflow: auto;
        display: flex;
        align-items: center;
        justify-content: center;
      "
    >
      <div
        style="max-width: 100%; height: auto"
        v-html="previewCalendarSvg"
      ></div>
    </div>
  </Dialog>
</template>

<style scoped>
/* Layout */
.loading-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
}

/* Main grid layout for checkout */
.checkout-grid {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 1rem;
  margin: 0 auto 3rem; /* Add space before footer */
  padding: 0 1rem;
  align-items: start;
}

.checkout-form {
  min-width: 0; /* Prevent grid blowout */
}

.checkout-sidebar {
  position: sticky;
  top: 2rem;
  height: fit-content;
}

/* Sections */

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.section-header h2 {
  font-size: 1.125rem;
  font-weight: 400;
  margin: 0;
}

.login-prompt {
  font-size: 0.875rem;
  color: #545454;
}

.login-prompt a {
  color: #1773b0;
  text-decoration: none;
}

/* Forms */
.form-group {
  margin-bottom: 1rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.form-row.three {
  grid-template-columns: 2fr 1fr 1fr;
}

.form-group.half {
  margin-bottom: 0;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 1.5rem;
}

.continue-btn {
  background: #197bbd;
  border-color: #197bbd;
  color: white;
  padding: 0.875rem 1.5rem;
  font-size: 1rem;
}

.continue-btn:hover {
  background: #1a6fa7;
  border-color: #1a6fa7;
}

.complete-btn {
  background: #197bbd;
  border-color: #197bbd;
}

.back-btn {
  color: #1773b0;
}

/* Info summary */
.info-summary {
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 1rem;
  margin-bottom: 1.5rem;
  background: #fafafa;
}

.summary-row {
  display: grid;
  grid-template-columns: 80px 1fr auto;
  gap: 1rem;
  padding: 0.5rem 0;
  font-size: 0.875rem;
}

.summary-row:not(:last-child) {
  border-bottom: 1px solid #e1e1e1;
}

.summary-label {
  color: #545454;
}

.summary-value {
  color: #333;
}

.summary-change {
  color: #1773b0;
  cursor: pointer;
  text-decoration: none;
}

/* Shipping methods */
.shipping-methods {
  margin-bottom: 1.5rem;
}

.shipping-option {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  margin-bottom: 0.75rem;
  cursor: pointer;
  background: var(--p-surface-0);
}

.shipping-option.selected {
  border-color: #197bbd;
  background: #f3f9fc;
}

.shipping-details {
  flex: 1;
}

.shipping-name {
  font-weight: 500;
  color: #333;
}

.shipping-desc {
  font-size: 0.875rem;
  color: #737373;
}

.shipping-price {
  font-weight: 500;
  color: #333;
}

/* Payment */
.payment-desc {
  font-size: 0.875rem;
  color: #545454;
  margin-bottom: 1rem;
}

.payment-method {
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  overflow: hidden;
}

.payment-option {
  display: flex;
  align-items: center;
  padding: 1rem;
  background: var(--p-surface-0);
  border-bottom: 1px solid #d9d9d9;
}

.payment-option.selected {
  background: #f3f9fc;
}

.payment-icons {
  margin-left: auto;
}

.card-fields {
  padding: 1rem;
  background: #fafafa;
}

.stripe-element {
  padding: 12px;
  background: var(--p-surface-0);
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  min-height: 44px;
}

.stripe-element--focus {
  border-color: var(--p-primary-color);
  box-shadow: 0 0 0 1px var(--p-primary-color);
}

.stripe-element--invalid {
  border-color: #ef4444;
}

.stripe-element--complete {
  border-color: #10b981;
}

.billing-option {
  display: flex;
  align-items: center;
  margin-bottom: 0.75rem;
}

/* Sidebar */

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
  /* Add padding to prevent quantity badge clipping */
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
}

.image-placeholder.calendar-preview {
  background: var(--p-surface-0);
  border-color: #90caf9;
}

.image-placeholder.calendar-preview:hover {
  border-color: #1976d2;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.calendar-icon {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #1565c0;
}

.calendar-year {
  font-size: 1.2rem;
  font-weight: bold;
  line-height: 1;
  margin-bottom: 2px;
}

.calendar-icon .pi-calendar {
  font-size: 0.8rem;
}

.calendar-name {
  color: #666;
  font-size: 0.9em;
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
}

.item-variant {
  font-size: 0.875rem;
  color: #737373;
}

.item-price {
  font-weight: 500;
  color: #333;
}

.discount-code {
  margin: 1rem 0;
}

.discount-input {
  display: flex;
  gap: 0.5rem;
}

.summary-totals {
  margin-top: 1rem;
}

.total-row {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0;
  font-size: 0.875rem;
}

.total-final {
  font-size: 1.125rem;
  font-weight: 500;
}

.total-amount {
  display: flex;
  align-items: baseline;
  gap: 0.25rem;
}

.currency {
  font-size: 0.75rem;
  color: #737373;
}

/* Responsive */
@media (max-width: 968px) {
  .checkout-grid {
    grid-template-columns: 1fr;
    padding: 0 1rem; /* Maintain same side margins on mobile */
  }

  .checkout-sidebar {
    position: static;
    order: -1;
  }

  .express-buttons {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .form-row.three {
    grid-template-columns: 1fr;
  }
}
</style>
