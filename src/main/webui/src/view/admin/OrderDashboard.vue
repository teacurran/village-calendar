<script setup lang="ts">
import { onMounted, ref, computed } from "vue";
import {
  Breadcrumb,
  Button,
  Column,
  DataTable,
  Dialog,
  InputText,
  Textarea,
  Dropdown,
  Message,
  Tag,
  Calendar,
  IconField,
  InputIcon,
} from "primevue";
import { useToast } from "primevue/usetoast";
import { useOrderStore } from "../../stores/orderStore";
import { useAuthStore } from "../../stores/authStore";
import type { CalendarOrder } from "../../stores/orderStore";
import {
  formatCurrency,
  getOrderStatusSeverity,
  formatOrderStatus,
} from "../../services/orderService";

const toast = useToast();
const orderStore = useOrderStore();
const authStore = useAuthStore();

// Breadcrumb
const homeBreadcrumb = ref({
  icon: "pi pi-home",
  url: "/",
});

const breadCrumbs = ref([{ label: "Admin" }, { label: "Order Dashboard" }]);

// Loading state
const loading = computed(() => orderStore.loading);
const error = computed(() => orderStore.error);
const orders = computed(() => orderStore.orders);

// Filters
const statusFilter = ref<string | null>(null);
const dateRangeFilter = ref<Date[] | null>(null);
const searchQuery = ref<string>("");

const statusOptions = [
  { label: "All Orders", value: null },
  { label: "Pending", value: "PENDING" },
  { label: "Paid", value: "PAID" },
  { label: "Processing", value: "PROCESSING" },
  { label: "Printed", value: "PRINTED" },
  { label: "Shipped", value: "SHIPPED" },
  { label: "Delivered", value: "DELIVERED" },
  { label: "Cancelled", value: "CANCELLED" },
  { label: "Refunded", value: "REFUNDED" },
];

// Update status dialog
const updateStatusDialog = ref(false);
const editingOrder = ref<CalendarOrder | null>(null);
const statusForm = ref({
  status: "",
  notes: "",
  trackingNumber: "",
});

// Available status transitions
const statusTransitions = [
  { label: "Pending", value: "PENDING" },
  { label: "Paid", value: "PAID" },
  { label: "Processing", value: "PROCESSING" },
  { label: "Printed", value: "PRINTED" },
  { label: "Shipped", value: "SHIPPED" },
  { label: "Delivered", value: "DELIVERED" },
  { label: "Cancelled", value: "CANCELLED" },
  { label: "Refunded", value: "REFUNDED" },
];

// Order details dialog
const detailsDialog = ref(false);
const viewingOrder = ref<CalendarOrder | null>(null);
const previewDialogVisible = ref(false);
const previewSvgContent = ref("");

// Validation errors
const validationErrors = ref<string[]>([]);

/**
 * Load orders with status filter
 */
async function loadOrders() {
  if (!authStore.token) {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Not authenticated",
      life: 5000,
    });
    return;
  }

  await orderStore.loadOrders(authStore.token, statusFilter.value || undefined);
}

/**
 * Open dialog to update order status
 */
function openUpdateStatus(order: CalendarOrder) {
  editingOrder.value = order;
  statusForm.value = {
    status: order.status,
    notes: "",
    trackingNumber: order.trackingNumber || "",
  };
  validationErrors.value = [];
  updateStatusDialog.value = true;
}

/**
 * Open order details dialog
 */
function openDetails(order: CalendarOrder) {
  viewingOrder.value = order;
  detailsDialog.value = true;
}

/**
 * Get first PDF item from order for download
 */
function getFirstPdfItem(order: CalendarOrder | null): any | null {
  if (!order?.items || order.items.length === 0) return null;
  return (
    order.items.find((item) => item.productType === "PDF") || order.items[0]
  );
}

/**
 * Get calendar SVG content from order
 * Checks calendar.generatedSvg first, then item configurations
 */
