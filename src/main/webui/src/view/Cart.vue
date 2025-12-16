<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import {
  useCartStore,
  type CartItem,
  type CalendarConfiguration,
} from "../stores/cart";
import { useToast } from "primevue/usetoast";
import { homeBreadcrumb } from "../navigation/breadcrumbs";
import Breadcrumb from "primevue/breadcrumb";
import Button from "primevue/button";
import Card from "primevue/card";
import InputNumber from "primevue/inputnumber";
import Divider from "primevue/divider";
import Dialog from "primevue/dialog";
import ProgressSpinner from "primevue/progressspinner";

const router = useRouter();
const cartStore = useCartStore();
const toast = useToast();

// Preview modal state
const showPreviewModal = ref(false);
const previewImageUrl = ref("");
const previewCalendarSvg = ref("");
const previewCalendarName = ref("");
const loading = ref(false);

// Store for calendar SVGs (for items without templateId)
const calendarSvgs = ref<Record<string, string>>({});

// Breadcrumbs
const breadcrumbItems = computed(() => [{ label: "Cart" }]);

// Cart items
const cartItems = computed(() => cartStore.items || []);
const cartSubtotal = computed(() => cartStore.subtotal || 0);
const cartItemCount = computed(() => cartStore.itemCount || 0);

// Check if item is a calendar (has productCode 'print' or 'pdf', has templateId, or has calendar config)
const isCalendarItem = (item: CartItem) => {
  // Check productCode
  if (item.productCode === "print" || item.productCode === "pdf") {
    return true;
  }
  // Has templateId means it's a calendar
  if (item.templateId) {
    return true;
  }
  // Has calendar configuration (from static product page or custom calendar)
  const config = getCalendarConfig(item);
  if (
    config &&
    (config.year || config.theme || config.svgContent || config.generatedSvg)
  ) {
    return true;
  }
  return false;
};

// Parse configuration and get calendar details
const getCalendarConfig = (item: CartItem): CalendarConfiguration | null => {
  if (item.configuration) {
    try {
      const config: CalendarConfiguration =
        typeof item.configuration === "string"
          ? JSON.parse(item.configuration)
          : item.configuration;
      return config;
    } catch (e) {
      console.error(
        "Failed to parse calendar configuration:",
        e,
        item.configuration,
      );
      return null;
    }
  }
  return null;
};

// Check if configuration has rendering properties (from static product page)
const hasRenderingConfig = (config: CalendarConfiguration): boolean => {
  return !!(config.year && (config.theme || config.layoutStyle));
};

// Generate calendar SVG from configuration (for static product page purchases)
const generateCalendarSvgFromConfig = async (
  config: CalendarConfiguration,
): Promise<string | null> => {
  try {
    const response = await fetch("/calendar/generate-json", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(config),
    });
    if (response.ok) {
      const result = await response.json();
      return result.svg;
    }
  } catch (error) {
    console.error("Failed to generate calendar SVG from config:", error);
  }
  return null;
};

// Load calendar SVGs for items without templateId
const loadCalendarSvgs = async () => {
  if (!cartStore.items || cartStore.items.length === 0) return;

  for (const item of cartStore.items) {
    // Skip items with templateId - they use the PNG endpoint
    if (item.templateId) continue;

    const config = getCalendarConfig(item);
    const calendarKey = item.id;

    // Skip if we already have SVG for this item
    if (calendarSvgs.value[calendarKey]) continue;

    if (config) {
      // Priority 1: SVG already stored in configuration (homepage or static product page)
      if (config.svgContent) {
        calendarSvgs.value[calendarKey] = config.svgContent;
      } else if (config.generatedSvg) {
        calendarSvgs.value[calendarKey] = config.generatedSvg;
      }
      // Priority 2: Has rendering config - generate SVG on demand (fallback)
      else if (hasRenderingConfig(config)) {
        const svg = await generateCalendarSvgFromConfig(config);
        if (svg) {
          calendarSvgs.value[calendarKey] = svg;
        }
      }
    }
  }
};

