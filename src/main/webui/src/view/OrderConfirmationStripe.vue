<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useCartStore } from "../stores/cart";
import ProgressSpinner from "primevue/progressspinner";

const router = useRouter();
const route = useRoute();
const cartStore = useCartStore();

const loading = ref(true);
const error = ref<string | null>(null);

onMounted(async () => {
  const sessionId = route.query.session_id as string;

  if (!sessionId) {
    error.value = "No session ID provided";
    loading.value = false;
    return;
  }

  try {
    // Look up order by Stripe session ID
    const response = await fetch("/graphql", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        query: `
          query GetOrderBySessionId($sessionId: String!) {
            orderByStripeSessionId(sessionId: $sessionId) {
              id
              orderNumber
              status
            }
          }
        `,
        variables: { sessionId },
      }),
    });

    const result = await response.json();

    if (result.errors || !result.data?.orderByStripeSessionId) {
      // Order not found by session - might not be processed yet
      // Wait a moment and try again
      await new Promise((resolve) => setTimeout(resolve, 2000));

      const retryResponse = await fetch("/graphql", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          query: `
            query GetOrderBySessionId($sessionId: String!) {
              orderByStripeSessionId(sessionId: $sessionId) {
                id
                orderNumber
                status
              }
            }
          `,
          variables: { sessionId },
        }),
      });

      const retryResult = await retryResponse.json();

      if (retryResult.errors || !retryResult.data?.orderByStripeSessionId) {
        error.value =
          "Order not found. Please check your email for confirmation.";
        loading.value = false;
        return;
      }

      const order = retryResult.data.orderByStripeSessionId;
      await cartStore.clearCart();
      router.replace({
        name: "order-confirmation",
        params: { orderId: order.orderNumber },
      });
      return;
    }

    const order = result.data.orderByStripeSessionId;

    // Clear the cart
    await cartStore.clearCart();

    // Redirect to the proper order confirmation page
    router.replace({
      name: "order-confirmation",
      params: { orderId: order.orderNumber },
    });
  } catch (err) {
    console.error("Error fetching order:", err);
    error.value =
      "Failed to load order. Please check your email for confirmation.";
    loading.value = false;
  }
});
</script>

<template>
  <div class="confirmation-loading">
    <div v-if="loading" class="loading-state">
      <ProgressSpinner />
      <p class="mt-4">Loading your order confirmation...</p>
    </div>

    <div v-else-if="error" class="error-state">
      <i class="pi pi-exclamation-triangle text-5xl text-orange-500 mb-4"></i>
      <h2>{{ error }}</h2>
      <p class="mt-2 text-gray-600">
        If you completed payment, you should receive a confirmation email
        shortly.
      </p>
      <button class="mt-4 p-button" @click="router.push({ name: 'templates' })">
        Continue Shopping
      </button>
    </div>
  </div>
</template>

<style scoped>
.confirmation-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.loading-state,
.error-state {
  text-align: center;
  color: var(--text-color-secondary);
}

.error-state h2 {
  color: var(--text-color);
}
</style>
