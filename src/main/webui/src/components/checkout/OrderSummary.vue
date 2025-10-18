<script setup lang="ts">
import { computed } from "vue";
import Card from "primevue/card";
import Divider from "primevue/divider";
import InputText from "primevue/inputtext";
import Button from "primevue/button";

interface CalendarItem {
  id: string;
  name: string;
  year: number;
  templateName?: string;
  previewSvg?: string;
  quantity: number;
  unitPrice: number;
}

interface Props {
  calendar?: CalendarItem;
  quantity?: number;
  subtotal?: number;
  shipping?: number;
  tax?: number;
  total?: number;
  showDiscountCode?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  quantity: 1,
  subtotal: 0,
  shipping: 0,
  tax: 0,
  total: 0,
  showDiscountCode: true,
});

const formatCurrency = (amount: number) => {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(amount);
};

const itemTotal = computed(() => {
  return props.quantity * (props.calendar?.unitPrice || 0);
});
</script>

<template>
  <Card class="order-summary">
    <template #title>
      <div class="summary-title">
        <i class="pi pi-shopping-cart"></i>
        Order Summary
      </div>
    </template>

    <template #content>
      <!-- Calendar Item -->
      <div v-if="calendar" class="calendar-item">
        <div class="item-preview">
          <div
            v-if="calendar.previewSvg"
            class="preview-image"
            v-html="calendar.previewSvg"
          ></div>
          <div v-else class="preview-placeholder">
            <i class="pi pi-calendar"></i>
            <div class="calendar-year">{{ calendar.year }}</div>
          </div>
          <span class="item-quantity">{{ quantity }}</span>
        </div>

        <div class="item-details">
          <div class="item-name">{{ calendar.name }}</div>
          <div class="item-year">
            <i class="pi pi-calendar"></i>
            {{ calendar.year }} Calendar
          </div>
          <div v-if="calendar.templateName" class="item-template">
            Template: {{ calendar.templateName }}
          </div>
        </div>

        <div class="item-price">
          {{ formatCurrency(itemTotal) }}
        </div>
      </div>

      <Divider />

      <!-- Discount Code -->
      <div v-if="showDiscountCode" class="discount-section">
        <div class="discount-input">
          <InputText placeholder="Discount code" class="w-full" disabled />
          <Button label="Apply" outlined disabled />
        </div>
        <p class="discount-note">Discount codes coming soon!</p>
      </div>

      <Divider v-if="showDiscountCode" />

      <!-- Price Breakdown -->
      <div class="price-breakdown">
        <div class="price-row">
          <span>Subtotal</span>
          <span>{{ formatCurrency(subtotal) }}</span>
        </div>
        <div class="price-row">
          <span>Shipping</span>
          <span v-if="shipping > 0">{{ formatCurrency(shipping) }}</span>
          <span v-else class="text-muted">Calculated at checkout</span>
        </div>
        <div class="price-row">
          <span>Tax</span>
          <span v-if="tax > 0">{{ formatCurrency(tax) }}</span>
          <span v-else class="text-muted">Calculated at checkout</span>
        </div>

        <Divider />

        <div class="price-row total-row">
          <span class="total-label">Total</span>
          <span class="total-amount">
            <small class="currency">USD</small>
            {{ formatCurrency(total) }}
          </span>
        </div>
      </div>
    </template>
  </Card>
</template>

<style scoped>
.order-summary {
  position: sticky;
  top: 2rem;
}

.summary-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
}

.summary-title i {
  color: #3b82f6;
}

/* Calendar Item */
.calendar-item {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
}

.item-preview {
  position: relative;
  flex-shrink: 0;
}

.preview-image,
.preview-placeholder {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
}

.preview-placeholder {
  flex-direction: column;
  color: #3b82f6;
}

.preview-placeholder i {
  font-size: 1.5rem;
}

.calendar-year {
  font-size: 1.25rem;
  font-weight: bold;
  margin-top: 0.25rem;
}

.item-quantity {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #3b82f6;
  color: white;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.875rem;
  font-weight: 600;
  border: 2px solid white;
}

.item-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.item-name {
  font-weight: 600;
  color: #1f2937;
  font-size: 1rem;
}

.item-year,
.item-template {
  font-size: 0.875rem;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.item-year i {
  font-size: 0.75rem;
}

.item-price {
  font-weight: 600;
  color: #1f2937;
  font-size: 1.125rem;
}

/* Discount Section */
.discount-section {
  margin: 1rem 0;
}

.discount-input {
  display: flex;
  gap: 0.5rem;
}

.discount-note {
  font-size: 0.75rem;
  color: #9ca3af;
  margin-top: 0.5rem;
  font-style: italic;
}

/* Price Breakdown */
.price-breakdown {
  margin-top: 1rem;
}

.price-row {
  display: flex;
  justify-content: space-between;
  padding: 0.75rem 0;
  font-size: 0.875rem;
  color: #4b5563;
}

.text-muted {
  color: #9ca3af;
  font-style: italic;
}

.total-row {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
  padding-top: 1rem;
}

.total-label {
  font-size: 1.125rem;
}

.total-amount {
  display: flex;
  align-items: baseline;
  gap: 0.25rem;
}

.currency {
  font-size: 0.75rem;
  color: #6b7280;
  font-weight: 400;
}

/* Responsive */
@media (max-width: 768px) {
  .order-summary {
    position: static;
  }

  .calendar-item {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .item-details {
    align-items: center;
  }
}
</style>
