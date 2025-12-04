<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import Breadcrumb from "primevue/breadcrumb";
import Button from "primevue/button";
import Card from "primevue/card";
import Dialog from "primevue/dialog";
import Timeline from "primevue/timeline";
import Tag from "primevue/tag";
import ProgressSpinner from "primevue/progressspinner";
import { homeBreadcrumb } from "../navigation/breadcrumbs";
import { useAuthStore } from "../stores/authStore";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const order = ref<any>(null);
const loading = ref(true);
const estimatedDelivery = ref(null);
const previewDialogVisible = ref(false);
const previewSvgContent = ref("");

const breadcrumbs = ref([{ label: "Order Confirmation" }]);

const orderTimeline = ref([
  {
    status: "Order Placed",
    date: new Date().toLocaleDateString(),
    icon: "pi-shopping-cart",
    color: "#9C27B0",
    completed: true,
  },
  {
    status: "Processing",
    date: "In Progress",
    icon: "pi-cog",
    color: "#673AB7",
    completed: false,
  },
  {
    status: "Printed",
    date: "Pending",
    icon: "pi-print",
    color: "#2196F3",
    completed: false,
  },
  {
    status: "Shipped",
    date: "Pending",
    icon: "pi-send",
    color: "#FF9800",
    completed: false,
  },
  {
    status: "Delivered",
    date: "Pending",
    icon: "pi-check",
    color: "#607D8B",
    completed: false,
  },
]);

onMounted(async () => {
  // Scroll to top
  window.scrollTo({ top: 0, behavior: "smooth" });

  try {
    // Try to get order data from session first (direct checkout flow)
    const orderData = sessionStorage.getItem("lastOrder");
    const pendingOrderInfo = sessionStorage.getItem("pendingOrderInfo");

    if (orderData) {
      // Direct flow - order info stored after payment in Checkout.vue
      order.value = JSON.parse(orderData);
      sessionStorage.removeItem("lastOrder");
    } else if (pendingOrderInfo) {
      // Redirect flow - restore pending order info saved before Stripe redirect
      const pendingOrder = JSON.parse(pendingOrderInfo);
      const orderId = route.params.orderId as string;
      order.value = {
        orderNumber: orderId,
        ...pendingOrder,
      };
      sessionStorage.removeItem("pendingOrderInfo");
    } else if (route.params.orderId) {
      // Fetch from backend if no session data
      const orderId = route.params.orderId as string;
      const fetchedOrder = await fetchOrderFromBackend(orderId);
      if (fetchedOrder) {
        order.value = fetchedOrder;
      }
    }

    if (!order.value) {
      // Redirect if no order data available
      router.push({ name: "templates" });
      return;
    }

    // Calculate estimated delivery
    if (order.value.shippingMethod?.estimatedDays) {
      const days = order.value.shippingMethod.estimatedDays;
      const deliveryDate = new Date();
      deliveryDate.setDate(deliveryDate.getDate() + (days[1] || days));
      estimatedDelivery.value = deliveryDate.toLocaleDateString("en-US", {
        weekday: "long",
        year: "numeric",
        month: "long",
        day: "numeric",
      });
    }
  } catch (err) {
    console.error("Error loading order:", err);
  } finally {
    loading.value = false;
  }
});

