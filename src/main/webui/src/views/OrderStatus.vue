<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import Card from "primevue/card";
import Button from "primevue/button";
import ProgressSpinner from "primevue/progressspinner";
import Timeline from "primevue/timeline";
import { graphql } from "../services/graphqlService";
import type { CalendarOrder, CalendarOrderItem } from "../types/order";

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const order = ref<CalendarOrder | null>(null);
const error = ref<string | null>(null);

// Order status timeline events
const statusEvents = computed(() => {
  if (!order.value) return [];

  const events = [];

  // Order placed
  events.push({
    status: "Order Placed",
    date: order.value.created,
    icon: "pi pi-shopping-cart",
    color: "#10b981",
    completed: true,
  });

  // Payment received
  if (order.value.paidAt) {
    events.push({
      status: "Payment Received",
      date: order.value.paidAt,
      icon: "pi pi-credit-card",
      color: "#10b981",
      completed: true,
    });
  }

  // Processing
  const isProcessing =
    order.value.status === "PROCESSING" ||
    order.value.status === "SHIPPED" ||
    order.value.status === "DELIVERED";
  events.push({
    status: "Processing",
    date: isProcessing ? "In progress" : "Pending",
    icon: "pi pi-cog",
    color: isProcessing ? "#10b981" : "#9ca3af",
    completed: isProcessing,
  });

  // Shipped
  if (order.value.shippedAt) {
    events.push({
      status: "Shipped",
      date: order.value.shippedAt,
      icon: "pi pi-truck",
      color: "#10b981",
      completed: true,
      trackingNumber: order.value.trackingNumber,
    });
  } else if (
    order.value.status === "PAID" ||
    order.value.status === "PROCESSING"
  ) {
    events.push({
      status: "Shipped",
      date: "Pending",
      icon: "pi pi-truck",
      color: "#9ca3af",
      completed: false,
    });
  }

  // Delivered
  if (order.value.deliveredAt) {
    events.push({
      status: "Delivered",
      date: order.value.deliveredAt,
      icon: "pi pi-check-circle",
      color: "#10b981",
      completed: true,
    });
  } else if (order.value.status === "SHIPPED") {
    events.push({
      status: "Delivered",
      date: "In transit",
      icon: "pi pi-check-circle",
      color: "#9ca3af",
      completed: false,
    });
  }

  return events;
});

