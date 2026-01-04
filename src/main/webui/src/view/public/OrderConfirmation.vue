<template>
  <div class="order-confirmation-page">
    <div class="container">
      <!-- Loading state -->
      <div v-if="loading" class="loading-container">
        <ProgressSpinner />
        <p class="loading-text">Loading order details...</p>
      </div>

      <!-- Success state -->
      <div v-else-if="order" class="confirmation-content">
        <!-- Success Header -->
        <div class="success-header">
          <div class="success-icon">
            <i class="pi pi-check-circle"></i>
          </div>
          <h1 class="success-title">Order Confirmed!</h1>
          <p class="success-subtitle">
            Thank you for your order. We'll send you a confirmation email
            shortly.
          </p>
        </div>

        <!-- Order Details Card -->
        <Card class="order-details-card">
          <template #title>
            <div class="card-title-row">
              <h2>Order Details</h2>
              <Tag
                :value="formatOrderStatus(order.status)"
                :severity="getOrderStatusSeverity(order.status)"
              />
            </div>
          </template>
          <template #content>
            <!-- Order Info -->
            <div class="info-section">
              <div class="info-row">
                <span class="label">Order ID:</span>
                <span class="value">{{ order.id }}</span>
              </div>
              <div class="info-row">
                <span class="label">Order Date:</span>
                <span class="value">{{ formatDateTime(order.created) }}</span>
              </div>
              <div v-if="order.user" class="info-row">
                <span class="label">Customer:</span>
                <span class="value">{{ order.user.email }}</span>
              </div>
            </div>

            <Divider />

            <!-- Order Items -->
            <div class="calendar-section">
              <h3>Order Items</h3>
              <div
                v-for="item in order.items"
                :key="item.id"
                class="calendar-info mb-3"
              >
                <div class="calendar-details">
                  <p class="calendar-name">
                    {{ item.description || "Calendar" }}
                  </p>
                  <p class="calendar-meta">
                    {{ item.quantity }} x ${{
                      item.unitPrice?.toFixed(2) || "0.00"
                    }}
                  </p>
                  <p class="calendar-meta">Type: {{ item.productType }}</p>
                </div>
              </div>
            </div>

            <Divider />

            <!-- Shipping Address -->
            <div class="shipping-section">
              <h3>Shipping Address</h3>
              <address class="shipping-address">
                {{ formatAddress(order.shippingAddress) }}
              </address>
              <div v-if="order.trackingNumber" class="tracking-info">
                <Message severity="info" :closable="false">
                  <div class="tracking-content">
                    <span
                      >Tracking Number:
                      <strong>{{ order.trackingNumber }}</strong></span
                    >
                    <Button
                      label="Track Package"
                      icon="pi pi-external-link"
                      text
                      size="small"
                      @click="trackPackage"
                    />
                  </div>
                </Message>
              </div>
            </div>

            <Divider />

            <!-- Order Summary -->
            <div class="order-summary-section">
              <h3>Order Summary</h3>
              <div class="summary-rows">
                <div
                  v-for="item in order.items"
                  :key="item.id"
                  class="summary-row"
                >
                  <span>{{ item.description }} × {{ item.quantity }}</span>
                  <span>{{ formatCurrency(item.lineTotal) }}</span>
                </div>
                <div class="summary-row">
                  <span>Subtotal</span>
                  <span>{{ formatCurrency(order.subtotal) }}</span>
                </div>
                <div class="summary-row">
                  <span>Shipping</span>
                  <span>{{ formatCurrency(order.shippingCost || 0) }}</span>
                </div>
                <div class="summary-row">
                  <span>Tax</span>
                  <span>{{ formatCurrency(order.taxAmount || 0) }}</span>
                </div>
                <Divider />
                <div class="summary-row total">
                  <span>Total</span>
                  <span>{{ formatCurrency(order.totalPrice) }}</span>
                </div>
              </div>
            </div>

            <Divider />

            <!-- Delivery Timeline -->
            <div class="timeline-section">
              <h3>Delivery Status</h3>
              <div class="timeline">
                <div
                  class="timeline-item"
                  :class="{ active: order.status !== 'PENDING' }"
                >
                  <div class="timeline-marker"></div>
                  <div class="timeline-content">
                    <p class="timeline-title">Order Placed</p>
                    <p class="timeline-date">{{ formatDate(order.created) }}</p>
                  </div>
                </div>
                <div
                  class="timeline-item"
                  :class="{
                    active:
                      order.status === 'PROCESSING' ||
                      order.status === 'SHIPPED' ||
                      order.status === 'DELIVERED',
                  }"
                >
                  <div class="timeline-marker"></div>
                  <div class="timeline-content">
                    <p class="timeline-title">Processing</p>
                    <p
                      v-if="
                        order.status === 'PROCESSING' ||
                        order.status === 'SHIPPED' ||
                        order.status === 'DELIVERED'
                      "
                      class="timeline-date"
                    >
                      In progress
                    </p>
                  </div>
                </div>
                <div
                  class="timeline-item"
                  :class="{
                    active:
                      order.status === 'SHIPPED' ||
                      order.status === 'DELIVERED',
                  }"
                >
                  <div class="timeline-marker"></div>
                  <div class="timeline-content">
                    <p class="timeline-title">Shipped</p>
                    <p v-if="order.shippedAt" class="timeline-date">
                      {{ formatDate(order.shippedAt) }}
                    </p>
                  </div>
                </div>
                <div
                  class="timeline-item"
                  :class="{ active: order.status === 'DELIVERED' }"
                >
                  <div class="timeline-marker"></div>
                  <div class="timeline-content">
                    <p class="timeline-title">Delivered</p>
                    <p v-if="order.deliveredAt" class="timeline-date">
                      {{ formatDate(order.deliveredAt) }}
                    </p>
                    <p
                      v-else-if="estimatedDelivery"
                      class="timeline-date estimated"
                    >
                      Estimated: {{ formatDate(estimatedDelivery) }}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </Card>

        <!-- Action Buttons -->
        <div class="action-buttons">
          <Button
            label="Create Another Calendar"
            icon="pi pi-plus"
            @click="router.push('/')"
          />
          <Button
            label="View My Orders"
            icon="pi pi-list"
            outlined
            @click="viewOrders"
          />
        </div>
      </div>

      <!-- Error state -->
      <div v-else class="error-container">
        <Message severity="error" :closable="false">
          Failed to load order details. Please check your email for order
          confirmation.
        </Message>
        <Button
          label="Go Home"
          icon="pi pi-home"
          class="mt-4"
          @click="router.push('/')"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useToast } from "primevue/usetoast";
