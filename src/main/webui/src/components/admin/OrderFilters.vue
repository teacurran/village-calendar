<script setup lang="ts">
import { ref, watch } from "vue";
import {
  Dropdown,
  Calendar,
  InputText,
  Button,
  IconField,
  InputIcon,
} from "primevue";

/**
 * OrderFilters Component
 * Provides filtering controls for admin order dashboard
 */

interface Props {
  modelValue?: {
    status: string | null;
    dateRange: Date[] | null;
    search: string;
  };
  orderCount?: number;
}

interface Emits {
  (
    e: "update:modelValue",
    value: {
      status: string | null;
      dateRange: Date[] | null;
      search: string;
    },
  ): void;
  (e: "clear"): void;
  (e: "statusChange"): void;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({
    status: null,
    dateRange: null,
    search: "",
  }),
  orderCount: 0,
});

const emit = defineEmits<Emits>();

// Local filter state
const statusFilter = ref<string | null>(props.modelValue.status);
const dateRangeFilter = ref<Date[] | null>(props.modelValue.dateRange);
const searchQuery = ref<string>(props.modelValue.search);

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

/**
 * Emit updates when filters change
 */
watch([statusFilter, dateRangeFilter, searchQuery], () => {
  emit("update:modelValue", {
    status: statusFilter.value,
    dateRange: dateRangeFilter.value,
    search: searchQuery.value,
  });
});

/**
 * Handle status filter change
 */
function onStatusChange() {
  emit("statusChange");
}

/**
 * Clear all filters
 */
function handleClearFilters() {
  statusFilter.value = null;
  dateRangeFilter.value = null;
  searchQuery.value = "";
  emit("clear");
}
</script>

<template>
  <div class="order-filters">
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
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
          @change="onStatusChange"
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
          label="Clear"
          icon="pi pi-filter-slash"
          severity="secondary"
          outlined
          @click="handleClearFilters"
        />
        <span class="text-surface-600 ml-2 self-center whitespace-nowrap">
          {{ orderCount }} {{ orderCount === 1 ? "order" : "orders" }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-filters {
  margin-bottom: 1rem;
}

@media (max-width: 768px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
