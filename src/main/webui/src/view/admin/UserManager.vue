<script setup lang="ts">
import { onMounted, ref, computed } from "vue";
import {
  Breadcrumb,
  Button,
  Column,
  DataTable,
  Message,
  Tag,
  InputText,
  Avatar,
  ConfirmDialog,
} from "primevue";
import { useToast } from "primevue/usetoast";
import { useConfirm } from "primevue/useconfirm";
import {
  ALL_USERS_QUERY,
  UPDATE_USER_ADMIN_MUTATION,
  type AdminUser,
} from "../../graphql/admin-queries";
import { useAuthStore } from "../../stores/authStore";

const toast = useToast();
const confirm = useConfirm();
const authStore = useAuthStore();

// Breadcrumb
const homeBreadcrumb = ref({
  icon: "pi pi-home",
  url: "/",
});

const breadCrumbs = ref([{ label: "Admin" }, { label: "User Manager" }]);

// State
const loading = ref(false);
const error = ref<string | null>(null);
const users = ref<AdminUser[]>([]);

// Search filter
const searchFilter = ref("");

// Current user ID (to prevent removing own admin status)
const currentUserId = computed(() => {
  // Extract user ID from JWT token
  const token = localStorage.getItem("auth_token");
  if (!token) return null;
  try {
    const parts = token.split(".");
    const payload = JSON.parse(atob(parts[1]));
    return payload.sub;
  } catch {
    return null;
  }
});

// Filtered users
const filteredUsers = computed(() => {
  if (!searchFilter.value) {
    return users.value;
  }
  const search = searchFilter.value.toLowerCase();
  return users.value.filter(
    (u) =>
      u.email.toLowerCase().includes(search) ||
      (u.displayName && u.displayName.toLowerCase().includes(search)),
  );
});

// Stats
const stats = computed(() => ({
  total: users.value.length,
  admins: users.value.filter((u) => u.isAdmin).length,
  regular: users.value.filter((u) => !u.isAdmin).length,
}));

/**
 * Fetch all users from the API
 */
async function fetchUsers() {
  loading.value = true;
  error.value = null;

  try {
    const response = await fetch("/graphql", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${authStore.token}`,
      },
      body: JSON.stringify({
        query: ALL_USERS_QUERY,
        variables: { limit: 500 },
      }),
    });

    if (!response.ok) {
      throw new Error("Failed to fetch users");
    }

    const result = await response.json();

    if (result.errors) {
      throw new Error(result.errors[0]?.message || "GraphQL error");
    }

    users.value = result.data?.allUsers || [];
  } catch (err: any) {
    error.value = err.message || "Failed to fetch users";
    console.error("Error fetching users:", err);
  } finally {
    loading.value = false;
  }
}

/**
 * Update user admin status
 */
async function updateUserAdmin(user: AdminUser, newAdminStatus: boolean) {
  try {
    const response = await fetch("/graphql", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${authStore.token}`,
      },
      body: JSON.stringify({
        query: UPDATE_USER_ADMIN_MUTATION,
        variables: {
          userId: user.id,
          isAdmin: newAdminStatus,
        },
      }),
    });

    if (!response.ok) {
      throw new Error("Failed to update user");
    }

    const result = await response.json();

    if (result.errors) {
      throw new Error(result.errors[0]?.message || "GraphQL error");
    }

    // Update local state
    const index = users.value.findIndex((u) => u.id === user.id);
    if (index !== -1) {
      users.value[index].isAdmin = newAdminStatus;
    }

    toast.add({
      severity: "success",
      summary: "Success",
      detail: `${user.displayName || user.email} is now ${newAdminStatus ? "an admin" : "a regular user"}`,
      life: 3000,
    });
  } catch (err: any) {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: err.message || "Failed to update user",
      life: 5000,
    });
  }
}

/**
 * Confirm toggle admin status
 */
function confirmToggleAdmin(user: AdminUser) {
  const newStatus = !user.isAdmin;
  const action = newStatus ? "grant admin privileges to" : "remove admin privileges from";

  // Prevent removing own admin status
  if (user.id === currentUserId.value && !newStatus) {
    toast.add({
      severity: "warn",
      summary: "Not Allowed",
      detail: "You cannot remove your own admin status",
      life: 5000,
    });
    return;
  }

  confirm.require({
    message: `Are you sure you want to ${action} ${user.displayName || user.email}?`,
    header: newStatus ? "Grant Admin Privileges" : "Remove Admin Privileges",
    icon: newStatus ? "pi pi-shield" : "pi pi-exclamation-triangle",
    acceptClass: newStatus ? "p-button-success" : "p-button-danger",
    accept: () => updateUserAdmin(user, newStatus),
  });
}

/**
 * Format date for display
 */
