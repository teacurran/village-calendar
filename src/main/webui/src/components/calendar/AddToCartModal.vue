<script setup lang="ts">
import { ref, computed } from "vue";
import Dialog from "primevue/dialog";
import Button from "primevue/button";
import RadioButton from "primevue/radiobutton";

// Props
interface Props {
  visible: boolean;
  calendarYear: number;
}

const props = defineProps<Props>();

// Emits
const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
  (e: "select", productType: "pdf" | "print"): void;
}>();

// State
const selectedOption = ref<"pdf" | "print">("print");

// Computed
const isOpen = computed({
  get: () => props.visible,
  set: (value) => emit("update:visible", value),
});

// Product options
const productOptions = [
  {
    id: "print" as const,
    name: 'Printed 35" x 23" Poster',
    price: 25.0,
    description: "Beautiful printed calendar shipped directly to your door.",
    features: [
      "Premium quality paper stock",
      "Vibrant, long-lasting colors",
      "PDF download included free",
      "Ships within 3-5 business days",
    ],
    icon: "pi-print",
    badge: "Most Popular",
  },
  {
    id: "pdf" as const,
    name: "Digital PDF Download",
    price: 5.0,
    description:
      "High-resolution PDF file ready for printing at home or any print shop.",
    features: [
      "Instant download after purchase",
      "Unlimited personal prints",
    ],
    icon: "pi-file-pdf",
  },
];

// Methods
const handleSelect = () => {
  emit("select", selectedOption.value);
  isOpen.value = false;
};

const handleClose = () => {
  isOpen.value = false;
};

const formatPrice = (price: number) => {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(price);
};
</script>

<template>
  <Dialog
    v-model:visible="isOpen"
    modal
    :closable="true"
    :draggable="false"
    class="add-to-cart-modal"
    :style="{ width: '500px', maxWidth: '95vw' }"
    @hide="handleClose"
  >
    <template #header>
      <div class="modal-header">
        <i class="pi pi-shopping-cart"></i>
        <span class="font-semibold">Add to Cart</span>
      </div>
    </template>

    <div class="modal-content">
      <p class="modal-intro">
        Choose how you'd like to receive your {{ calendarYear }} calendar:
      </p>

      <div class="product-options">
        <div
          v-for="option in productOptions"
          :key="option.id"
          class="product-option"
          :class="{ selected: selectedOption === option.id }"
          @click="selectedOption = option.id"
        >
          <div class="option-header">
            <RadioButton
              v-model="selectedOption"
              :input-id="option.id"
              :value="option.id"
              name="productType"
            />
            <div class="option-icon">
              <i :class="['pi', option.icon]"></i>
            </div>
            <div class="option-title">
              <span class="option-name">{{ option.name }}</span>
              <span v-if="option.badge" class="option-badge">{{
                option.badge
              }}</span>
            </div>
            <div class="option-price">
              {{ formatPrice(option.price) }}
              <span class="price-note">+ tax</span>
            </div>
          </div>

          <div class="option-body">
            <p class="option-description">{{ option.description }}</p>
            <ul class="option-features">
              <li v-for="feature in option.features" :key="feature">
                <i class="pi pi-check"></i>
                {{ feature }}
              </li>
            </ul>
          </div>

          <div v-if="option.id === 'print'" class="shipping-note">
            <i class="pi pi-info-circle"></i>
            Shipping calculated at checkout
          </div>
        </div>
      </div>

      <div class="tax-notice">
        <i class="pi pi-info-circle"></i>
        Sales tax will be calculated based on your location during checkout.
      </div>
    </div>

    <template #footer>
      <div class="modal-footer">
        <Button label="Cancel" text @click="handleClose" />
        <Button
          label="Add to Cart"
          icon="pi pi-shopping-cart"
          @click="handleSelect"
        />
      </div>
    </template>
  </Dialog>
</template>

<style scoped>
.modal-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.modal-header i {
  font-size: 1.25rem;
  color: var(--primary-color);
}

.modal-content {
  padding: 0.5rem 0;
}

.modal-intro {
  color: var(--text-color-secondary);
  margin-bottom: 1.25rem;
  font-size: 0.9375rem;
}

.product-options {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.product-option {
  border: 2px solid var(--surface-200);
  border-radius: 12px;
  padding: 1rem;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--surface-0);
}

.product-option:hover {
  border-color: var(--primary-300);
  background: var(--surface-50);
}

.product-option.selected {
  border-color: var(--primary-color);
  background: var(--primary-50);
}

.option-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.option-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: var(--surface-100);
  display: flex;
  align-items: center;
  justify-content: center;
}

.product-option.selected .option-icon {
  background: var(--primary-100);
  color: var(--primary-700);
}

.option-icon i {
  font-size: 1.25rem;
}

.option-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.option-name {
  font-weight: 600;
  font-size: 1rem;
  color: var(--text-color);
}

.option-badge {
  font-size: 0.6875rem;
  font-weight: 600;
  padding: 0.125rem 0.5rem;
  background: var(--primary-color);
  color: white;
  border-radius: 12px;
  text-transform: uppercase;
}

.option-price {
  text-align: right;
}

.option-price {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--primary-color);
}

.price-note {
  display: block;
  font-size: 0.6875rem;
  font-weight: 400;
  color: var(--text-color-secondary);
}

.option-body {
  padding-left: 2.5rem;
}

.option-description {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  margin-bottom: 0.5rem;
  line-height: 1.4;
}

.option-features {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.option-features li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8125rem;
  color: var(--text-color-secondary);
}

.option-features li i {
  color: var(--green-500);
  font-size: 0.75rem;
}

.shipping-note {
  margin-top: 0.75rem;
  padding: 0.5rem 0.75rem;
  background: var(--surface-100);
  border-radius: 6px;
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.product-option.selected .shipping-note {
  background: var(--primary-100);
}

.tax-notice {
  margin-top: 1rem;
  padding: 0.75rem;
  background: var(--surface-50);
  border-radius: 8px;
  font-size: 0.8125rem;
  color: var(--text-color-secondary);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.tax-notice i {
  color: var(--primary-color);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

@media (max-width: 480px) {
  .option-header {
    flex-wrap: wrap;
  }

  .option-price {
    width: 100%;
    text-align: left;
    margin-top: 0.5rem;
    padding-left: 2.5rem;
  }

  .option-body {
    padding-left: 0;
  }
}
</style>
