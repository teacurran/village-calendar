<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import Drawer from "primevue/drawer";
import Button from "primevue/button";
import Divider from "primevue/divider";
import Dialog from "primevue/dialog";
import { useCartStore } from "../stores/cart";
import { useToast } from "primevue/usetoast";
import { useRouter } from "vue-router";
import { ROUTE_NAMES } from "../navigation/routes";

const { t } = useI18n({ useScope: "global" });
const cartStore = useCartStore();
const toast = useToast();
const router = useRouter();

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
  // templateId is used to store the product ID
  if (isCalendarItem(item) && item.configuration) {
    try {
      return JSON.parse(item.configuration);
    } catch (e) {
      console.error(
        "Failed to parse calendar configuration for item:",
        item.id,
        e,
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

// Load calendar SVGs when cart items change
const loadCalendarSvgs = async () => {
  if (!cartStore.items || cartStore.items.length === 0) return;

  for (const item of cartStore.items) {
    const config = getCalendarConfig(item);

    if (config) {
      const calendarKey = item.id;

      if (config.svgContent) {
        calendarSvgs.value[calendarKey] = config.svgContent;
      } else if (config.calendarId) {
        const svg = await fetchCalendarSvg(config.calendarId);
        if (svg) {
          calendarSvgs.value[calendarKey] = svg;
        }
      }
    }
  }
};

// Show calendar preview
const showCalendarPreview = (item: any) => {
  const config = getCalendarConfig(item);
  if (config) {
    // Use item.id as the key, same as in loadCalendarSvgs
    const calendarKey = item.id;
    if (calendarSvgs.value[calendarKey]) {
      previewCalendarSvg.value = calendarSvgs.value[calendarKey];
      previewCalendarName.value = config.name || `${config.year} Calendar`;
      showPreviewModal.value = true;
    }
  }
};

// Fetch cart on component mount
onMounted(async () => {
  await cartStore.fetchCart(true);
  await loadCalendarSvgs();
});

// Watch for cart changes
watch(
  () => cartStore.items,
  async () => {
    await loadCalendarSvgs();
  },
);

const isOpen = computed({
  get: () => cartStore.isOpen,
  set: (value) => {
    if (value) {
      cartStore.openCart();
      loadCalendarSvgs();
    } else {
      cartStore.closeCart();
    }
  },
});

const updateQuantity = async (itemId: string, quantity: number) => {
  try {
    await cartStore.updateQuantity(itemId, quantity);
  } catch (error) {
    toast.add({
      severity: "error",
      summary: t("cart.error.title"),
      detail: t("cart.error.updateQuantity"),
      life: 3000,
    });
  }
};

const removeItem = async (itemId: string) => {
  try {
    await cartStore.removeFromCart(itemId);
  } catch (error) {
    toast.add({
      severity: "error",
      summary: t("cart.error.title"),
      detail: t("cart.error.removeItem"),
      life: 3000,
    });
  }
};

const clearCart = async () => {
  if (confirm(t("cart.clearCartConfirm"))) {
    try {
      await cartStore.clearCart();
    } catch (error) {
      toast.add({
        severity: "error",
        summary: t("cart.error.title"),
        detail: t("cart.error.clearCart"),
        life: 3000,
      });
    }
  }
};

const proceedToCheckout = () => {
  cartStore.closeCart();
  router.push({ name: ROUTE_NAMES.CHECKOUT });
};

const viewFullCart = () => {
  cartStore.closeCart();
  router.push({ name: ROUTE_NAMES.CART });
};
</script>

<template>
  <Drawer
    v-model:visible="isOpen"
    position="right"
    class="cart-drawer"
    :style="{ width: '400px' }"
  >
    <template #header>
      <div class="flex align-items-center gap-2">
        <i class="pi pi-shopping-cart"></i>
        <span class="font-semibold">{{ $t("cart.title") }}</span>
        <span v-if="cartStore.itemCount > 0" class="text-sm text-gray-600">
          ({{ cartStore.itemCount }}
          {{ cartStore.itemCount === 1 ? $t("cart.item") : $t("cart.items") }})
        </span>
      </div>
    </template>

    <div class="cart-content">
      <!-- Empty Cart State -->
      <div v-if="cartStore.isEmpty" class="empty-cart text-center py-8">
        <i class="pi pi-shopping-cart text-6xl text-gray-300 mb-4"></i>
        <h3 class="text-xl mb-2">{{ $t("cart.empty.title") }}</h3>
        <p class="text-gray-600 mb-4">{{ $t("cart.empty.description") }}</p>
        <Button
          :label="$t('cart.empty.continueShopping')"
          class="p-button-outlined"
          @click="cartStore.closeCart()"
        />
      </div>

      <!-- Cart Items -->
      <div v-else>
        <div class="cart-items">
          <div v-for="item in cartStore.items" :key="item.id" class="cart-item">
            <!-- Product Image -->
            <div class="item-image">
              <!-- Calendar Preview -->
              <div
                v-if="isCalendarItem(item) && getCalendarConfig(item)"
                class="image-placeholder calendar-preview"
                :title="`Click to preview ${getCalendarConfig(item).name || 'calendar'}`"
                @click="showCalendarPreview(item)"
              >
                <div
                  v-if="calendarSvgs[item.id]"
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
                  v-html="calendarSvgs[item.id]"
                ></div>
                <div v-else class="calendar-icon">
                  <div class="calendar-year">
                    {{ getCalendarConfig(item).year }}
                  </div>
                  <i class="pi pi-calendar"></i>
                </div>
              </div>
              <!-- Regular Product -->
              <div v-else class="image-placeholder">
                <i class="pi pi-box text-gray-400"></i>
              </div>
            </div>

            <!-- Product Details -->
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
              <div v-else-if="item.notes" class="item-variant">
                {{ item.notes }}
              </div>

              <!-- Quantity and Price -->
              <div class="item-controls">
                <!-- Quantity Controls -->
                <div class="quantity-controls">
                  <Button
                    icon="pi pi-minus"
                    class="p-button-xs p-button-outlined p-button-rounded"
                    :disabled="item.quantity <= 1"
                    @click="updateQuantity(item.id, item.quantity - 1)"
                  />
                  <span class="quantity-display">{{ item.quantity }}</span>
                  <Button
                    icon="pi pi-plus"
                    class="p-button-xs p-button-outlined p-button-rounded"
                    :disabled="item.quantity >= 999"
                    @click="updateQuantity(item.id, item.quantity + 1)"
                  />
                </div>

                <!-- Price -->
                <div class="item-price">
                  {{
                    new Intl.NumberFormat("en-US", {
                      style: "currency",
                      currency: "USD",
                    }).format(item.lineTotal)
                  }}
                </div>
              </div>

              <!-- Remove Button -->
              <Button
                :label="$t('cart.removeFromCart')"
                icon="pi pi-trash"
                class="p-button-xs p-button-outlined mt-2"
                severity="danger"
                @click="removeItem(item.id)"
              />
            </div>
          </div>
        </div>

        <Divider />

        <!-- Cart Summary -->
        <div class="cart-summary">
          <div class="flex justify-content-between align-items-center mb-3">
            <span class="font-semibold">{{ $t("cart.total") }}:</span>
            <span class="font-bold text-xl">{{
              cartStore.totalDisplayAmount
            }}</span>
          </div>

          <div class="cart-actions">
            <Button
              :label="$t('cart.checkout')"
              icon="pi pi-credit-card"
              class="w-full mb-2"
              @click="proceedToCheckout"
            />
            <Button
              label="View Cart"
              icon="pi pi-shopping-cart"
              class="w-full p-button-outlined mb-2"
              @click="viewFullCart"
            />
            <Button
              :label="$t('cart.empty.continueShopping')"
              class="w-full p-button-text"
              @click="cartStore.closeCart()"
            />
            <Button
              :label="$t('cart.clearCart')"
              class="w-full p-button-outlined"
              severity="danger"
              @click="clearCart"
            />
          </div>
        </div>
      </div>
    </div>
  </Drawer>

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
.cart-drawer {
  --drawer-width: 400px;
}

.cart-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.cart-items {
  flex: 1;
  overflow-y: auto;
  margin-bottom: 1rem;
}

.cart-item {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--surface-200);
}

.cart-item:last-child {
  border-bottom: none;
}

.item-image {
  position: relative;
  flex-shrink: 0;
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
  overflow: hidden;
}

.image-placeholder.calendar-preview {
  background: white;
  border-color: #90caf9;
  cursor: pointer;
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
}

.calendar-icon .pi {
  font-size: 0.875rem;
  margin-top: 2px;
}

.item-details {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-weight: 500;
  color: var(--text-color);
  margin-bottom: 0.25rem;
  line-height: 1.3;
}

.calendar-name {
  color: var(--text-color-secondary);
  font-size: 0.875rem;
}

.item-variant {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  margin-bottom: 0.5rem;
}

.item-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 0.5rem;
}

.quantity-controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.quantity-display {
  min-width: 2rem;
  text-align: center;
  font-weight: 500;
}

.quantity-controls .p-button {
  width: 24px;
  height: 24px;
}

.quantity-controls .p-button :deep(.p-button-icon) {
  font-size: 0.75rem;
}

.item-price {
  font-weight: 600;
  color: var(--text-color);
}

.cart-summary {
  border-top: 1px solid var(--surface-200);
  padding-top: 1rem;
  background: var(--surface-0);
  margin-top: auto;
}

.cart-actions {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.empty-cart {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

@media (max-width: 480px) {
  .cart-drawer {
    --drawer-width: 100vw;
  }
}
</style>
