<script setup lang="ts">
import { useRouter } from "vue-router";
import Card from "primevue/card";
import Button from "primevue/button";

const router = useRouter();

const returnToCart = () => {
  router.push({ name: "cart" });
};

const browseTemplates = () => {
  router.push({ name: "home" });
};

const viewDashboard = () => {
  router.push({ name: "dashboard" });
};
</script>

<template>
  <div class="order-cancelled-page">
    <div class="container">
      <Card class="cancelled-card">
        <template #content>
          <div class="cancelled-state">
            <i
              class="pi pi-times-circle"
              style="font-size: 4rem; color: #f59e0b"
            ></i>
            <h1>Payment Cancelled</h1>
            <p class="message">
              Your payment was cancelled and no charges were made to your
              account.
            </p>
            <p class="submessage">
              If you experienced any issues during checkout, please try again or
              contact our support team for assistance.
            </p>

            <div class="info-box">
              <i class="pi pi-info-circle"></i>
              <p>
                Your calendar design has been saved and is still available in
                your account. You can complete your order anytime by visiting
                your dashboard.
              </p>
            </div>

            <div class="button-group">
              <Button
                label="Try Again"
                icon="pi pi-refresh"
                class="primary-btn"
                @click="returnToCart"
              />
              <Button
                label="View My Calendars"
                icon="pi pi-calendar"
                severity="secondary"
                outlined
                @click="viewDashboard"
              />
              <Button
                label="Browse Templates"
                icon="pi pi-images"
                severity="secondary"
                text
                @click="browseTemplates"
              />
            </div>
          </div>
        </template>
      </Card>
    </div>
  </div>
</template>

<style scoped>
.order-cancelled-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.container {
  max-width: 600px;
  width: 100%;
}

.cancelled-card {
  text-align: center;
}

.cancelled-state {
  padding: 2rem;
}

.cancelled-state i {
  margin-bottom: 1rem;
}

.cancelled-state h1 {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 0.5rem;
}

.message {
  font-size: 1.125rem;
  color: #4b5563;
  margin: 1rem 0;
  line-height: 1.6;
}

.submessage {
  color: #6b7280;
  line-height: 1.6;
  margin-bottom: 2rem;
}

.info-box {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.5rem;
  background: #fffbeb;
  border: 1px solid #fcd34d;
  border-radius: 8px;
  margin: 2rem 0;
  text-align: left;
}

.info-box i {
  color: #f59e0b;
  font-size: 1.5rem;
  margin-top: 0.25rem;
  flex-shrink: 0;
}

.info-box p {
  color: #78350f;
  margin: 0;
  line-height: 1.6;
}

.button-group {
  display: flex;
  gap: 1rem;
  justify-content: center;
  margin-top: 2rem;
  flex-wrap: wrap;
}

.primary-btn {
  background: #f59e0b;
  border-color: #f59e0b;
}

.primary-btn:hover {
  background: #d97706;
  border-color: #d97706;
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