// Fetch order from backend API
async function fetchOrderFromBackend(orderId: string) {
  try {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };
    if (authStore.token) {
      headers["Authorization"] = `Bearer ${authStore.token}`;
    }

    const response = await fetch("/graphql", {
      method: "POST",
      headers,
      body: JSON.stringify({
        query: `
          query GetOrder($orderNumber: String!) {
            orderByNumber(orderNumber: $orderNumber) {
              id
              orderNumber
              status
              customerEmail
              subtotal
              shippingCost
              taxAmount
              totalPrice
              shippingAddress
              items {
                id
                productType
                productName
                calendarYear
                quantity
                unitPrice
                lineTotal
                configuration
                itemStatus
              }
            }
          }
        `,
        variables: { orderNumber: orderId },
      }),
    });

    if (!response.ok) return null;

    const result = await response.json();
    if (result.errors || !result.data?.orderByNumber) return null;

    const backendOrder = result.data.orderByNumber;

    // Map backend fields to expected format
    return {
      orderNumber: backendOrder.orderNumber,
      email: backendOrder.customerEmail,
      items: backendOrder.items,
      subtotal: backendOrder.subtotal,
      shippingCost: backendOrder.shippingCost,
      taxAmount: backendOrder.taxAmount,
      totalAmount: backendOrder.totalPrice,
      shippingAddress: backendOrder.shippingAddress,
    };
  } catch (err) {
    console.error("Failed to fetch order:", err);
    return null;
  }
}

function formatCurrency(amount) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(amount);
}

function printOrder() {
  window.print();
}

function continueShopping() {
  router.push({ name: "templates" });
}

// Parse item configuration - it may be a string or object
function parseItemConfig(item: any) {
  if (!item.configuration) return null;
  if (typeof item.configuration === "string") {
    try {
      return JSON.parse(item.configuration);
    } catch {
      return null;
    }
  }
  return item.configuration;
}

// Get product type display info
function getProductTypeInfo(item: any) {
  const config = parseItemConfig(item);
  const productType = config?.productType || "print";

  if (productType === "pdf") {
    return {
      label: "Digital PDF",
      icon: "pi-file-pdf",
      severity: "info",
    };
  }
  return {
    label: "Printed Calendar",
    icon: "pi-print",
    severity: "success",
  };
}

// Check if item includes PDF access (PDF product or print includes free PDF)
function hasPdfAccess(item: any) {
  const config = parseItemConfig(item);
  // Both PDF products and printed calendars include PDF access
  return config?.productType === "pdf" || config?.productType === "print";
}

// Get the calendar year from item
function getItemYear(item: any) {
  const config = parseItemConfig(item);
  return (
    config?.year || config?.configuration?.year || new Date().getFullYear()
  );
}

// Get SVG content from item for preview
function getItemSvgContent(item: any) {
  const config = parseItemConfig(item);
  return config?.svgContent || null;
}

// Show calendar preview
function showPreview(item: any) {
  const svg = getItemSvgContent(item);
  if (svg) {
    previewSvgContent.value = svg;
    previewDialogVisible.value = true;
  }
}

// Get customer name parts from order for filename
function getCustomerNameForFilename(): string {
  if (!order.value) return "customer";

  // Try shipping address first
  const addr = order.value.shippingAddress;
  if (addr?.firstName && addr?.lastName) {
    const firstInitial = addr.firstName.charAt(0).toUpperCase();
    const lastName = addr.lastName;
    return `${firstInitial}${lastName}`;
  }

  // Fallback to email prefix
  if (order.value.email) {
    return order.value.email.split("@")[0];
  }

  return "customer";
}