function getCalendarSvg(order: CalendarOrder | null): string | null {
  if (!order) return null;

  // Try calendar.generatedSvg first
  if (order.calendar?.generatedSvg) {
    return order.calendar.generatedSvg;
  }

  // Try to find SVG in item configurations
  if (order.items && order.items.length > 0) {
    for (const item of order.items) {
      const config = item.configuration;
      if (config) {
        // Configuration might be a string or object
        let configObj = config;
        if (typeof config === "string") {
          try {
            configObj = JSON.parse(config);
          } catch {
            continue;
          }
        }
        if (configObj?.generatedSvg) {
          return configObj.generatedSvg;
        }
      }
    }
  }

  return null;
}

/**
 * Show calendar preview in modal
 */
function showCalendarPreview(order: CalendarOrder) {
  const svg = getCalendarSvg(order);
  if (svg) {
    previewSvgContent.value = svg;
    previewDialogVisible.value = true;
  }
}

/**
 * Get customer name (first initial + last name) for filename
 */
function getCustomerNameForFilename(order: CalendarOrder): string {
  // Try shipping address first
  const addr = order.shippingAddress;
  if (addr?.firstName && addr?.lastName) {
    const firstInitial = addr.firstName.charAt(0).toUpperCase();
    return `${firstInitial}${addr.lastName}`;
  }

  // Try user display name
  if (order.user?.displayName) {
    const parts = order.user.displayName.split(" ");
    if (parts.length > 1) {
      const firstInitial = parts[0].charAt(0).toUpperCase();
      const lastName = parts[parts.length - 1];
      return `${firstInitial}${lastName}`;
    }
    return parts[0];
  }

  // Fallback to email prefix
  if (order.customerEmail || order.user?.email) {
    return (order.customerEmail || order.user?.email || "").split("@")[0];
  }

  return "customer";
}

/**
 * Download PDF for specific order item using secure backend endpoint
 */
async function downloadPdf(order: CalendarOrder, item: any) {
  const orderNumber = order.orderNumber;
  const itemId = item?.id;
  if (!orderNumber) {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Order number not available",
      life: 3000,
    });
    return;
  }
  if (!itemId) {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Item ID not available",
      life: 3000,
    });
    return;
  }

  try {
    // Use secure order-based PDF download endpoint
    const response = await fetch(
      `/api/orders/${orderNumber}/items/${itemId}/pdf`,
      {
        method: "GET",
        headers: {
          Accept: "application/pdf",
        },
      },
    );

    if (!response.ok) {
      throw new Error(`Failed to generate PDF: ${response.status}`);
    }

    // Get filename from backend response headers
    const contentDisposition = response.headers.get("Content-Disposition");
    let filename = `calendar-${orderNumber}-item${itemId}.pdf`;
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename="([^"]+)"/);
      if (filenameMatch) {
        filename = filenameMatch[1];
      }
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

    toast.add({
      severity: "success",
      summary: "Downloaded",
      detail: `PDF saved as ${filename}`,
      life: 3000,
    });
  } catch (error) {
    console.error("Failed to generate PDF:", error);
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Failed to generate PDF. Please try again.",
      life: 3000,
    });
  }
}

/**
 * Validate status update form
 */
function validateStatusForm(): boolean {
  validationErrors.value = [];

  if (!statusForm.value.status) {
    validationErrors.value.push("Status is required");
  }

  // Tracking number required for SHIPPED status
  if (
    statusForm.value.status === "SHIPPED" &&
    !statusForm.value.trackingNumber
  ) {
    validationErrors.value.push(
      "Tracking number is required when status is SHIPPED",
    );
  }

  return validationErrors.value.length === 0;
}

/**
 * Update order status
 */
