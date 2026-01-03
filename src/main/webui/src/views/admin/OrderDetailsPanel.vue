<script setup lang="ts">
import { computed, ref } from "vue";
import { Tag, Button, Dialog } from "primevue";
import type { CalendarOrder } from "../../stores/orderStore";
import {
  formatCurrency,
  getOrderStatusSeverity,
  formatOrderStatus,
} from "../../services/orderService";

/**
 * OrderDetailsPanel Component
 * Displays comprehensive order information for admin review
 */

interface Props {
  order: CalendarOrder | null;
}

const props = defineProps<Props>();

const previewDialogVisible = ref(false);
const previewSvgContent = ref("");

/**
 * Format date for display
 */
function formatDate(date: string | undefined): string {
  if (!date) return "-";
  return new Date(date).toLocaleDateString();
}

/**
 * Format datetime for display
 */
function formatDateTime(date: string | undefined): string {
  if (!date) return "-";
  return new Date(date).toLocaleString();
}

/**
 * Format shipping address
 */
function formatAddress(address: any): string {
  if (!address) return "-";
  const parts = [
    address.street,
    address.street2,
    address.city,
    address.state,
    address.postalCode,
    address.country,
  ].filter(Boolean);
  return parts.join(", ");
}

/**
 * Get status timeline events
 */
const statusTimeline = computed(() => {
  if (!props.order) return [];

  const events = [];

  events.push({
    label: "Order Created",
    date: props.order.created,
    icon: "pi pi-shopping-cart",
    color: "primary",
  });

  if (props.order.paidAt) {
    events.push({
      label: "Payment Confirmed",
      date: props.order.paidAt,
      icon: "pi pi-check-circle",
      color: "success",
    });
  }

  if (props.order.status === "PROCESSING") {
    events.push({
      label: "Processing",
      date: props.order.updated,
      icon: "pi pi-cog",
      color: "info",
    });
  }

  if (props.order.status === "PRINTED") {
    events.push({
      label: "Printed",
      date: props.order.updated,
      icon: "pi pi-print",
      color: "info",
    });
  }

  if (props.order.shippedAt) {
    events.push({
      label: "Shipped",
      date: props.order.shippedAt,
      icon: "pi pi-send",
      color: "success",
    });
  }

  if (props.order.deliveredAt) {
    events.push({
      label: "Delivered",
      date: props.order.deliveredAt,
      icon: "pi pi-check",
      color: "success",
    });
  }

  if (props.order.status === "CANCELLED") {
    events.push({
      label: "Cancelled",
      date: props.order.updated,
      icon: "pi pi-times-circle",
      color: "danger",
    });
  }

  if (props.order.status === "REFUNDED") {
    events.push({
      label: "Refunded",
      date: props.order.updated,
      icon: "pi pi-refresh",
      color: "secondary",
    });
  }

  return events;
});

/**
 * Get SVG content from first order item configuration or item assets
 */
function getCalendarSvg(): string | null {
  if (!props.order?.items?.length) return null;
  const item = props.order.items[0];
  // Try configuration first
  if (item.configuration?.generatedSvg) {
    return item.configuration.generatedSvg;
  }
  // Try assets
  if (item.assets?.length) {
    const mainAsset = item.assets.find((a: { assetKey: string }) => a.assetKey === "main");
    if (mainAsset?.svgContent) return mainAsset.svgContent;
  }
  return null;
}

/**
 * Show calendar preview in modal
 */
function showCalendarPreview() {
  const svg = getCalendarSvg();
  if (svg) {
    previewSvgContent.value = svg;
    previewDialogVisible.value = true;
  }
}

/**
 * Get customer name (first initial + last name) from shipping address or user display name
 */
function getCustomerNameForFilename(): string {
  if (!props.order) return "customer";

  // Try shipping address first
  const addr = props.order.shippingAddress;
  if (addr?.firstName && addr?.lastName) {
    const firstInitial = addr.firstName.charAt(0).toUpperCase();
    return `${firstInitial}${addr.lastName}`;
  }

  // Try user display name
  if (props.order.user?.displayName) {
    const parts = props.order.user.displayName.split(" ");
    if (parts.length > 1) {
      const firstInitial = parts[0].charAt(0).toUpperCase();
      const lastName = parts[parts.length - 1];
      return `${firstInitial}${lastName}`;
    }
    return parts[0];
  }

  // Fallback to email prefix
  if (props.order.customerEmail) {
    return props.order.customerEmail.split("@")[0];
  }

  return "customer";
}

