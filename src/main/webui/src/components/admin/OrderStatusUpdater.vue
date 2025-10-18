<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { Dropdown, InputText, Textarea, Message, Button } from "primevue";
import type { CalendarOrder } from "../../stores/orderStore";

/**
 * OrderStatusUpdater Component
 * Admin tool to update order status, add tracking number, and notes
 */

interface Props {
  order: CalendarOrder | null;
  loading?: boolean;
}

interface Emits {
  (
    e: "update",
    data: {
      status: string;
      trackingNumber?: string;
      notes?: string;
    },
  ): void;
  (e: "cancel"): void;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const emit = defineEmits<Emits>();

// Form state
const statusForm = ref({
  status: "",
  notes: "",
  trackingNumber: "",
});

const validationErrors = ref<string[]>([]);

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

/**
 * Valid status transitions based on current status
 * Enforces business logic for status changes
 */
const validTransitions = computed(() => {
  if (!props.order) return statusTransitions;

  const currentStatus = props.order.status;

  // Define valid transitions based on current status
  const transitionMap: Record<string, string[]> = {
    PENDING: ["PAID", "CANCELLED"],
    PAID: ["PROCESSING", "CANCELLED", "REFUNDED"],
    PROCESSING: ["SHIPPED", "CANCELLED"],
    SHIPPED: ["DELIVERED"],
    DELIVERED: [], // Final state
    CANCELLED: ["REFUNDED"], // Can refund a cancelled order
    REFUNDED: [], // Final state
  };

  const allowedStatuses = transitionMap[currentStatus] || [];

  return statusTransitions.filter(
    (option) =>
      option.value === currentStatus || allowedStatuses.includes(option.value),
  );
});

/**
 * Help text for status transitions
 */
const statusHelpText = computed(() => {
  if (!statusForm.value.status) return "";

  const helpMap: Record<string, string> = {
    PENDING: "Order awaiting payment confirmation",
    PAID: "Payment confirmed, ready for processing",
    PROCESSING: "Calendar is being printed and prepared",
    SHIPPED: "Order has been shipped (tracking number required)",
    DELIVERED: "Order successfully delivered to customer",
    CANCELLED: "Order cancelled (will trigger refund if paid)",
    REFUNDED: "Payment refunded to customer",
  };

  return helpMap[statusForm.value.status] || "";
});

/**
 * Initialize form when order changes
 */
watch(
  () => props.order,
  (newOrder) => {
    if (newOrder) {
      statusForm.value = {
        status: newOrder.status,
        notes: "",
        trackingNumber: newOrder.trackingNumber || "",
      };
      validationErrors.value = [];
    }
  },
  { immediate: true },
);

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
    !statusForm.value.trackingNumber?.trim()
  ) {
    validationErrors.value.push(
      "Tracking number is required when status is SHIPPED",
    );
  }

  // Warn if no notes provided for cancellation
  if (
    (statusForm.value.status === "CANCELLED" ||
      statusForm.value.status === "REFUNDED") &&
    !statusForm.value.notes?.trim()
  ) {
    validationErrors.value.push(
      "Notes are recommended when cancelling or refunding an order",
    );
  }

  return validationErrors.value.length === 0;
}

/**
 * Submit status update
 */
function handleUpdate() {
  if (!validateStatusForm()) {
    return;
  }

  emit("update", {
    status: statusForm.value.status,
    trackingNumber: statusForm.value.trackingNumber || undefined,
    notes: statusForm.value.notes || undefined,
  });
}

/**
 * Cancel update
 */
function handleCancel() {
  emit("cancel");
}
</script>

<template>
  <div v-if="order" class="order-status-updater">
    <!-- Validation Errors -->
    <Message v-if="validationErrors.length > 0" severity="warn" class="mb-4">
      <ul class="list-disc pl-4">
        <li v-for="err in validationErrors" :key="err">{{ err }}</li>
      </ul>
    </Message>

    <!-- Current Status Info -->
    <div class="bg-surface-100 p-3 rounded mb-4">
      <div class="text-sm">
        <span class="text-surface-600">Current Status:</span>
        <span class="font-semibold ml-2">{{ order.status }}</span>
      </div>
      <div v-if="order.notes" class="text-xs text-surface-600 mt-2">
        <strong>Previous Notes:</strong> {{ order.notes }}
      </div>
    </div>

    <!-- New Status -->
    <div class="field mb-4">
      <label for="order-status" class="font-semibold block mb-2">
        New Status *
      </label>
      <Dropdown
        id="order-status"
        v-model="statusForm.status"
        :options="validTransitions"
        option-label="label"
        option-value="value"
        placeholder="Select new status"
        class="w-full"
      />
      <small v-if="statusHelpText" class="text-surface-500 block mt-1">
        {{ statusHelpText }}
      </small>
    </div>

    <!-- Tracking Number (conditional) -->
    <div v-if="statusForm.status === 'SHIPPED'" class="field mb-4">
      <label for="order-tracking" class="font-semibold block mb-2">
        Tracking Number *
      </label>
      <InputText
        id="order-tracking"
        v-model="statusForm.trackingNumber"
        class="w-full"
        placeholder="Enter tracking number"
      />
      <small class="text-surface-500 block mt-1">
        Required when marking as SHIPPED
      </small>
    </div>

    <!-- Admin Notes -->
    <div class="field mb-4">
      <label for="order-notes" class="font-semibold block mb-2">
        Admin Notes
        <span
          v-if="
            statusForm.status === 'CANCELLED' ||
            statusForm.status === 'REFUNDED'
          "
        >
          *
        </span>
      </label>
      <Textarea
        id="order-notes"
        v-model="statusForm.notes"
        class="w-full"
        rows="4"
        placeholder="Add notes about this status update"
      />
      <small class="text-surface-500 block mt-1">
        {{
          statusForm.status === "CANCELLED" || statusForm.status === "REFUNDED"
            ? "Please provide a reason for cancellation/refund"
            : "These notes will be appended to order history (optional)"
        }}
      </small>
    </div>

    <!-- Actions -->
    <div class="flex justify-end gap-2">
      <Button
        label="Cancel"
        icon="pi pi-times"
        severity="secondary"
        outlined
        :disabled="loading"
        @click="handleCancel"
      />
      <Button
        label="Update Status"
        icon="pi pi-check"
        :loading="loading"
        @click="handleUpdate"
      />
    </div>
  </div>
</template>

<style scoped>
.order-status-updater {
  padding: 0.5rem;
}

.field {
  margin-bottom: 1rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
}
</style>
