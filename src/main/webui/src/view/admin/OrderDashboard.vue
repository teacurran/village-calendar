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
  { label: "Shipped", value: "SHIPPED" },
  { label: "Delivered", value: "DELIVERED" },
  { label: "Cancelled", value: "CANCELLED" },
  { label: "Refunded", value: "REFUNDED" },
];

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

      <Column field="id" header="Order ID" style="width: 10%">
        <template #body="{ data }">
          <span class="font-mono text-sm"
            >{{ data.id.substring(0, 8) }}...</span
          >
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

      <Column field="calendar.name" header="Calendar" style="width: 15%">
        <template #body="{ data }">
          <div>
            <div>{{ data.calendar.name }}</div>
            <div class="text-sm text-surface-600">
              Year: {{ data.calendar.year }}
            </div>
          </div>
        </template>
      </Column>

      <Column field="quantity" header="Qty" sortable style="width: 5%">
        <template #body="{ data }">
          {{ data.quantity }}
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
              v-tooltip.top="'Update Status'"
              icon="pi pi-pencil"
              size="small"
              text
              severity="secondary"
              @click="openUpdateStatus(data)"
            />
            <Button
              v-tooltip.top="'View Details'"
              icon="pi pi-eye"
              size="small"
              text
              severity="info"
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
              <span class="text-surface-600">Customer:</span>
              <span class="font-semibold ml-2">{{
                editingOrder.user.email
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Calendar:</span>
              <span class="font-semibold ml-2">{{
                editingOrder.calendar.name
              }}</span>
            </div>
            <div>
              <span class="text-surface-600">Quantity:</span>
              <span class="font-semibold ml-2">{{
                editingOrder.quantity
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
