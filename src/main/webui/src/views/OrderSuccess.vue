<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useToast } from "primevue/usetoast";
import Card from "primevue/card";
import Button from "primevue/button";
import ProgressSpinner from "primevue/progressspinner";

const route = useRoute();
const router = useRouter();
const toast = useToast();

const loading = ref(true);
const orderNumber = ref<string | null>(null);
const orderDetails = ref<any>(null);
const error = ref<string | null>(null);

onMounted(async () => {
  try {
    // Get session_id from query params (Stripe redirects back with this)
    const sessionId = route.query.session_id as string;

    if (!sessionId) {
      throw new Error("No session ID provided");
    }

    // TODO: Query backend to get order details by Stripe session ID
    // For now, display a success message with the session ID
    orderNumber.value = `Pending confirmation`;

    toast.add({
      severity: "success",
      summary: "Order Placed Successfully",
      detail: "You will receive a confirmation email shortly.",
      life: 5000,
    });
  } catch (err: any) {
    error.value = err.message || "Failed to load order confirmation";
    toast.add({
      severity: "error",
      summary: "Error",
      detail: error.value,
      life: 5000,
    });
  } finally {
    loading.value = false;
  }
});

const viewOrders = () => {
  router.push({ name: "dashboard" });
};

const goHome = () => {
  router.push({ name: "home" });
};
</script>

<template>
  <div class="order-success-page">
    <div class="container">
      <Card v-if="loading" class="success-card">
        <template #content>
          <div class="loading-state">
            <ProgressSpinner />
            <p>Loading order confirmation...</p>
          </div>
        </template>
      </Card>

      <Card v-else-if="error" class="success-card">
        <template #content>
          <div class="error-state">
            <i
              class="pi pi-exclamation-triangle"
              style="font-size: 3rem; color: #f59e0b"
            ></i>
            <h1>Error Loading Confirmation</h1>
            <p>{{ error }}</p>
            <div class="button-group">
              <Button
                label="Go to Dashboard"
                icon="pi pi-home"
                @click="viewOrders"
              />
              <Button
                label="Browse Templates"
                icon="pi pi-images"
                severity="secondary"
                @click="goHome"
              />
            </div>
          </div>
        </template>
      </Card>

      <Card v-else class="success-card">
        <template #content>
          <div class="success-state">
            <i
              class="pi pi-check-circle"
              style="font-size: 4rem; color: #10b981"
            ></i>
            <h1>Order Confirmed!</h1>
            <p v-if="orderNumber" class="order-number">
              Order Number: <strong>{{ orderNumber }}</strong>
            </p>
            <p class="confirmation-message">
              Thank you for your purchase! Your calendar order has been
              confirmed. You will receive an email confirmation shortly with
              your order details and tracking information once your order ships.
            </p>

            <div class="next-steps">
              <h3>What's Next?</h3>
              <ul>
                <li>
                  <i class="pi pi-envelope"></i>
                  Check your email for order confirmation
                </li>
                <li>
                  <i class="pi pi-print"></i>
                  Your calendar will be printed and prepared for shipping
                </li>
                <li>
                  <i class="pi pi-truck"></i>
                  You'll receive tracking information when your order ships
                </li>
              </ul>
            </div>

            <div class="button-group">
              <Button
                label="View My Orders"
                icon="pi pi-list"
                class="primary-btn"
                @click="viewOrders"
              />
              <Button
                label="Create Another Calendar"
                icon="pi pi-plus"
                severity="secondary"
                outlined
                @click="goHome"
              />
            </div>
          </div>
        </template>
      </Card>
    </div>
  </div>
</template>

<style scoped>
.order-success-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.container {
  max-width: 600px;
  width: 100%;
}

.success-card {
  text-align: center;
}

.loading-state,
.error-state,
.success-state {
  padding: 2rem;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.error-state i,
.success-state i {
  margin-bottom: 1rem;
}

.success-state h1,
.error-state h1 {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 0.5rem;
}

.order-number {
  font-size: 1.125rem;
  color: #4b5563;
  margin: 1rem 0;
}

.order-number strong {
  color: #1f2937;
  font-weight: 600;
}

.confirmation-message {
  color: #6b7280;
  line-height: 1.6;
  margin: 1.5rem 0;
}

.next-steps {
  text-align: left;
  margin: 2rem 0;
  padding: 1.5rem;
  background: #f9fafb;
  border-radius: 8px;
  border-left: 4px solid #10b981;
}

.next-steps h3 {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 1rem;
}

.next-steps ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.next-steps li {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 0;
  color: #4b5563;
}

.next-steps li i {
  color: #10b981;
  font-size: 1.25rem;
}

.button-group {
  display: flex;
  gap: 1rem;
  justify-content: center;
  margin-top: 2rem;
  flex-wrap: wrap;
}

.primary-btn {
  background: #10b981;
  border-color: #10b981;
}

.primary-btn:hover {
  background: #059669;
  border-color: #059669;
}

@media (max-width: 640px) {
  .button-group {
    flex-direction: column;
  }

  .button-group button {
    width: 100%;
  }
}
</style>
