<script setup lang="ts">
import { computed } from "vue";
import { Tag } from "primevue";
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
      <div class="grid grid-cols-2 gap-3 text-sm">
        <div>
          <span class="text-surface-600">Calendar:</span>
          <span class="font-semibold ml-2">{{ order.calendar.name }}</span>
        </div>
        <div>
          <span class="text-surface-600">Year:</span>
          <span class="ml-2">{{ order.calendar.year }}</span>
        </div>
        <div>
          <span class="text-surface-600">Quantity:</span>
          <span class="ml-2">{{ order.quantity }}</span>
        </div>
        <div>
          <span class="text-surface-600">Unit Price:</span>
          <span class="ml-2">{{ formatCurrency(order.unitPrice) }}</span>
        </div>
        <div class="col-span-2">
          <span class="text-surface-600">Total Price:</span>
          <span class="font-bold text-lg ml-2">{{
            formatCurrency(order.totalPrice)
          }}</span>
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
  </div>
</template>

<style scoped>
.order-details-panel {
  max-height: 70vh;
  overflow-y: auto;
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