import { useAuthStore } from "../../stores/authStore";
import {
  fetchOrderById,
  formatOrderStatus,
  getOrderStatusSeverity,
  formatCurrency,
} from "../../services/orderService";
import {
  formatDate,
  formatDateTime,
  calculateEstimatedDelivery,
  downloadCalendarPDF,
} from "../../services/calendarService";
import CalendarPreview from "../../components/CalendarPreview.vue";
import Card from "primevue/card";
import Button from "primevue/button";
import Tag from "primevue/tag";
import ProgressSpinner from "primevue/progressspinner";
import Message from "primevue/message";
import Divider from "primevue/divider";

interface CalendarOrder {
  id: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  status: string;
  shippingAddress: any;
  paidAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  trackingNumber: string | null;
  created: string;
  updated: string;
  calendar: {
    id: string;
    name: string;
    year: number;
    generatedPdfUrl: string | null;
    template: {
      id: string;
      name: string;
    };
  };
  user: {
    id: string;
    email: string;
    displayName: string | null;
  };
}

const router = useRouter();
const route = useRoute();
const toast = useToast();
const authStore = useAuthStore();

// State
const loading = ref(true);
const order = ref<CalendarOrder | null>(null);

// Computed
const estimatedDelivery = computed(() => {
  if (!order.value || order.value.deliveredAt) return null;
  return calculateEstimatedDelivery(new Date(order.value.created));
});

// Methods
const formatAddress = (address: any) => {
  if (!address) return "N/A";
  const parts = [
    address.street,
    address.street2,
    address.city,
    `${address.state} ${address.postalCode}`,
    address.country,
  ].filter(Boolean);
  return parts.join(", ");
};