function formatDate(date: string | null | undefined): string {
  if (!date) return "Never";
  return new Date(date).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * Format relative time
 */
function formatRelativeTime(date: string | null | undefined): string {
  if (!date) return "Never";
  const now = new Date();
  const then = new Date(date);
  const diffMs = now.getTime() - then.getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffDays === 0) return "Today";
  if (diffDays === 1) return "Yesterday";
  if (diffDays < 7) return `${diffDays} days ago`;
  if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`;
  if (diffDays < 365) return `${Math.floor(diffDays / 30)} months ago`;
  return `${Math.floor(diffDays / 365)} years ago`;
}

/**
 * Get provider icon
 */
function getProviderIcon(provider: string): string {
  switch (provider?.toLowerCase()) {
    case "google":
      return "pi pi-google";
    case "facebook":
      return "pi pi-facebook";
    case "apple":
      return "pi pi-apple";
    default:
      return "pi pi-user";
  }
}

onMounted(async () => {
  await fetchUsers();
});
</script>

<template>
  <ConfirmDialog />
  <Breadcrumb :home="homeBreadcrumb" :model="breadCrumbs" class="mb-4" />

  <div class="user-manager-page">
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-3xl font-bold">User Manager</h1>
      <Button
        icon="pi pi-refresh"
        label="Refresh"
        severity="secondary"
        outlined
        @click="fetchUsers"
        :loading="loading"
      />
    </div>

    <!-- Stats Cards -->
    <div class="stats-grid mb-6">
      <div class="stat-card">
        <div class="stat-icon bg-blue-100">
          <i class="pi pi-users text-blue-600"></i>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ stats.total }}</span>
          <span class="stat-label">Total Users</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon bg-purple-100">
          <i class="pi pi-shield text-purple-600"></i>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ stats.admins }}</span>
          <span class="stat-label">Admins</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon bg-green-100">
          <i class="pi pi-user text-green-600"></i>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ stats.regular }}</span>
          <span class="stat-label">Regular Users</span>
        </div>
      </div>
    </div>

    <!-- Error Display -->
    <Message v-if="error" severity="error" class="mb-4">{{ error }}</Message>

    <!-- Search Filter -->
    <div class="mb-4">
      <InputText
        v-model="searchFilter"
        placeholder="Search users by name or email..."
        class="w-full md:w-1/2"
      >
        <template #prepend>
          <i class="pi pi-search" />
        </template>
      </InputText>
    </div>

    <!-- Users DataTable -->
    <DataTable
      :value="filteredUsers"
      :loading="loading"
      striped-rows
      paginator
      :rows="15"
      :rows-per-page-options="[10, 15, 25, 50]"
      table-style="min-width: 50rem"
      sort-field="created"
      :sort-order="-1"
    >
      <template #empty>
        <div class="text-center py-4">No users found</div>
      </template>

      <Column header="User" sortable sort-field="email" style="width: 30%">
        <template #body="{ data }">
          <div class="flex items-center gap-3">
            <Avatar
              v-if="data.profileImageUrl"
              :image="data.profileImageUrl"
              shape="circle"
              size="large"
            />
            <Avatar
              v-else
              :label="(data.displayName || data.email).charAt(0).toUpperCase()"
              shape="circle"
              size="large"
              class="bg-primary-100 text-primary-600"
            />
            <div>
              <div class="font-semibold">
                {{ data.displayName || "No Name" }}
                <Tag
                  v-if="data.id === currentUserId"
                  value="You"
                  severity="info"
                  class="ml-2 text-xs"
                />
              </div>
              <div class="text-sm text-surface-500">{{ data.email }}</div>
            </div>
          </div>
        </template>
      </Column>

      <Column header="Provider" sortable sort-field="oauthProvider" style="width: 12%">
        <template #body="{ data }">
          <div class="flex items-center gap-2">
            <i :class="getProviderIcon(data.oauthProvider)"></i>
            <span class="capitalize">{{ data.oauthProvider?.toLowerCase() }}</span>
          </div>
        </template>
      </Column>

      <Column header="Role" sortable sort-field="isAdmin" style="width: 12%">
        <template #body="{ data }">
          <Tag v-if="data.isAdmin" severity="warning" value="Admin" icon="pi pi-shield" />
          <Tag v-else severity="secondary" value="User" icon="pi pi-user" />
        </template>
      </Column>

      <Column header="Created" sortable sort-field="created" style="width: 15%">
        <template #body="{ data }">
          <div>
            <div>{{ formatRelativeTime(data.created) }}</div>
            <div class="text-xs text-surface-500">{{ formatDate(data.created) }}</div>
          </div>
        </template>
      </Column>

      <Column header="Last Login" sortable sort-field="lastLoginAt" style="width: 15%">
        <template #body="{ data }">
          <div>
            <div>{{ formatRelativeTime(data.lastLoginAt) }}</div>
            <div class="text-xs text-surface-500">{{ formatDate(data.lastLoginAt) }}</div>
          </div>
        </template>
      </Column>

      <Column header="Actions" style="width: 16%">
        <template #body="{ data }">
          <div class="flex gap-2">
            <Button
              v-if="!data.isAdmin"
              v-tooltip.top="'Make Admin'"
              icon="pi pi-shield"
              size="small"
              severity="success"
              outlined
              @click="confirmToggleAdmin(data)"
            />
            <Button
              v-else
              v-tooltip.top="data.id === currentUserId ? 'Cannot remove own admin' : 'Remove Admin'"
              icon="pi pi-user"
              size="small"
              severity="warning"
              outlined
              :disabled="data.id === currentUserId"
              @click="confirmToggleAdmin(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>
  </div>
</template>

<style scoped>
.user-manager-page {
  margin: 0 auto;
  width: 95%;
  max-width: 1400px;
  padding: 2rem;
  background-color: white;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
  background: white;
  border: 1px solid var(--surface-200);
  border-radius: 0.5rem;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 0.5rem;
}

.stat-icon i {
  font-size: 1.5rem;
}

.stat-content {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 700;
  line-height: 1;
}

.stat-label {
  font-size: 0.875rem;
  color: var(--surface-500);
}

@media (max-width: 768px) {
  .user-manager-page {
    width: 100%;
    padding: 1rem;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