/**
 * Download PDF with order ID and customer name in filename
 */
function downloadPdf() {
  const svg = getCalendarSvg();
  if (!svg || !props.order) {
    console.error("No SVG content available for PDF download");
    return;
  }

  const orderNumber = props.order.orderNumber || props.order.id;
  const customerName = getCustomerNameForFilename();
  const firstItem = props.order.items?.[0];
  const year = firstItem?.configuration?.year || new Date().getFullYear();

  // For now, download as SVG - in production this would call a PDF generation endpoint
  const blob = new Blob([svg], { type: "image/svg+xml" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `calendar-${orderNumber}-${customerName}-${year}.pdf`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
</script>

<template>
  <div v-if="order" class="order-details-panel">
    <!-- Order Summary -->
    <div class="bg-surface-100 p-4 rounded-lg mb-4">
      <h3 class="font-semibold text-lg mb-3">Order Information</h3>
      <div class="grid grid-cols-2 gap-3 text-sm">
        <div>
          <span class="text-surface-600">Order ID:</span>
          <span class="font-mono ml-2">{{ order.id }}</span>
        </div>
        <div>
          <span class="text-surface-600">Status:</span>
          <Tag
            class="ml-2"
            :severity="getOrderStatusSeverity(order.status)"
            :value="formatOrderStatus(order.status)"
          />
        </div>
        <div>
          <span class="text-surface-600">Created:</span>
          <span class="ml-2">{{ formatDateTime(order.created) }}</span>
        </div>
        <div>
          <span class="text-surface-600">Last Updated:</span>
          <span class="ml-2">{{ formatDateTime(order.updated) }}</span>
        </div>
      </div>
    </div>

    <!-- Customer Information -->
    <div class="bg-surface-50 p-4 rounded-lg mb-4">
      <h3 class="font-semibold text-lg mb-3">Customer Details</h3>
      <div class="grid grid-cols-2 gap-3 text-sm">
        <div>
          <span class="text-surface-600">Name:</span>
          <span class="font-semibold ml-2">{{
            order.user.displayName || "N/A"
          }}</span>
        </div>
        <div>
          <span class="text-surface-600">Email:</span>
          <span class="ml-2">{{ order.user.email }}</span>
        </div>
        <div class="col-span-2">
          <span class="text-surface-600">Shipping Address:</span>
          <div class="font-semibold ml-2 mt-1">
            {{ formatAddress(order.shippingAddress) }}
          </div>
        </div>
      </div>
    </div>

    <!-- Calendar & Order Items -->
    <div class="bg-surface-50 p-4 rounded-lg mb-4">
      <h3 class="font-semibold text-lg mb-3">Order Items</h3>
      <div class="flex gap-4">
        <!-- Calendar Thumbnail -->
        <div class="calendar-thumbnail-container">
          <div
            v-if="getCalendarSvg()"
            class="calendar-thumbnail"
            @click="showCalendarPreview"
            v-html="getCalendarSvg()"
          ></div>
          <div v-else class="calendar-thumbnail-placeholder">
            <i class="pi pi-calendar text-2xl text-surface-400"></i>
          </div>
        </div>

        <!-- Order Items -->
        <div class="flex-1">
          <div class="grid grid-cols-2 gap-3 text-sm">
            <div v-for="item in order.items" :key="item.id" class="col-span-2 border-b pb-2 mb-2">
              <div class="font-semibold">{{ item.description || "Calendar" }}</div>
              <div class="text-surface-600">
                {{ item.quantity }} x {{ formatCurrency(item.unitPrice) }} = {{ formatCurrency(item.lineTotal) }}
              </div>
            </div>
            <div class="col-span-2">
              <span class="text-surface-600">Total Price:</span>
              <span class="font-bold text-lg ml-2">{{
                formatCurrency(order.totalPrice)
              }}</span>
            </div>
          </div>

          <!-- Action Buttons -->
          <div class="flex gap-2 mt-3">
            <Button
              v-if="getCalendarSvg()"
              label="Preview"
              icon="pi pi-eye"
              size="small"
              outlined
              @click="showCalendarPreview"
            />
            <Button
              v-if="getCalendarSvg()"
              label="Download PDF"
              icon="pi pi-download"
              size="small"
              outlined
              @click="downloadPdf"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Payment Information -->
    <div class="bg-surface-50 p-4 rounded-lg mb-4">
      <h3 class="font-semibold text-lg mb-3">Payment Details</h3>
      <div class="grid grid-cols-2 gap-3 text-sm">
        <div>
          <span class="text-surface-600">Paid At:</span>
          <span class="ml-2">{{ formatDateTime(order.paidAt) }}</span>
        </div>
        <div v-if="order.stripePaymentIntentId">
          <span class="text-surface-600">Payment Intent:</span>
          <span class="font-mono text-xs ml-2">{{
            order.stripePaymentIntentId
          }}</span>
        </div>
        <div v-if="order.stripeChargeId">
          <span class="text-surface-600">Charge ID:</span>
          <span class="font-mono text-xs ml-2">{{ order.stripeChargeId }}</span>
        </div>
      </div>
    </div>

    <!-- Shipping Information -->
    <div
      v-if="order.trackingNumber || order.shippedAt"
      class="bg-surface-50 p-4 rounded-lg mb-4"
    >
      <h3 class="font-semibold text-lg mb-3">Shipping Information</h3>
      <div class="grid grid-cols-2 gap-3 text-sm">
        <div v-if="order.shippedAt">
          <span class="text-surface-600">Shipped At:</span>
          <span class="ml-2">{{ formatDateTime(order.shippedAt) }}</span>
        </div>
        <div v-if="order.trackingNumber">
          <span class="text-surface-600">Tracking Number:</span>
          <span class="font-mono ml-2">{{ order.trackingNumber }}</span>
        </div>
        <div v-if="order.deliveredAt">
          <span class="text-surface-600">Delivered At:</span>
          <span class="ml-2">{{ formatDateTime(order.deliveredAt) }}</span>
        </div>
      </div>
    </div>

    <!-- Order Notes -->
    <div v-if="order.notes" class="bg-surface-50 p-4 rounded-lg mb-4">
      <h3 class="font-semibold text-lg mb-3">Admin Notes</h3>
      <p class="text-sm whitespace-pre-wrap">{{ order.notes }}</p>
    </div>

    <!-- Status Timeline -->
    <div class="bg-surface-50 p-4 rounded-lg">
      <h3 class="font-semibold text-lg mb-3">Status Timeline</h3>
      <div class="space-y-3">
        <div
          v-for="(event, index) in statusTimeline"
          :key="index"
          class="flex items-start gap-3"
        >
          <i :class="`pi ${event.icon} text-${event.color}`"></i>
          <div class="flex-1">
            <div class="font-semibold text-sm">{{ event.label }}</div>
            <div class="text-xs text-surface-600">
              {{ formatDateTime(event.date) }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Calendar Preview Dialog -->
    <Dialog
      v-model:visible="previewDialogVisible"
      modal
      header="Calendar Preview"
      :style="{ width: '90vw', maxWidth: '1200px' }"
      :dismissable-mask="true"
    >
      <div class="preview-container" v-html="previewSvgContent"></div>
    </Dialog>
  </div>
</template>

<style scoped>
.order-details-panel {
  max-height: 70vh;
  overflow-y: auto;
}

/* Calendar thumbnail */
.calendar-thumbnail-container {
  flex-shrink: 0;
}

.calendar-thumbnail {
  width: 120px;
  height: 78px;
  overflow: hidden;
  border-radius: 4px;
  border: 1px solid var(--p-surface-200);
  cursor: pointer;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.calendar-thumbnail:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.calendar-thumbnail :deep(svg) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.calendar-thumbnail-placeholder {
  width: 120px;
  height: 78px;
  background-color: var(--p-surface-100);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Preview dialog */
.preview-container {
  width: 100%;
  max-height: 70vh;
  overflow: auto;
}

.preview-container :deep(svg) {
  width: 100%;
  height: auto;
}

.text-primary {
  color: var(--p-primary-color);
}

.text-success {
  color: #22c55e;
}

.text-info {
  color: #3b82f6;
}

.text-danger {
  color: #ef4444;
}

.text-secondary {
  color: var(--p-surface-600);
}
</style>
