<template>
  <div class="dashboard">
    <div class="dashboard-container">
      <!-- Header -->
      <div class="dashboard-header">
        <h1 class="dashboard-title">My Dashboard</h1>
        <p class="dashboard-subtitle">
          Welcome back, {{ authStore.userName }}!
        </p>
      </div>

      <!-- Dashboard Content -->
      <div class="dashboard-content">
        <!-- Quick Stats -->
        <div class="stats-grid">
          <Card class="stat-card">
            <template #content>
              <div class="stat-content">
                <i class="pi pi-shopping-cart stat-icon"></i>
                <div class="stat-info">
                  <div class="stat-label">Total Orders</div>
                  <div class="stat-value">{{ orderCount }}</div>
                </div>
              </div>
            </template>
          </Card>

          <Card class="stat-card">
            <template #content>
              <div class="stat-content">
                <i class="pi pi-calendar stat-icon"></i>
                <div class="stat-info">
                  <div class="stat-label">Active Calendars</div>
                  <div class="stat-value">{{ activeCalendars }}</div>
                </div>
              </div>
            </template>
          </Card>

          <Card class="stat-card">
            <template #content>
              <div class="stat-content">
                <i class="pi pi-clock stat-icon"></i>
                <div class="stat-info">
                  <div class="stat-label">Pending Orders</div>
                  <div class="stat-value">{{ pendingOrders }}</div>
                </div>
              </div>
            </template>
          </Card>
        </div>

        <!-- Quick Actions -->
        <Card class="actions-card">
          <template #title>Quick Actions</template>
          <template #content>
            <div class="actions-grid">
              <Button
                label="Create New Calendar"
                icon="pi pi-plus"
                class="action-button"
                @click="createNewCalendar"
              />
              <Button
                label="View My Orders"
                icon="pi pi-shopping-bag"
                outlined
                class="action-button"
                @click="viewOrders"
              />
              <Button
                label="Browse Templates"
                icon="pi pi-images"
                outlined
                class="action-button"
                @click="browseTemplates"
              />
            </div>
          </template>
        </Card>

        <!-- Recent Orders Section -->
        <Card class="recent-orders-card">
          <template #title>Recent Orders</template>
          <template #content>
            <div v-if="loading" class="loading-container">
              <ProgressSpinner />
            </div>
            <div v-else-if="recentOrders.length > 0" class="orders-list">
              <div
                v-for="order in recentOrders"
                :key="order.id"
                class="order-item"
              >
                <div class="order-info">
                  <span class="order-id">Order #{{ order.id }}</span>
                  <span class="order-date">{{ formatDate(order.date) }}</span>
                </div>
                <Tag
                  :value="order.status"
                  :severity="getStatusSeverity(order.status)"
                />
              </div>
            </div>
            <div v-else class="empty-state">
              <i class="pi pi-inbox text-4xl text-gray-300 mb-2"></i>
              <p class="text-gray-500">No orders yet</p>
              <Button
                label="Create Your First Calendar"
                icon="pi pi-plus"
                class="mt-3"
                @click="createNewCalendar"
              />
            </div>
          </template>
        </Card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/authStore";
import Card from "primevue/card";
import Button from "primevue/button";
import Tag from "primevue/tag";
import ProgressSpinner from "primevue/progressspinner";

const router = useRouter();
const authStore = useAuthStore();

// State
const loading = ref(false);
const recentOrders = ref<Array<{ id: string; date: string; status: string }>>(
  [],
);

// Mock stats (will be replaced with real data from API)
const orderCount = ref(0);
const activeCalendars = ref(0);
const pendingOrders = ref(0);

// Load dashboard data
const loadDashboardData = async () => {
  loading.value = true;
  try {
    // TODO: Fetch real data from API
    // For now, using mock data
    await new Promise((resolve) => setTimeout(resolve, 500));

    // Mock data
    orderCount.value = 0;
    activeCalendars.value = 0;
    pendingOrders.value = 0;
    recentOrders.value = [];
  } catch (error) {
    console.error("Error loading dashboard data:", error);
  } finally {
    loading.value = false;
  }
};

// Format date
const formatDate = (date: string) => {
  return new Date(date).toLocaleDateString();
};

// Get status severity for Tag component
const getStatusSeverity = (status: string) => {
  const severityMap: Record<string, string> = {
    completed: "success",
    processing: "info",
    pending: "warning",
    cancelled: "danger",
  };
  return severityMap[status.toLowerCase()] || "info";
};

// Actions
const createNewCalendar = () => {
  router.push("/");
};

const viewOrders = () => {
  // TODO: Navigate to orders page when implemented
};

const browseTemplates = () => {
  router.push("/templates");
};

// Load data on mount
onMounted(() => {
  loadDashboardData();
});
</script>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: #f9fafb;
  padding: 2rem 1rem;
}

.dashboard-container {
  max-width: 1400px;
  margin: 0 auto;
}

.dashboard-header {
  margin-bottom: 2rem;
}

.dashboard-title {
  font-size: 2rem;
  font-weight: bold;
  color: #111827;
  margin-bottom: 0.5rem;
}

.dashboard-subtitle {
  color: #6b7280;
  font-size: 1.125rem;
}

.dashboard-content {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
}

.stat-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.stat-icon {
  font-size: 2.5rem;
  color: var(--primary-color);
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 0.875rem;
  color: #6b7280;
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: 2rem;
  font-weight: bold;
  color: #111827;
}

.actions-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.actions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.action-button {
  width: 100%;
}

.recent-orders-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.loading-container {
  display: flex;
  justify-content: center;
  padding: 3rem 0;
}

.orders-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.order-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  transition: background 0.2s;
}

.order-item:hover {
  background: #f9fafb;
}

.order-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.order-id {
  font-weight: 600;
  color: #111827;
}

.order-date {
  font-size: 0.875rem;
  color: #6b7280;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 1rem;
  text-align: center;
}

@media (max-width: 768px) {
  .dashboard {
    padding: 1rem 0.5rem;
  }

  .dashboard-title {
    font-size: 1.5rem;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .actions-grid {
    grid-template-columns: 1fr;
  }
}
</style>