async function updateStatus() {
  if (!validateStatusForm() || !editingOrder.value || !authStore.token) {
    return;
  }

  const input = {
    status: statusForm.value.status,
    notes: statusForm.value.notes || undefined,
    trackingNumber: statusForm.value.trackingNumber || undefined,
  };

  const result = await orderStore.updateOrderStatus(
    editingOrder.value.id,
    input,
    authStore.token,
  );

  if (result) {
    toast.add({
      severity: "success",
      summary: "Success",
      detail: "Order status updated successfully",
      life: 3000,
    });
    updateStatusDialog.value = false;
  } else {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: orderStore.error || "Failed to update order status",
      life: 5000,
    });
  }
}

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
 * Filtered orders based on all active filters
 */
const filteredOrders = computed(() => {
  let result = orders.value;

  // Apply search filter (order ID or customer email)
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(
      (order) =>
        order.id.toLowerCase().includes(query) ||
        order.user.email.toLowerCase().includes(query) ||
        order.user.displayName?.toLowerCase().includes(query) ||
        (order.trackingNumber &&
          order.trackingNumber.toLowerCase().includes(query)),
    );
  }

  // Apply date range filter
  if (dateRangeFilter.value && dateRangeFilter.value.length === 2) {
    const [startDate, endDate] = dateRangeFilter.value;
    if (startDate && endDate) {
      result = result.filter((order) => {
        const orderDate = new Date(order.created);
        return orderDate >= startDate && orderDate <= endDate;
      });
    }
  }

  return result;
});

/**
 * Get order count for current filter
 */
const orderCount = computed(() => {
  return filteredOrders.value.length;
});

/**
 * Watch status filter changes
 */
async function onStatusFilterChange() {
  await loadOrders();
}

/**
 * Clear all filters
 */
function clearFilters() {
  statusFilter.value = null;
  dateRangeFilter.value = null;
  searchQuery.value = "";
  loadOrders();
}

onMounted(async () => {
  await loadOrders();
});
</script>