const downloadPDF = () => {
  if (!order.value?.calendar?.generatedPdfUrl) return;
  downloadCalendarPDF(
    order.value.calendar.generatedPdfUrl,
    order.value.calendar.name,
  );
};

const trackPackage = () => {
  // Open tracking URL (would need to be configured based on shipping provider)
  const trackingUrl = `https://www.trackingservice.com/track/${order.value.trackingNumber}`;
  window.open(trackingUrl, "_blank");
};

const viewOrders = () => {
  // Navigate to orders page (would need to be created)
  router.push("/orders");
};

// Initialize
onMounted(async () => {
  try {
    if (!authStore.isAuthenticated) {
      toast.add({
        severity: "warn",
        summary: "Login Required",
        detail: "Please log in to view order details",
        life: 3000,
      });
      authStore.initiateLogin("google");
      return;
    }

    const orderId = route.params.orderId as string;
    order.value = await fetchOrderById(orderId, authStore.token);

    if (!order.value) {
      throw new Error("Order not found");
    }
  } catch (error: any) {
    console.error("Error loading order:", error);
    toast.add({
      severity: "error",
      summary: "Error",
      detail: error.message || "Failed to load order",
      life: 3000,
    });
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.order-confirmation-page {
  min-height: 100vh;
  background: #f9fafb;
  padding: 2rem 0;
}

.container {
  max-width: 900px;
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

.success-header {
  text-align: center;
  padding: 2rem 0 3rem 0;
}

.success-icon {
  font-size: 4rem;
  color: #10b981;
  margin-bottom: 1rem;
}

.success-title {
  font-size: 2.5rem;
  font-weight: bold;
  color: #111827;
  margin-bottom: 0.5rem;
}

.success-subtitle {
  font-size: 1.125rem;
  color: #6b7280;
}

.order-details-card {
  margin-bottom: 2rem;
}

.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title-row h2 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.info-section {
  margin-bottom: 1rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 0.75rem 0;
}

.info-row .label {
  color: #6b7280;
  font-weight: 500;
}

.info-row .value {
  color: #111827;
  font-weight: 500;
}

.calendar-section h3,
.shipping-section h3,
.order-summary-section h3,
.timeline-section h3 {
  margin-bottom: 1rem;
  font-size: 1.125rem;
  font-weight: 600;
  color: #111827;
}

.calendar-info {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 1.5rem;
  align-items: start;
}

@media (max-width: 640px) {
  .calendar-info {
    grid-template-columns: 1fr;
  }
}

.calendar-preview-small {
  background: #f3f4f6;
  border-radius: 8px;
  overflow: hidden;
  aspect-ratio: 1;
}

.calendar-details {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.calendar-name {
  font-size: 1.125rem;
  font-weight: 600;
  color: #111827;
  margin: 0;
}

.calendar-meta {
  color: #6b7280;
  margin: 0;
}

.shipping-address {
  font-style: normal;
  color: #374151;
  line-height: 1.6;
}

.tracking-info {
  margin-top: 1rem;
}

.tracking-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.summary-rows {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0;
  color: #374151;
}

.summary-row.total {
  font-size: 1.25rem;
  font-weight: 600;
  color: #111827;
}

.timeline {
  position: relative;
  padding-left: 2rem;
}

.timeline::before {
  content: "";
  position: absolute;
  left: 0.5rem;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #e5e7eb;
}

.timeline-item {
  position: relative;
  padding-bottom: 2rem;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-marker {
  position: absolute;
  left: -1.5rem;
  top: 0.25rem;
  width: 1rem;
  height: 1rem;
  border-radius: 50%;
  background: #e5e7eb;
  border: 3px solid white;
  box-shadow: 0 0 0 2px #e5e7eb;
  z-index: 1;
}

.timeline-item.active .timeline-marker {
  background: #10b981;
  box-shadow: 0 0 0 2px #10b981;
}

.timeline-content {
  padding-left: 0.5rem;
}

.timeline-title {
  font-weight: 600;
  color: #111827;
  margin: 0 0 0.25rem 0;
}

.timeline-date {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0;
}

.timeline-date.estimated {
  font-style: italic;
}

.action-buttons {
  display: flex;
  gap: 1rem;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 2rem;
}
</style>
