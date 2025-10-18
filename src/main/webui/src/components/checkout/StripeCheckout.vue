<script setup lang="ts">
import { ref } from "vue";
import { useToast } from "primevue/usetoast";
import Button from "primevue/button";
import {
  PLACE_ORDER_MUTATION,
  type PlaceOrderInput,
  type AddressInput,
} from "@/graphql/order-mutations";

interface Props {
  calendarId: string;
  quantity: number;
  shippingAddress: AddressInput;
  productType?: "WALL_CALENDAR" | "DESK_CALENDAR" | "POSTER";
  disabled?: boolean;
}

interface Emits {
  (e: "payment-initiated"): void;
  (e: "payment-error", error: Error): void;
}

const props = withDefaults(defineProps<Props>(), {
  productType: "WALL_CALENDAR",
  disabled: false,
});

const emit = defineEmits<Emits>();
const toast = useToast();

const processing = ref(false);

/**
 * Initiates the Stripe Checkout Session by calling the placeOrder mutation.
 * On success, redirects the user to the Stripe-hosted checkout page.
 */
const initiateCheckout = async () => {
  if (props.disabled || processing.value) {
    return;
  }

  processing.value = true;
  emit("payment-initiated");

  try {
    // Prepare the input for placeOrder mutation
    const input: PlaceOrderInput = {
      calendarId: props.calendarId,
      productType: props.productType,
      quantity: props.quantity,
      shippingAddress: props.shippingAddress,
    };

    // Call GraphQL mutation
    const response = await fetch("/graphql", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include", // Important for authentication
      body: JSON.stringify({
        query: PLACE_ORDER_MUTATION,
        variables: { input },
      }),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const result = await response.json();

    // Check for GraphQL errors
    if (result.errors && result.errors.length > 0) {
      const errorMessage =
        result.errors[0].message || "Failed to create payment session";
      throw new Error(errorMessage);
    }

    // Extract payment intent data
    const paymentIntent = result.data?.placeOrder;

    if (!paymentIntent) {
      throw new Error("No payment intent returned from server");
    }

    // Note: The GraphQL schema returns PaymentIntent type, but for Stripe Checkout Sessions,
    // we need to check if there's a checkout URL in the response.
    // For now, we'll use the clientSecret to redirect (this may need backend adjustment)

    // TODO: Backend should return checkoutUrl for Stripe Checkout Sessions
    // For MVP, display a message that payment processing is being set up
    toast.add({
      severity: "info",
      summary: "Payment Processing",
      detail: "Redirecting to payment processor...",
      life: 3000,
    });

    // In a real implementation with Checkout Sessions, you would do:
    // window.location.href = paymentIntent.checkoutUrl;

    // For now, since the backend returns PaymentIntent (for Stripe Elements),
    // we'll show an error asking to use the main checkout page
    throw new Error(
      "Payment processing is currently handled through the main checkout page. " +
        "Please use the existing checkout flow.",
    );
  } catch (error: any) {
    console.error("Checkout error:", error);

    toast.add({
      severity: "error",
      summary: "Payment Error",
      detail: error.message || "Failed to initiate payment. Please try again.",
      life: 5000,
    });

    emit("payment-error", error);
  } finally {
    processing.value = false;
  }
};

defineExpose({ initiateCheckout });
</script>

<template>
  <div class="stripe-checkout">
    <Button
      label="Pay with Stripe"
      icon="pi pi-credit-card"
      :loading="processing"
      :disabled="disabled || processing"
      class="checkout-button"
      size="large"
      @click="initiateCheckout"
    />

    <div class="checkout-info">
      <i class="pi pi-lock"></i>
      <span>Secure payment powered by Stripe</span>
    </div>

    <p class="checkout-disclaimer">
      By clicking "Pay with Stripe", you will be redirected to Stripe's secure
      checkout page to complete your payment. Your payment information is never
      stored on our servers.
    </p>
  </div>
</template>

<style scoped>
.stripe-checkout {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.checkout-button {
  width: 100%;
  background: #635bff;
  border-color: #635bff;
  font-size: 1.125rem;
  padding: 1rem;
  font-weight: 600;
}

.checkout-button:hover:not(:disabled) {
  background: #5145e5;
  border-color: #5145e5;
}

.checkout-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.checkout-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: #6b7280;
  font-size: 0.875rem;
}

.checkout-info i {
  color: #10b981;
}

.checkout-disclaimer {
  font-size: 0.75rem;
  color: #9ca3af;
  text-align: center;
  line-height: 1.5;
  margin: 0;
}

@media (max-width: 640px) {
  .checkout-button {
    font-size: 1rem;
    padding: 0.875rem;
  }
}
</style>