// Format date for display
function formatDate(dateString: string | undefined): string {
  if (!dateString || dateString === "Pending" || dateString === "In progress" || dateString === "In transit") {
    return dateString || "";
  }
  try {
    const date = new Date(dateString);
    return date.toLocaleDateString("en-US", {
      year: "numeric",
      month: "long",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return dateString;
  }
}

// Format currency
function formatCurrency(amount: number | undefined): string {
  if (amount === undefined || amount === null) return "$0.00";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(amount);
}

// Get status badge class
function getStatusClass(status: string): string {
  switch (status) {
    case "PAID":
    case "DELIVERED":
      return "status-success";
    case "PROCESSING":
    case "SHIPPED":
      return "status-info";
    case "PENDING":
      return "status-warning";
    case "CANCELLED":
    case "REFUNDED":
      return "status-danger";
    default:
      return "";
  }
}

// Download PDF (placeholder - would need backend endpoint)
async function downloadPdf(item: CalendarOrderItem) {
  // TODO: Implement PDF download endpoint
  alert("PDF download coming soon!");
}

// Go to home page
function goHome() {
  router.push({ name: "home" });
}

onMounted(async () => {
  try {
    const orderNumber = route.params.orderNumber as string;
    const orderId = route.params.orderId as string;

    if (!orderNumber || !orderId) {
      throw new Error("Invalid order link");
    }

    const query = `
      query OrderByNumberAndId($orderNumber: String!, $orderId: String!) {
        orderByNumberAndId(orderNumber: $orderNumber, orderId: $orderId) {
          id
          orderNumber
          customerEmail
          status
          subtotal
          shippingCost
          taxAmount
          totalPrice
          trackingNumber
          notes
          created
          updated
          paidAt
          shippedAt
          deliveredAt
          shippingAddress
          items {
            id
            productType
            productName
            calendarYear
            quantity
            unitPrice
            lineTotal
            itemStatus
          }
        }
      }
    `;

    const data = await graphql<{ orderByNumberAndId: CalendarOrder }>(query, {
      orderNumber,
      orderId,
    });

    if (!data.orderByNumberAndId) {
      throw new Error("Order not found");
    }

    order.value = data.orderByNumberAndId;
  } catch (err: any) {
    error.value = err.message || "Failed to load order";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="order-status-page">
    <div class="container">
      <!-- Loading State -->
      <Card v-if="loading" class="status-card">
        <template #content>
          <div class="loading-state">
            <ProgressSpinner />
            <p>Loading order details...</p>
          </div>
        </template>
      </Card>

      <!-- Error State -->
      <Card v-else-if="error" class="status-card">
        <template #content>
          <div class="error-state">
            <i
              class="pi pi-exclamation-triangle"
              style="font-size: 3rem; color: #f59e0b"
            ></i>
            <h1>Order Not Found</h1>
            <p>{{ error }}</p>
            <p class="help-text">
              If you believe this is an error, please contact support at
              <a href="mailto:support@villagecompute.com"
                >support@villagecompute.com</a
              >
            </p>
            <Button label="Go to Home" icon="pi pi-home" @click="goHome" />
          </div>
        </template>
      </Card>

      <!-- Order Details -->
      <template v-else-if="order">
        <Card class="status-card">
          <template #header>
            <div class="order-header">
              <div class="order-info">
                <h1>Order #{{ order.orderNumber }}</h1>
                <span :class="['status-badge', getStatusClass(order.status)]">
                  {{ order.status }}
                </span>
              </div>
              <p class="order-date">Placed on {{ formatDate(order.created) }}</p>
            </div>
          </template>

          <template #content>
            <!-- Order Timeline -->
            <div class="timeline-section">
              <h2>Order Progress</h2>
              <Timeline :value="statusEvents" layout="horizontal" align="top">
                <template #marker="slotProps">
                  <span
                    class="timeline-marker"
                    :style="{ backgroundColor: slotProps.item.color }"
                  >
                    <i :class="slotProps.item.icon"></i>
                  </span>
                </template>
                <template #content="slotProps">
                  <div class="timeline-content">
                    <strong>{{ slotProps.item.status }}</strong>
                    <p>{{ formatDate(slotProps.item.date) }}</p>
                    <p
                      v-if="slotProps.item.trackingNumber"
                      class="tracking-number"
                    >
                      Tracking: {{ slotProps.item.trackingNumber }}
                    </p>
                  </div>
                </template>
              </Timeline>
            </div>

            <!-- Order Items -->
            <div class="items-section">
              <h2>Order Items</h2>
              <div
                v-for="item in order.items"
                :key="item.id"
                class="order-item"
              >
                <div class="item-info">
                  <strong>{{ item.productName || "Calendar" }}</strong>
                  <span v-if="item.calendarYear"> ({{ item.calendarYear }})</span>
                  <p>Quantity: {{ item.quantity }}</p>
                </div>
                <div class="item-price">
                  {{ formatCurrency(item.lineTotal) }}
                </div>
                <div class="item-actions">
                  <Button
                    label="Download PDF"
                    icon="pi pi-download"
                    size="small"
                    outlined
                    @click="downloadPdf(item)"
                  />
                </div>
              </div>
            </div>

            <!-- Order Summary -->
            <div class="summary-section">
              <h2>Order Summary</h2>
              <div class="summary-row">
                <span>Subtotal</span>
                <span>{{ formatCurrency(order.subtotal || order.totalPrice) }}</span>
              </div>
              <div v-if="order.shippingCost" class="summary-row">
                <span>Shipping</span>
                <span>{{ formatCurrency(order.shippingCost) }}</span>
              </div>
              <div v-if="order.taxAmount" class="summary-row">
                <span>Tax</span>
                <span>{{ formatCurrency(order.taxAmount) }}</span>
              </div>
              <div class="summary-row total">
                <span>Total</span>
                <span>{{ formatCurrency(order.totalPrice) }}</span>
              </div>
            </div>

            <!-- Shipping Address -->
            <div v-if="order.shippingAddress" class="shipping-section">
              <h2>Shipping Address</h2>
              <div class="address-box">
                <template v-if="typeof order.shippingAddress === 'object'">
                  <p v-if="order.shippingAddress.name">
                    <strong>{{ order.shippingAddress.name }}</strong>
                  </p>
                  <p v-if="order.shippingAddress.line1">
                    {{ order.shippingAddress.line1 }}
                  </p>
                  <p v-if="order.shippingAddress.line2">
                    {{ order.shippingAddress.line2 }}
                  </p>
                  <p>
                    {{ order.shippingAddress.city }},
                    {{ order.shippingAddress.state }}
                    {{ order.shippingAddress.postalCode }}
                  </p>
                  <p>{{ order.shippingAddress.country }}</p>
                </template>
              </div>
            </div>

            <!-- Contact Support -->
            <div class="support-section">
              <h2>Need Help?</h2>
              <p>
                If you have any questions about your order, please contact us at
                <a href="mailto:support@villagecompute.com"
                  >support@villagecompute.com</a
                >
              </p>
            </div>
          </template>
        </Card>

        <div class="actions">
          <Button
            label="Create Another Calendar"
            icon="pi pi-plus"
            @click="goHome"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.order-status-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 2rem;
}

.container {
  max-width: 800px;
  margin: 0 auto;
}

.status-card {
  margin-bottom: 1rem;
}

.loading-state,
.error-state {
  padding: 2rem;
  text-align: center;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.error-state h1 {
  font-size: 1.5rem;
  color: #1f2937;
  margin: 1rem 0;
}

.error-state .help-text {
  color: #6b7280;
  margin: 1rem 0;
}

.order-header {
  padding: 1.5rem;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.order-info {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.order-info h1 {
  font-size: 1.5rem;
  margin: 0;
  color: #1f2937;
}

.order-date {
  color: #6b7280;
  margin: 0.5rem 0 0 0;
}

.status-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.875rem;
  font-weight: 500;
}

.status-success {
  background: #d1fae5;
  color: #065f46;
}

.status-info {
  background: #dbeafe;
  color: #1e40af;
}

.status-warning {
  background: #fef3c7;
  color: #92400e;
}

.status-danger {
  background: #fee2e2;
  color: #991b1b;
}

.timeline-section,
.items-section,
.summary-section,
.shipping-section,
.support-section {
  margin-bottom: 2rem;
}

.timeline-section h2,
.items-section h2,
.summary-section h2,
.shipping-section h2,
.support-section h2 {
  font-size: 1.125rem;
  color: #1f2937;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #e5e7eb;
}

.timeline-marker {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 50%;
  color: white;
}

.timeline-content {
  text-align: center;
}

.timeline-content strong {
  display: block;
  color: #1f2937;
}

.timeline-content p {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0.25rem 0 0 0;
}

.tracking-number {
  font-size: 0.75rem;
  color: #3b82f6;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 8px;
  margin-bottom: 0.5rem;
}

.item-info {
  flex: 1;
}

.item-info strong {
  color: #1f2937;
}

.item-info p {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0.25rem 0 0 0;
}

.item-price {
  font-weight: 600;
  color: #1f2937;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0;
  color: #4b5563;
}

.summary-row.total {
  border-top: 2px solid #e5e7eb;
  margin-top: 0.5rem;
  padding-top: 1rem;
  font-weight: 600;
  font-size: 1.125rem;
  color: #1f2937;
}

.address-box {
  background: #f9fafb;
  padding: 1rem;
  border-radius: 8px;
}

.address-box p {
  margin: 0.25rem 0;
  color: #4b5563;
}

.support-section p {
  color: #6b7280;
}

.support-section a {
  color: #3b82f6;
}

.actions {
  text-align: center;
  margin-top: 1rem;
}

@media (max-width: 640px) {
  .order-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .item-actions {
    width: 100%;
  }

  .item-actions button {
    width: 100%;
  }
}
</style>