// Download PDF for item via backend conversion
async function downloadPdf(item: any) {
  const svg = getItemSvgContent(item);
  if (!svg) {
    console.error("No SVG content available for PDF download");
    return;
  }

  const year = getItemYear(item);
  const orderNumber = order.value?.orderNumber || "order";
  const customerName = getCustomerNameForFilename();
  const filename = `calendar-${orderNumber}-${customerName}-${year}.pdf`;

  try {
    // Call backend to convert SVG to PDF
    const response = await fetch("/api/calendar/svg-to-pdf", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        svgContent: svg,
        year: year,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to generate PDF: ${response.status}`);
    }

    // Download the PDF blob
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  } catch (error) {
    console.error("Failed to generate PDF:", error);
    // Fallback: download as SVG if PDF generation fails
    const blob = new Blob([svg], { type: "image/svg+xml" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename.replace(".pdf", ".svg");
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }
}
</script>

<template>
  <Breadcrumb :home="homeBreadcrumb" :model="breadcrumbs" class="mb-4" />

  <!-- Loading State -->
  <div
    v-if="loading"
    class="flex justify-content-center align-items-center"
    style="min-height: 400px"
  >
    <ProgressSpinner />
  </div>

  <div v-else class="order-confirmation-container">
    <!-- Success message -->
    <Card class="confirmation-header mb-4">
      <template #content>
        <div class="text-center">
          <div class="success-icon mb-4">
            <i class="pi pi-check-circle text-6xl text-green-500"></i>
          </div>

          <h1 class="text-3xl font-bold mb-2">Thank You for Your Order!</h1>
          <p class="text-lg text-gray-600 mb-4">
            Your order has been successfully placed and is being processed.
          </p>

          <div
            v-if="order"
            class="order-number-box p-3 bg-gray-50 border-round mb-4 inline-block"
          >
            <div class="text-sm text-gray-600 mb-1">Order Number</div>
            <div class="text-2xl font-bold text-primary">
              {{ order.orderNumber || "VC-" + Date.now() }}
            </div>
          </div>

          <p class="text-gray-600">
            A confirmation email has been sent to
            <strong>{{ order?.email }}</strong>
          </p>
        </div>
      </template>
    </Card>

    <div class="order-grid">
      <!-- Order details -->
      <div class="order-main">
        <!-- Order timeline -->
        <Card class="mb-4">
          <template #title>
            <h3>Order Status</h3>
          </template>
          <template #content>
            <Timeline
              :value="orderTimeline"
              align="horizontal"
              class="order-timeline"
            >
              <template #marker="slotProps">
                <span
                  class="timeline-marker"
                  :class="{ completed: slotProps.item.completed }"
                  :style="{
                    backgroundColor: slotProps.item.completed
                      ? slotProps.item.color
                      : '#e0e0e0',
                  }"
                >
                  <i :class="`pi ${slotProps.item.icon}`"></i>
                </span>
              </template>
              <template #content="slotProps">
                <div class="text-center">
                  <div class="font-semibold">{{ slotProps.item.status }}</div>
                  <div class="text-sm text-gray-600">
                    {{ slotProps.item.date }}
                  </div>
                </div>
              </template>
            </Timeline>

            <div
              v-if="estimatedDelivery"
              class="estimated-delivery mt-4 p-3 bg-blue-50 border-round"
            >
              <div class="flex align-items-center gap-2">
                <i class="pi pi-calendar text-blue-600"></i>
                <span>
                  Estimated Delivery: <strong>{{ estimatedDelivery }}</strong>
                </span>
              </div>
            </div>
          </template>
        </Card>

        <!-- Order items -->
        <Card class="mb-4">
          <template #title>
            <h3>Order Items</h3>
          </template>
          <template #content>
            <div v-if="order?.items" class="order-items">
              <div
                v-for="item in order.items"
                :key="item.id"
                class="order-item pb-3 mb-3 border-bottom-1 border-gray-200"
              >
                <div class="flex gap-3">
                  <!-- Calendar preview thumbnail -->
                  <div class="item-image">
                    <div
                      v-if="getItemSvgContent(item)"
                      class="calendar-thumbnail"
                      @click="showPreview(item)"
                      v-html="getItemSvgContent(item)"
                    ></div>
                    <div
                      v-else
                      class="w-5rem h-5rem bg-gray-100 border-round flex align-items-center justify-content-center"
                    >
                      <i class="pi pi-calendar text-2xl text-gray-400"></i>
                    </div>
                  </div>

                  <!-- Product details -->
                  <div class="item-details flex-1">
                    <div class="flex align-items-center gap-2 mb-2">
                      <h4 class="m-0">{{ getItemYear(item) }} Calendar</h4>
                      <Tag
                        :value="getProductTypeInfo(item).label"
                        :severity="getProductTypeInfo(item).severity"
                        :icon="'pi ' + getProductTypeInfo(item).icon"
                      />
                    </div>

                    <div class="flex gap-2 mb-2">
                      <Button
                        v-if="getItemSvgContent(item)"
                        label="Preview"
                        icon="pi pi-eye"
                        size="small"
                        text
                        @click="showPreview(item)"
                      />
                      <Button
                        v-if="hasPdfAccess(item)"
                        label="Download PDF"
                        icon="pi pi-download"
                        size="small"
                        text
                        @click="downloadPdf(item)"
                      />
                    </div>

                    <div
                      class="flex justify-content-between align-items-center"
                    >
                      <span class="text-gray-600"
                        >Quantity: {{ item.quantity }}</span
                      >
                      <span class="font-semibold">{{
                        formatCurrency(item.lineTotal)
                      }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Order totals -->
            <div class="order-totals pt-3">
              <div class="flex justify-content-between mb-2">
                <span>Subtotal</span>
                <span>{{ formatCurrency(order?.subtotal || 0) }}</span>
              </div>
              <div class="flex justify-content-between mb-2">
                <span>Shipping</span>
                <span>{{ formatCurrency(order?.shippingCost || 0) }}</span>
              </div>
              <div class="flex justify-content-between mb-2">
                <span>Tax</span>
                <span>{{ formatCurrency(order?.taxAmount || 0) }}</span>
              </div>
              <div
                class="flex justify-content-between pt-2 border-top-1 border-gray-300"
              >
                <span class="text-xl font-bold">Total</span>
                <span class="text-xl font-bold text-primary">
                  {{ formatCurrency(order?.totalAmount || 0) }}
                </span>
              </div>
            </div>
          </template>
        </Card>

        <!-- Addresses -->
        <Card>
          <template #title>
            <h3>Delivery Information</h3>
          </template>
          <template #content>
            <div class="grid">
              <div class="col-12 md:col-6">
                <h4 class="mb-3">Shipping Address</h4>
                <div v-if="order?.shippingAddress" class="text-gray-700">
                  {{ order.shippingAddress.firstName }}
                  {{ order.shippingAddress.lastName }}<br />
                  <span v-if="order.shippingAddress.company">
                    {{ order.shippingAddress.company }}<br />
                  </span>
                  {{ order.shippingAddress.address1 }}<br />
                  <span v-if="order.shippingAddress.address2">
                    {{ order.shippingAddress.address2 }}<br />
                  </span>
                  {{ order.shippingAddress.city }},
                  {{ order.shippingAddress.state }}
                  {{ order.shippingAddress.postalCode }}<br />
                  {{ order.shippingAddress.phone }}
                </div>
              </div>

              <div class="col-12 md:col-6">
                <h4 class="mb-3">Billing Address</h4>
                <div v-if="order?.billingAddress" class="text-gray-700">
                  <span v-if="order.billingSameAsShipping">
                    Same as shipping address
                  </span>
                  <template v-else>
                    {{ order.billingAddress.firstName }}
                    {{ order.billingAddress.lastName }}<br />
                    <span v-if="order.billingAddress.company">
                      {{ order.billingAddress.company }}<br />
                    </span>
                    {{ order.billingAddress.address1 }}<br />
                    <span v-if="order.billingAddress.address2">
                      {{ order.billingAddress.address2 }}<br />
                    </span>
                    {{ order.billingAddress.city }},
                    {{ order.billingAddress.state }}
                    {{ order.billingAddress.postalCode }}
                  </template>
                </div>
              </div>
            </div>

            <div class="mt-4">
              <h4 class="mb-3">Shipping Method</h4>
              <div v-if="order?.shippingMethod" class="text-gray-700">
                {{ order.shippingMethod.name }} -
                {{ order.shippingMethod.description }}
              </div>
            </div>
          </template>
        </Card>
      </div>

      <!-- Actions sidebar -->
      <div class="order-sidebar">
        <!-- Quick actions -->
        <Card class="mb-4">
          <template #title>
            <h3>What's Next?</h3>
          </template>
          <template #content>
            <div class="next-steps">
              <div class="flex align-items-start gap-3 mb-3">
                <i class="pi pi-envelope text-primary"></i>
                <div>
                  <div class="font-semibold mb-1">Check Your Email</div>
                  <div class="text-sm text-gray-600">
                    We've sent a confirmation with order details and tracking
                    information.
                  </div>
                </div>
              </div>

              <div class="flex align-items-start gap-3 mb-3">
                <i class="pi pi-truck text-primary"></i>
                <div>
                  <div class="font-semibold mb-1">Track Your Package</div>
                  <div class="text-sm text-gray-600">
                    You'll receive tracking information once your order ships.
                  </div>
                </div>
              </div>

              <div class="flex align-items-start gap-3">
                <i class="pi pi-at text-primary"></i>
                <div>
                  <div class="font-semibold mb-1">Need Help?</div>
                  <div class="text-sm text-gray-600">
                    Contact us at support@villagecompute.com
                  </div>
                </div>
              </div>
            </div>

            <div class="action-buttons mt-4">
              <Button
                label="Continue Shopping"
                icon="pi pi-shopping-bag"
                class="w-full mb-2"
                @click="continueShopping"
              />
              <Button
                label="Print Order"
                icon="pi pi-print"
                class="w-full p-button-outlined"
                @click="printOrder"
              />
            </div>
          </template>
        </Card>

        <!-- Create account prompt -->
        <Card v-if="!order?.userId">
          <template #title>
            <h3>Save Time Next Time!</h3>
          </template>
          <template #content>
            <p class="text-gray-600 mb-3">
              Create an account to track orders, save addresses, and checkout
              faster next time.
            </p>
            <Button
              label="Create Account"
              icon="pi pi-user-plus"
              class="w-full"
              @click="() => router.push('/register')"
            />
          </template>
        </Card>
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
.order-confirmation-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem;
}

/* Two-column grid layout */
.order-grid {
  display: flex;
  gap: 1.5rem;
}

.order-main {
  flex: 2;
  min-width: 0;
}

.order-sidebar {
  flex: 1;
  min-width: 280px;
}

.success-icon {
  animation: successPulse 1s ease-in-out;
}

@keyframes successPulse {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.order-number-box {
  border: 2px dashed var(--primary-color);
}

/* Timeline customization */
.order-timeline :deep(.p-timeline-event-connector) {
  background-color: var(--surface-200);
}

.timeline-marker {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  transition: all 0.3s ease;
}

.timeline-marker.completed {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.timeline-marker i {
  font-size: 1.2rem;
}

/* Order items */
.order-item:last-child {
  border-bottom: none !important;
  margin-bottom: 0 !important;
  padding-bottom: 0 !important;
}

/* Calendar thumbnail */
.calendar-thumbnail {
  width: 100px;
  height: 65px;
  overflow: hidden;
  border-radius: 4px;
  border: 1px solid var(--surface-200);
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

/* Estimated delivery */
.estimated-delivery {
  border: 1px solid #90caf9;
}

/* Next steps */
.next-steps i {
  font-size: 1.25rem;
  margin-top: 0.25rem;
}

/* Print styles */
@media print {
  .action-buttons,
  .breadcrumb,
  .pi-print {
    display: none !important;
  }

  .order-confirmation-container {
    padding: 0;
  }

  .card {
    box-shadow: none !important;
    border: 1px solid #ddd !important;
  }
}

/* Responsive */
@media (max-width: 991px) {
  .order-confirmation-container {
    padding: 1rem;
  }

  .order-grid {
    flex-direction: column;
  }

  .order-sidebar {
    min-width: 100%;
  }

  .order-timeline :deep(.p-timeline) {
    flex-direction: column;
  }

  .order-timeline :deep(.p-timeline-event-connector) {
    width: 2px;
    height: 100%;
  }
}
</style>