// Check if item has SVG available (either from templateId or generated)
const hasSvgAvailable = (item: CartItem): boolean => {
  if (item.templateId) return true;
  return !!calendarSvgs.value[item.id];
};

// Get thumbnail URL for a cart item
const getThumbnailUrl = (item: CartItem): string => {
  // Use template-based PNG endpoint
  if (item.templateId) {
    return `/api/static-content/template/${item.templateId}.png`;
  }
  return "";
};

// Show calendar preview in modal
const showCalendarPreview = (item: CartItem) => {
  const config = getCalendarConfig(item);
  previewCalendarName.value =
    config?.name || item.templateName || `Calendar ${item.year}`;

  if (item.templateId) {
    // Use SVG endpoint for template-based items
    previewImageUrl.value = `/api/static-content/template/${item.templateId}.svg`;
    previewCalendarSvg.value = "";
    showPreviewModal.value = true;
  } else if (calendarSvgs.value[item.id]) {
    // Use generated SVG for items without templateId
    previewImageUrl.value = "";
    previewCalendarSvg.value = calendarSvgs.value[item.id];
    showPreviewModal.value = true;
  }
};

// Update item quantity
async function updateQuantity(item: CartItem, newQuantity: number) {
  if (newQuantity < 1) {
    await removeItem(item);
    return;
  }

  try {
    await cartStore.updateQuantity(item.id, newQuantity);
    toast.add({
      severity: "success",
      summary: "Cart Updated",
      detail: "Quantity updated",
      life: 2000,
    });
  } catch {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Failed to update quantity",
      life: 3000,
    });
  }
}

// Remove item from cart
async function removeItem(item: CartItem) {
  try {
    await cartStore.removeFromCart(item.id);

    // Remove the SVG from cache
    const calendarKey = item.id;
    if (calendarSvgs.value[calendarKey]) {
      delete calendarSvgs.value[calendarKey];
    }

    toast.add({
      severity: "success",
      summary: "Item Removed",
      detail: "Item removed from cart",
      life: 2000,
    });
  } catch {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Failed to remove item",
      life: 3000,
    });
  }
}

// Clear entire cart
async function clearCart() {
  try {
    await cartStore.clearCart();
    toast.add({
      severity: "success",
      summary: "Cart Cleared",
      detail: "All items removed from cart",
      life: 2000,
    });
  } catch {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Failed to clear cart",
      life: 3000,
    });
  }
}

// Continue shopping
function continueShopping() {
  router.push("/");
}

// Proceed to checkout
function proceedToCheckout() {
  router.push("/checkout");
}