<template>
  <Breadcrumb :home="homeBreadcrumb" :model="breadCrumbs" class="mb-4" />

  <div class="order-dashboard-page">
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-3xl font-bold">Order Dashboard</h1>
      <Button icon="pi pi-refresh" label="Refresh" @click="loadOrders" />
    </div>

    <!-- Error Display -->
    <Message v-if="error" severity="error" class="mb-4">{{ error }}</Message>

    <!-- Filters Section -->
    <div class="mb-4 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      <!-- Status Filter -->
      <div>
        <label for="status-filter" class="block font-semibold mb-2"
          >Status</label
        >
        <Dropdown
          id="status-filter"
          v-model="statusFilter"
          :options="statusOptions"
          option-label="label"
          option-value="value"
          placeholder="All Orders"
          class="w-full"
          @change="onStatusFilterChange"
        />
      </div>

      <!-- Date Range Filter -->
      <div>
        <label for="date-range" class="block font-semibold mb-2"
          >Date Range</label
        >
        <Calendar
          id="date-range"
          v-model="dateRangeFilter"
          selection-mode="range"
          :manual-input="false"
          date-format="mm/dd/yy"
          placeholder="Select date range"
          class="w-full"
          show-icon
          icon-display="input"
        />
      </div>

      <!-- Search Filter -->
      <div>
        <label for="search" class="block font-semibold mb-2">Search</label>
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText
            id="search"
            v-model="searchQuery"
            placeholder="Order ID, email, tracking..."
            class="w-full"
          />
        </IconField>
      </div>

      <!-- Filter Actions -->
      <div class="flex items-end gap-2">
        <Button
          label="Clear Filters"
          icon="pi pi-filter-slash"
          severity="secondary"
          outlined
          @click="clearFilters"
        />
        <span class="text-surface-600 ml-2 self-center"
          >{{ orderCount }} orders</span
        >
      </div>
    </div>

    <!-- Orders DataTable -->
    <DataTable
      :value="filteredOrders"
      :loading="loading"
      striped-rows
      paginator
      :rows="25"
      :rows-per-page-options="[10, 25, 50, 100]"
      table-style="min-width: 50rem"
    >
      <template #empty>
        <div class="text-center py-4">No orders found</div>
      </template>

      <Column field="orderNumber" header="Order" style="width: 12%">
        <template #body="{ data }">
          <div>
            <span class="font-mono font-semibold">{{
              data.orderNumber || data.id.substring(0, 8)
            }}</span>
          </div>
        </template>
      </Column>

      <Column field="user.email" header="Customer" sortable style="width: 15%">
        <template #body="{ data }">
          <div>
            <div class="font-semibold">
              {{ data.user.displayName || "Unknown" }}
            </div>
            <div class="text-sm text-surface-600">{{ data.user.email }}</div>
          </div>
        </template>
      </Column>

      <Column header="Items" style="width: 20%">
        <template #body="{ data }">
          <div v-if="data.items && data.items.length > 0">
            <div v-for="item in data.items" :key="item.id" class="mb-1">
              <div class="flex items-center gap-2">
                <Tag
                  :severity="item.productType === 'PDF' ? 'info' : 'success'"
                  :value="item.productType"
                  class="text-xs"
                />
                <span>{{ item.productName || "Calendar" }}</span>
                <span class="text-surface-600">x{{ item.quantity }}</span>
              </div>
            </div>
          </div>
          <div v-else-if="data.calendar">
            <div>{{ data.calendar.name }}</div>
            <div class="text-sm text-surface-600">
              Year: {{ data.calendar.year }} (x{{ data.quantity }})
            </div>
          </div>
          <div v-else class="text-surface-500">-</div>
        </template>
      </Column>

      <Column field="totalPrice" header="Total" sortable style="width: 10%">
        <template #body="{ data }">
          {{ formatCurrency(data.totalPrice) }}
        </template>
      </Column>

      <Column field="status" header="Status" sortable style="width: 10%">
        <template #body="{ data }">
          <Tag
            :severity="getOrderStatusSeverity(data.status)"
            :value="formatOrderStatus(data.status)"
          />
        </template>
      </Column>

      <Column field="trackingNumber" header="Tracking" style="width: 10%">
        <template #body="{ data }">
          {{ data.trackingNumber || "-" }}
        </template>
      </Column>

      <Column field="created" header="Order Date" sortable style="width: 12%">
        <template #body="{ data }">
          {{ formatDate(data.created) }}
        </template>
      </Column>

      <Column header="Actions" style="width: 13%">
        <template #body="{ data }">
          <div class="flex gap-2">
            <Button
              v-tooltip.top="'View Details'"
              icon="pi pi-eye"
              size="small"
              text
              severity="info"
              @click="openDetails(data)"
            />
            <Button
              v-tooltip.top="'Update Status'"
              icon="pi pi-pencil"
              size="small"
              text
              severity="secondary"
              @click="openUpdateStatus(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <!-- Update Status Dialog -->
    <Dialog
      v-model:visible="updateStatusDialog"
      :style="{ width: '700px' }"
      :header="`Update Order Status - ${editingOrder ? editingOrder.id.substring(0, 8) : ''}`"
      :modal="true"
    >
      <div v-if="editingOrder" class="flex flex-col gap-4">
        <!-- Validation Errors -->
        <Message v-if="validationErrors.length > 0" severity="error">
          <ul class="list-disc pl-4">
            <li v-for="err in validationErrors" :key="err">{{ err }}</li>
          </ul>
        </Message>

        <!-- Order Details -->
        <div class="bg-surface-100 p-4 rounded">
          <h3 class="font-semibold mb-2">Order Details</h3>
          <div class="grid grid-cols-2 gap-2 text-sm">
            <div>
              <span class="text-surface-600">Order Number:</span>
              <span class="font-semibold ml-2 font-mono">{{
                editingOrder.orderNumber || editingOrder.id.substring(0, 8)
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Customer:</span>
              <span class="font-semibold ml-2">{{
                editingOrder.customerEmail || editingOrder.user?.email
              }}</span>
            </div>
            <div class="col-span-2">
              <span class="text-surface-600">Items:</span>
              <div
                v-if="editingOrder.items && editingOrder.items.length > 0"
                class="ml-2 mt-1"
              >
                <div
                  v-for="item in editingOrder.items"
                  :key="item.id"
                  class="flex items-center gap-2 mb-1"
                >
                  <Tag
                    :severity="item.productType === 'PDF' ? 'info' : 'success'"
                    :value="item.productType"
                    class="text-xs"
                  />
                  <span class="font-semibold">{{
                    item.productName || "Calendar"
                  }}</span>
                  <span>x{{ item.quantity }}</span>
                  <span class="text-surface-600"
                    >@ {{ formatCurrency(item.unitPrice) }}</span
                  >
                </div>
              </div>
              <div v-else-if="editingOrder.calendar" class="font-semibold ml-2">
                {{ editingOrder.calendar.name }} x{{ editingOrder.quantity }}
              </div>
            </div>
            <div>
              <span class="text-surface-600">Subtotal:</span>
              <span class="font-semibold ml-2">{{
                formatCurrency(editingOrder.subtotal || editingOrder.totalPrice)
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Shipping:</span>
              <span class="font-semibold ml-2">{{
                formatCurrency(editingOrder.shippingCost || 0)
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Tax:</span>
              <span class="font-semibold ml-2">{{
                formatCurrency(editingOrder.taxAmount || 0)
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Total:</span>
              <span class="font-semibold ml-2">{{
                formatCurrency(editingOrder.totalPrice)
              }}</span>
            </div>
            <div class="col-span-2">
              <span class="text-surface-600">Shipping Address:</span>
              <div class="font-semibold ml-2">
                {{ formatAddress(editingOrder.shippingAddress) }}
              </div>
            </div>
            <div>
              <span class="text-surface-600">Paid At:</span>
              <span class="ml-2">{{
                formatDateTime(editingOrder.paidAt)
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Current Status:</span>
              <Tag
                class="ml-2"
                :severity="getOrderStatusSeverity(editingOrder.status)"
                :value="formatOrderStatus(editingOrder.status)"
              />
            </div>
          </div>
        </div>

        <!-- Existing Notes -->
        <div v-if="editingOrder.notes" class="bg-surface-50 p-3 rounded">
          <h4 class="font-semibold text-sm mb-1">Previous Notes:</h4>
          <p class="text-sm whitespace-pre-wrap">{{ editingOrder.notes }}</p>
        </div>

        <!-- New Status -->
        <div class="field">
          <label for="order-status" class="font-semibold">New Status *</label>
          <Dropdown
            id="order-status"
            v-model="statusForm.status"
            :options="statusTransitions"
            option-label="label"
            option-value="value"
            placeholder="Select new status"
            class="w-full"
          />
        </div>

        <!-- Tracking Number (conditional) -->
        <div v-if="statusForm.status === 'SHIPPED'" class="field">
          <label for="order-tracking" class="font-semibold"
            >Tracking Number *</label
          >
          <InputText
            id="order-tracking"
            v-model="statusForm.trackingNumber"
            class="w-full"
            placeholder="Enter tracking number"
          />
          <small class="text-surface-500"
            >Required when marking as SHIPPED</small
          >
        </div>

        <!-- Admin Notes -->
        <div class="field">
          <label for="order-notes" class="font-semibold">Admin Notes</label>
          <Textarea
            id="order-notes"
            v-model="statusForm.notes"
            class="w-full"
            rows="4"
            placeholder="Add notes about this status update (optional)"
          />
          <small class="text-surface-500"
            >These notes will be appended to order history</small
          >
        </div>
      </div>

      <template #footer>
        <Button
          label="Cancel"
          icon="pi pi-times"
          class="p-button-text"
          @click="updateStatusDialog = false"
        />
        <Button
          label="Update Status"
          icon="pi pi-check"
          @click="updateStatus"
        />
      </template>
    </Dialog>

    <!-- Order Details Dialog -->
    <Dialog
      v-model:visible="detailsDialog"
      :style="{ width: '800px' }"
      :header="`Order Details - ${viewingOrder?.orderNumber || viewingOrder?.id?.substring(0, 8) || ''}`"
      :modal="true"
    >
      <div v-if="viewingOrder" class="order-details-content">
        <!-- Calendar Preview Section -->
        <div class="bg-surface-100 p-4 rounded-lg mb-4">
          <h3 class="font-semibold text-lg mb-3">Calendar</h3>
          <div class="flex gap-4">
            <!-- Thumbnail -->
            <div class="calendar-thumbnail-container">
              <div
                v-if="getCalendarSvg(viewingOrder)"
                class="calendar-thumbnail"
                @click="showCalendarPreview(viewingOrder)"
                v-html="getCalendarSvg(viewingOrder)"
              ></div>
              <div v-else class="calendar-thumbnail-placeholder">
                <i class="pi pi-calendar text-2xl text-surface-400"></i>
              </div>
            </div>

            <!-- Calendar Details & Actions -->
            <div class="flex-1">
              <div class="grid grid-cols-2 gap-2 text-sm mb-3">
                <div>
                  <span class="text-surface-600">Name:</span>
                  <span class="font-semibold ml-2">{{
                    viewingOrder.calendar?.name || "Calendar"
                  }}</span>
                </div>
                <div>
                  <span class="text-surface-600">Year:</span>
                  <span class="font-semibold ml-2">{{
                    viewingOrder.calendar?.year || "-"
                  }}</span>
                </div>
              </div>
              <div class="flex gap-2">
                <Button
                  v-if="getCalendarSvg(viewingOrder)"
                  label="Preview"
                  icon="pi pi-eye"
                  size="small"
                  outlined
                  @click="showCalendarPreview(viewingOrder)"
                />
                <Button
                  v-if="getFirstPdfItem(viewingOrder)"
                  label="Download PDF"
                  icon="pi pi-download"
                  size="small"
                  @click="
                    downloadPdf(viewingOrder, getFirstPdfItem(viewingOrder))
                  "
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Order Information -->
        <div class="bg-surface-50 p-4 rounded-lg mb-4">
          <h3 class="font-semibold text-lg mb-3">Order Information</h3>
          <div class="grid grid-cols-2 gap-3 text-sm">
            <div>
              <span class="text-surface-600">Order Number:</span>
              <span class="font-mono font-semibold ml-2">{{
                viewingOrder.orderNumber || viewingOrder.id.substring(0, 8)
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Status:</span>
              <Tag
                class="ml-2"
                :severity="getOrderStatusSeverity(viewingOrder.status)"
                :value="formatOrderStatus(viewingOrder.status)"
              />
            </div>
            <div>
              <span class="text-surface-600">Created:</span>
              <span class="ml-2">{{
                formatDateTime(viewingOrder.created)
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Paid At:</span>
              <span class="ml-2">{{
                formatDateTime(viewingOrder.paidAt)
              }}</span>
            </div>
          </div>
        </div>

        <!-- Customer Information -->
        <div class="bg-surface-50 p-4 rounded-lg mb-4">
          <h3 class="font-semibold text-lg mb-3">Customer</h3>
          <div class="grid grid-cols-2 gap-3 text-sm">
            <div>
              <span class="text-surface-600">Name:</span>
              <span class="font-semibold ml-2">{{
                viewingOrder.user?.displayName || "-"
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Email:</span>
              <span class="ml-2">{{
                viewingOrder.customerEmail || viewingOrder.user?.email
              }}</span>
            </div>
            <div class="col-span-2">
              <span class="text-surface-600">Shipping Address:</span>
              <div class="font-semibold ml-2 mt-1">
                {{ formatAddress(viewingOrder.shippingAddress) }}
              </div>
            </div>
          </div>
        </div>

        <!-- Order Items & Totals -->
        <div class="bg-surface-50 p-4 rounded-lg mb-4">
          <h3 class="font-semibold text-lg mb-3">Order Items</h3>
          <div
            v-if="viewingOrder.items && viewingOrder.items.length > 0"
            class="mb-3"
          >
            <div
              v-for="item in viewingOrder.items"
              :key="item.id"
              class="flex items-center gap-2 mb-2 pb-2 border-bottom-1 border-surface-200"
            >
              <Tag
                :severity="item.productType === 'PDF' ? 'info' : 'success'"
                :value="item.productType"
                class="text-xs"
              />
              <span class="font-semibold flex-1">{{
                item.productName || "Calendar"
              }}</span>
              <span>x{{ item.quantity }}</span>
              <span class="text-surface-600"
                >@ {{ formatCurrency(item.unitPrice) }}</span
              >
              <span class="font-semibold">{{
                formatCurrency(item.lineTotal)
              }}</span>
              <Button
                v-tooltip.top="'Download PDF'"
                icon="pi pi-download"
                size="small"
                text
                severity="success"
                @click="downloadPdf(viewingOrder, item)"
              />
            </div>
          </div>
          <div
            class="grid grid-cols-2 gap-2 text-sm border-top-1 border-surface-300 pt-3"
          >
            <div>
              <span class="text-surface-600">Subtotal:</span>
              <span class="font-semibold ml-2">{{
                formatCurrency(viewingOrder.subtotal || viewingOrder.totalPrice)
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Shipping:</span>
              <span class="font-semibold ml-2">{{
                formatCurrency(viewingOrder.shippingCost || 0)
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Tax:</span>
              <span class="font-semibold ml-2">{{
                formatCurrency(viewingOrder.taxAmount || 0)
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Total:</span>
              <span class="font-bold text-lg ml-2">{{
                formatCurrency(viewingOrder.totalPrice)
              }}</span>
            </div>
          </div>
        </div>

        <!-- Shipping Info (if shipped) -->
        <div
          v-if="viewingOrder.trackingNumber || viewingOrder.shippedAt"
          class="bg-surface-50 p-4 rounded-lg mb-4"
        >
          <h3 class="font-semibold text-lg mb-3">Shipping</h3>
          <div class="grid grid-cols-2 gap-3 text-sm">
            <div v-if="viewingOrder.shippedAt">
              <span class="text-surface-600">Shipped At:</span>
              <span class="ml-2">{{
                formatDateTime(viewingOrder.shippedAt)
              }}</span>
            </div>
            <div v-if="viewingOrder.trackingNumber">
              <span class="text-surface-600">Tracking:</span>
              <span class="font-mono ml-2">{{
                viewingOrder.trackingNumber
              }}</span>
            </div>
          </div>
        </div>

        <!-- Notes -->
        <div v-if="viewingOrder.notes" class="bg-surface-50 p-4 rounded-lg">
          <h3 class="font-semibold text-lg mb-3">Notes</h3>
          <p class="text-sm whitespace-pre-wrap">{{ viewingOrder.notes }}</p>
        </div>
      </div>

      <template #footer>
        <Button
          label="Close"
          icon="pi pi-times"
          class="p-button-text"
          @click="detailsDialog = false"
        />
      </template>
    </Dialog>

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
.order-dashboard-page {
  margin: 0 auto;
  width: 95%;
  max-width: 1600px;
  padding: 2rem;
  background-color: white;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1);
}

.field {
  margin-bottom: 1rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
}

/* Order details dialog */
.order-details-content {
  max-height: 70vh;
  overflow-y: auto;
}

/* Calendar thumbnail */
.calendar-thumbnail-container {
  flex-shrink: 0;
}

.calendar-thumbnail {
  width: 150px;
  height: 97px;
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
  width: 150px;
  height: 97px;
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

@media (max-width: 768px) {
  .order-dashboard-page {
    width: 100%;
    padding: 1rem;
  }

  .grid-cols-2 {
    grid-template-columns: 1fr;
  }
}
</style>
