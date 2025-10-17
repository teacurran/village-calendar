<template>
  <div class="admin-dashboard">
    <!-- Back to Home Button -->
    <Button
      icon="pi pi-home"
      label="Back to Home"
      outlined
      class="back-button"
      @click="navigateTo('/')"
    />

    <div class="dashboard-header">
      <h1>Admin Dashboard</h1>
      <p class="welcome-text">Welcome back, {{ userName }}</p>
    </div>

    <div class="dashboard-cards">
      <!-- Template Manager Card -->
      <Card class="dashboard-card" @click="navigateTo('/admin/templates')">
        <template #header>
          <div
            class="card-icon"
            style="
              background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            "
          >
            <i class="pi pi-palette" style="font-size: 2.5rem"></i>
          </div>
        </template>
        <template #title>
          <h2>Template Manager</h2>
        </template>
        <template #content>
          <p>
            Create, edit, and manage calendar templates. Configure themes,
            layouts, and default settings for user calendars.
          </p>
          <div class="card-actions">
            <Button label="Manage Templates" icon="pi pi-arrow-right" text />
          </div>
        </template>
      </Card>

      <!-- Order Dashboard Card -->
      <Card class="dashboard-card" @click="navigateTo('/admin/orders')">
        <template #header>
          <div
            class="card-icon"
            style="
              background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
            "
          >
            <i class="pi pi-shopping-cart" style="font-size: 2.5rem"></i>
          </div>
        </template>
        <template #title>
          <h2>Order Management</h2>
        </template>
        <template #content>
          <p>
            View and manage customer orders. Update order statuses, add tracking
            numbers, and handle fulfillment.
          </p>
          <div class="card-actions">
            <Button label="View Orders" icon="pi pi-arrow-right" text />
          </div>
        </template>
      </Card>

      <!-- User Management Card (Future) -->
      <Card class="dashboard-card disabled-card">
        <template #header>
          <div
            class="card-icon"
            style="
              background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
            "
          >
            <i class="pi pi-users" style="font-size: 2.5rem"></i>
          </div>
        </template>
        <template #title>
          <h2>User Management</h2>
        </template>
        <template #content>
          <p>Manage user accounts, permissions, and admin roles.</p>
          <Tag severity="info" value="Coming Soon" class="mt-2" />
        </template>
      </Card>

      <!-- Analytics Card (Future) -->
      <Card class="dashboard-card disabled-card">
        <template #header>
          <div
            class="card-icon"
            style="
              background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
            "
          >
            <i class="pi pi-chart-bar" style="font-size: 2.5rem"></i>
          </div>
        </template>
        <template #title>
          <h2>Analytics</h2>
        </template>
        <template #content>
          <p>
            View sales statistics, popular templates, and customer insights.
          </p>
          <Tag severity="info" value="Coming Soon" class="mt-2" />
        </template>
      </Card>
    </div>

    <!-- Quick Actions -->
    <div class="quick-actions">
      <h2>Quick Actions</h2>
      <div class="action-buttons">
        <Button
          label="Create New Template"
          icon="pi pi-plus"
          @click="navigateTo('/admin/templates')"
        />
        <Button
          label="View Recent Orders"
          icon="pi pi-list"
          severity="secondary"
          @click="navigateTo('/admin/orders')"
        />
        <Button
          label="Go to Homepage"
          icon="pi pi-home"
          severity="secondary"
          outlined
          @click="navigateTo('/')"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../../stores/authStore";
import Card from "primevue/card";
import Button from "primevue/button";
import Tag from "primevue/tag";

const router = useRouter();
const authStore = useAuthStore();

const userName = computed(() => {
  return authStore.user?.displayName || authStore.user?.email || "Admin";
});

function navigateTo(path: string) {
  router.push(path);
}
</script>

<style scoped>
.admin-dashboard {
  max-width: 1400px;
  margin: 0 auto;
  padding: 2rem;
  position: relative;
}

.back-button {
  position: absolute;
  top: 2rem;
  left: 2rem;
}

.dashboard-header {
  margin-bottom: 3rem;
  text-align: center;
}

.dashboard-header h1 {
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 0.5rem 0;
}

.welcome-text {
  font-size: 1.125rem;
  color: var(--text-color-secondary);
  margin: 0;
}

.dashboard-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 2rem;
  margin-bottom: 3rem;
}

.dashboard-card {
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid var(--surface-border);
}

.dashboard-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
}

.disabled-card {
  opacity: 0.7;
  cursor: not-allowed;
}

.disabled-card:hover {
  transform: none;
  box-shadow: none;
}

.card-icon {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  border-radius: 8px 8px 0 0;
}

.dashboard-card h2 {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 0.5rem 0;
}

.dashboard-card p {
  color: var(--text-color-secondary);
  line-height: 1.6;
  margin-bottom: 1rem;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 1rem;
}

.quick-actions {
  background: var(--surface-50);
  padding: 2rem;
  border-radius: 8px;
  margin-top: 2rem;
}

.quick-actions h2 {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0 0 1.5rem 0;
  color: var(--text-color);
}

.action-buttons {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .admin-dashboard {
    padding: 1rem;
  }

  .dashboard-header h1 {
    font-size: 2rem;
  }

  .dashboard-cards {
    grid-template-columns: 1fr;
    gap: 1rem;
  }

  .action-buttons {
    flex-direction: column;
  }

  .action-buttons button {
    width: 100%;
  }
}
</style>