// Initialize
onMounted(async () => {
  loading.value = true;
  try {
    await cartStore.fetchCart();
    await loadCalendarSvgs();
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="container mx-auto px-4 py-8 max-w-7xl">
    <!-- Breadcrumb -->
    <Breadcrumb :home="homeBreadcrumb" :model="breadcrumbItems" class="mb-6" />

    <!-- Page Title -->
    <h1 class="text-3xl font-bold mb-8">Shopping Cart</h1>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center items-center py-12">
      <ProgressSpinner />
    </div>

    <!-- Empty Cart -->
    <Card v-else-if="cartItemCount === 0" class="text-center py-12">
      <template #content>
        <i class="pi pi-shopping-cart text-6xl text-surface-300 mb-4"></i>
        <p class="text-xl mb-6">Your cart is empty</p>
        <Button
          label="Continue Shopping"
          icon="pi pi-arrow-left"
          class="p-button-primary"
          @click="continueShopping"
        />
      </template>
    </Card>

    <!-- Cart Content -->
    <div v-else class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <!-- Cart Items (Left Column) -->
      <div class="lg:col-span-2">
        <Card>
          <template #header>
            <div class="flex justify-between items-center p-4">
              <h2 class="text-xl font-semibold">
                Cart Items ({{ cartItemCount }})
              </h2>
              <Button
                label="Clear Cart"
                icon="pi pi-trash"
                class="p-button-text p-button-danger"
                size="small"
                @click="clearCart"
              />
            </div>
          </template>

          <template #content>
            <div class="space-y-4">
              <!-- Cart Item -->
              <div
                v-for="item in cartItems"
                :key="item.id"
                class="border-b pb-4 last:border-b-0"
              >
                <div class="flex gap-4">
                  <!-- Item Image/Preview -->
                  <div class="flex-shrink-0">
                    <!-- Calendar Thumbnail (PNG from API for template-based items) -->
                    <img
                      v-if="isCalendarItem(item) && item.templateId"
                      :src="getThumbnailUrl(item)"
                      :alt="item.templateName"
                      class="calendar-thumbnail"
                      @click="showCalendarPreview(item)"
                    />
                    <!-- Calendar SVG Thumbnail (for items without templateId) -->
                    <div
                      v-else-if="isCalendarItem(item) && calendarSvgs[item.id]"
                      class="calendar-svg-thumbnail"
                      @click="showCalendarPreview(item)"
                    >
                      <div
                        class="svg-container"
                        v-html="calendarSvgs[item.id]"
                      ></div>
                    </div>
                    <!-- Calendar Loading Placeholder -->
                    <div
                      v-else-if="isCalendarItem(item) && !item.templateId"
                      class="calendar-thumbnail-placeholder"
                    >
                      <i class="pi pi-spin pi-spinner text-gray-400"></i>
                    </div>
                    <!-- Regular Product Image -->
                    <img
                      v-else-if="item.imageUrl"
                      :src="item.imageUrl"
                      :alt="item.productName"
                      class="product-thumbnail"
                    />
                    <!-- Placeholder -->
                    <div v-else class="product-thumbnail-placeholder">
                      <i class="pi pi-box text-gray-400"></i>
                    </div>
                  </div>

                  <!-- Item Details -->
                  <div class="flex-1">
                    <h3 class="font-semibold text-lg mb-1">
                      {{ item.templateName || item.productName }}
                    </h3>
                    <p
                      v-if="item.description"
                      class="text-sm text-surface-600 mb-2"
                    >
                      {{ item.description }}
                    </p>

                    <!-- Calendar Details -->
                    <div
                      v-if="isCalendarItem(item)"
                      class="text-sm text-surface-600"
                    >
                      <template v-if="getCalendarConfig(item)">
                        <p v-if="getCalendarConfig(item).name">
                          {{ getCalendarConfig(item).name }}
                        </p>
                        <p>
                          Year: {{ item.year || getCalendarConfig(item).year }}
                        </p>
                      </template>
                      <template v-else>
                        <p>Year: {{ item.year }}</p>
                      </template>
                    </div>

                    <!-- Product Options -->
                    <div
                      v-if="item.options"
                      class="text-sm text-surface-600 mt-1"
                    >
                      <span v-for="(value, key) in item.options" :key="key">
                        {{ key }}: {{ value }}<br />
                      </span>
                    </div>
                  </div>

                  <!-- Price and Quantity -->
                  <div class="text-right">
                    <p class="font-semibold text-lg mb-2">
                      ${{ (item.lineTotal || 0).toFixed(2) }}
                    </p>
                    <p class="text-sm text-surface-600 mb-2">
                      ${{ (item.unitPrice || 0).toFixed(2) }} each
                    </p>

                    <div class="flex items-center gap-2">
                      <InputNumber
                        v-model="item.quantity"
                        :min="1"
                        :max="99"
                        show-buttons
                        button-layout="horizontal"
                        :input-style="{ width: '3rem' }"
                        size="small"
                        @update:model-value="(val) => updateQuantity(item, val)"
                      />
                      <Button
                        v-tooltip="'Remove'"
                        icon="pi pi-trash"
                        class="p-button-text p-button-danger"
                        size="small"
                        @click="removeItem(item)"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </Card>
      </div>

      <!-- Order Summary (Right Column) -->
      <div class="lg:col-span-1">
        <Card class="sticky top-4">
          <template #header>
            <h2 class="text-xl font-semibold p-4">Order Summary</h2>
          </template>

          <template #content>
            <div class="space-y-3">
              <div class="flex justify-between">
                <span>Subtotal</span>
                <span class="font-semibold"
                  >${{ cartSubtotal.toFixed(2) }}</span
                >
              </div>

              <div class="flex justify-between text-surface-600">
                <span>Shipping</span>
                <span>Calculated at checkout</span>
              </div>

              <div class="flex justify-between text-surface-600">
                <span>Tax</span>
                <span>Calculated at checkout</span>
              </div>

              <Divider />

              <div class="flex justify-between text-lg font-semibold">
                <span>Estimated Total</span>
                <span>${{ cartSubtotal.toFixed(2) }}</span>
              </div>

              <div class="space-y-2 pt-4">
                <Button
                  label="Proceed to Checkout"
                  icon="pi pi-arrow-right"
                  icon-pos="right"
                  class="w-full p-button-primary"
                  size="large"
                  @click="proceedToCheckout"
                />

                <Button
                  label="Continue Shopping"
                  icon="pi pi-arrow-left"
                  class="w-full p-button-text"
                  @click="continueShopping"
                />
              </div>
            </div>
          </template>
        </Card>
      </div>
    </div>

    <!-- Calendar Preview Modal -->
    <Dialog
      v-model:visible="showPreviewModal"
      :header="previewCalendarName"
      :modal="true"
      :dismissable-mask="true"
      :style="{ width: '90vw', maxWidth: '900px' }"
    >
      <div class="calendar-preview bg-white p-4 rounded">
        <!-- Inline SVG content (for generated/fetched SVGs) -->
        <div
          v-if="previewCalendarSvg"
          class="calendar-preview-svg"
          v-html="previewCalendarSvg"
        ></div>
        <!-- URL-based SVG (for template-based items) -->
        <object
          v-else-if="previewImageUrl"
          :data="previewImageUrl"
          type="image/svg+xml"
          class="w-full h-auto"
        >
          <img
            :src="previewImageUrl.replace('.svg', '.png')"
            alt="Calendar preview"
            class="w-full h-auto"
          />
        </object>
      </div>
    </Dialog>
  </div>
</template>

<style scoped>
/* Thumbnails for full cart page */
.product-thumbnail,
.product-thumbnail-placeholder,
.calendar-thumbnail-placeholder {
  width: 128px;
  height: 80px;
  background: #f5f5f5;
  border: 1px solid #e1e1e1;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.calendar-thumbnail-placeholder {
  background: white;
  border-color: #90caf9;
}

/* SVG thumbnail container for items without templateId */
.calendar-svg-thumbnail {
  width: 180px;
  height: 116px;
  background: white;
  border: 1px solid #90caf9;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.calendar-svg-thumbnail:hover {
  border-color: #1976d2;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.calendar-svg-thumbnail .svg-container {
  width: 800px;
  height: 800px;
  transform: scale(0.225);
  transform-origin: top left;
  pointer-events: none;
  position: absolute;
  top: 0;
  left: 0;
}

.calendar-svg-thumbnail .svg-container :deep(svg) {
  width: 100%;
  height: auto;
}

/* Calendar thumbnail as img element with correct aspect ratio (3500:2250) */
img.calendar-thumbnail {
  width: 180px;
  height: 116px;
  object-fit: cover;
  background: white;
  border: 1px solid #90caf9;
  border-radius: 4px;
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

img.calendar-thumbnail:hover {
  border-color: #1976d2;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.product-thumbnail {
  object-fit: cover;
}

.product-thumbnail-placeholder {
  background: #f5f5f5;
}

/* Modal preview styles */
.calendar-preview :deep(svg) {
  width: 100%;
  height: auto;
  max-height: 70vh;
}

.calendar-preview-svg :deep(svg) {
  width: 100%;
  height: auto;
  max-height: 70vh;
}

:deep(.p-inputnumber-button-group) {
  .p-button {
    padding: 0.25rem;
  }
}
</style>
