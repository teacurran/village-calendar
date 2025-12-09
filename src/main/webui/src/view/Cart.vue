<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { useCartStore } from "../stores/cart";
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

// Store for calendar SVGs
const calendarSvgs = ref<Record<string, string>>({});
const showPreviewModal = ref(false);
const previewCalendarSvg = ref("");
const previewCalendarName = ref("");
const loading = ref(false);
const allUserCalendars = ref<any[]>([]);

// Breadcrumbs
const breadcrumbItems = computed(() => [{ label: "Cart" }]);

// Cart items
const cartItems = computed(() => cartStore.items || []);
const cartSubtotal = computed(() => cartStore.subtotal || 0);
const cartItemCount = computed(() => cartStore.itemCount || 0);

// Check if item is a calendar (has productCode 'print' or 'pdf', or has calendar config)
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
    } catch {
      // Ignore JSON parse errors
    }
  }
  return false;
};

// Parse configuration and get calendar details
const getCalendarConfig = (item: any) => {
  if (item.configuration) {
    try {
      const config =
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

// Load calendar SVGs for cart items
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
      } else if (config.calendarId) {
        const svg = await fetchCalendarSvg(config.calendarId);
        if (svg) {
          calendarSvgs.value[calendarKey] = svg;
        } else {
          const calendar = allUserCalendars.value.find(
            (c) => c.id === config.calendarId,
          );
          if (calendar) {
            if (calendar.generatedSvg) {
              calendarSvgs.value[calendarKey] = calendar.generatedSvg;
            } else if (calendar.svgContent) {
              calendarSvgs.value[calendarKey] = calendar.svgContent;
            }
          }
        }
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
      previewCalendarName.value =
        config.name ||
        item.templateName ||
        `Calendar ${item.year || config.year}`;
      showPreviewModal.value = true;
    }
  }
};

// Update item quantity
async function updateQuantity(item: any, newQuantity: number) {
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
  } catch (error) {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Failed to update quantity",
      life: 3000,
    });
  }
}

// Remove item from cart
async function removeItem(item: any) {
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
  } catch (error) {
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
    calendarSvgs.value = {};
    toast.add({
      severity: "success",
      summary: "Cart Cleared",
      detail: "All items removed from cart",
      life: 2000,
    });
  } catch (error) {
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

// Watch for cart changes and reload SVGs
watch(
  () => cartStore.items,
  async () => {
    await loadCalendarSvgs();
  },
  { deep: true },
);
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
                    <!-- Calendar Preview -->
                    <div
                      v-if="isCalendarItem(item) && calendarSvgs[item.id]"
                      class="calendar-thumbnail"
                      @click="showCalendarPreview(item)"
                    >
                      <div
                        style="
                          width: 3500px;
                          height: 2250px;
                          transform: scale(0.0514);
                          transform-origin: top left;
                          pointer-events: none;
                          position: absolute;
                          top: 0;
                          left: 0;
                        "
                        v-html="calendarSvgs[item.id]"
                      ></div>
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
      :style="{ width: '90vw', maxWidth: '800px' }"
    >
      <div
        v-if="previewCalendarSvg"
        class="calendar-preview bg-white p-4 rounded"
        v-html="previewCalendarSvg"
      />
    </Dialog>
  </div>
</template>

<style scoped>
/* Thumbnails for full cart page */
.product-thumbnail,
.product-thumbnail-placeholder {
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

/* Calendar thumbnail shows full design with correct aspect ratio (3500:2250) */
.calendar-thumbnail {
  width: 180px;
  height: 116px;
  background: white;
  border: 1px solid #90caf9;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  cursor: pointer;
}

.calendar-thumbnail:hover {
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

:deep(.p-inputnumber-button-group) {
  .p-button {
    padding: 0.25rem;
  }
}
</style>
