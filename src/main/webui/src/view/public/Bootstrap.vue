<template>
  <div class="bootstrap-container">
    <Card class="bootstrap-card">
      <template #title>
        <div class="title-container">
          <i
            class="pi pi-shield"
            style="font-size: 2rem; color: var(--primary-color)"
          ></i>
          <h1>Bootstrap Admin Account</h1>
        </div>
      </template>

      <template #content>
        <!-- Loading State -->
        <div v-if="loading" class="loading-state">
          <ProgressSpinner />
          <p>Checking system status...</p>
        </div>

        <!-- Admin Already Exists -->
        <div v-else-if="!needsBootstrap" class="info-message">
          <Message severity="info">
            <p><strong>System Already Configured</strong></p>
            <p>
              Admin accounts already exist in the system. Bootstrap is not
              needed.
            </p>
          </Message>
          <Button label="Go to Home" icon="pi pi-home" @click="goHome" />
        </div>

        <!-- Bootstrap Form -->
        <div v-else class="bootstrap-form">
          <Message severity="warn" :closable="false">
            <p><strong>No Admin Account Found</strong></p>
            <p>
              The system needs at least one admin account. Please select a user
              to promote to admin.
            </p>
          </Message>

          <!-- No Users Message -->
          <div v-if="users.length === 0" class="no-users-message">
            <Message severity="info">
              <p><strong>No Users Found</strong></p>
              <p>
                You need to authenticate with OAuth (Google or Facebook) first
                before creating an admin account.
              </p>
            </Message>
            <div class="oauth-buttons">
              <Button
                label="Login with Google"
                icon="pi pi-google"
                class="p-button-outlined"
                @click="loginWithGoogle"
              />
              <Button
                label="Login with Facebook"
                icon="pi pi-facebook"
                class="p-button-outlined"
                @click="loginWithFacebook"
              />
            </div>
          </div>

          <!-- User Selection -->
          <div v-else class="user-selection">
            <h3>Select User to Promote to Admin</h3>
            <DataTable
              v-model:selection="selectedUser"
              :value="users"
              selection-mode="single"
              data-key="id"
              :paginator="false"
              class="user-table"
            >
              <Column
                selection-mode="single"
                header-style="width: 3rem"
              ></Column>
              <Column field="displayName" header="Name">
                <template #body="slotProps">
                  <div class="user-info">
                    <img
                      v-if="slotProps.data.profileImageUrl"
                      :src="slotProps.data.profileImageUrl"
                      class="user-avatar"
                      alt="Profile"
                    />
                    <div>
                      <div class="user-name">
                        {{ slotProps.data.displayName || "Unknown" }}
                      </div>
                      <div class="user-email">{{ slotProps.data.email }}</div>
                    </div>
                  </div>
                </template>
              </Column>
              <Column field="id" header="User ID">
                <template #body="slotProps">
                  <code class="user-id">{{ slotProps.data.id }}</code>
                </template>
              </Column>
            </DataTable>

            <div class="action-buttons">
              <Button
                label="Create Admin Account"
                icon="pi pi-shield"
                :disabled="!selectedUser || creating"
                :loading="creating"
                severity="primary"
                @click="createAdmin"
              />
            </div>
          </div>
        </div>

        <!-- Error Message -->
        <Message
          v-if="errorMessage"
          severity="error"
          @close="errorMessage = null"
        >
          {{ errorMessage }}
        </Message>
      </template>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useToast } from "primevue/usetoast";
import Card from "primevue/card";
import Button from "primevue/button";
import Message from "primevue/message";
import ProgressSpinner from "primevue/progressspinner";
import DataTable from "primevue/datatable";
import Column from "primevue/column";

interface User {
  id: string;
  email: string;
  displayName: string;
  profileImageUrl?: string;
}

interface BootstrapStatus {
  needsBootstrap: boolean;
  totalUsers: number;
  hasAdmins: boolean;
}

const router = useRouter();
const toast = useToast();

const loading = ref(true);
const needsBootstrap = ref(false);
const users = ref<User[]>([]);
const selectedUser = ref<User | null>(null);
const creating = ref(false);
const errorMessage = ref<string | null>(null);

onMounted(async () => {
  await checkBootstrapStatus();
});

async function checkBootstrapStatus() {
  try {
    loading.value = true;
    const response = await fetch("/bootstrap/status");

    if (!response.ok) {
      throw new Error("Failed to check bootstrap status");
    }

    const status: BootstrapStatus = await response.json();
    needsBootstrap.value = status.needsBootstrap;

    if (needsBootstrap.value) {
      // Fetch users for selection
      await fetchUsers();
    }
  } catch (error) {
    console.error("Error checking bootstrap status:", error);
    errorMessage.value = "Failed to check bootstrap status. Please try again.";
  } finally {
    loading.value = false;
  }
}

async function fetchUsers() {
  try {
    const response = await fetch("/bootstrap/users");

    if (!response.ok) {
      throw new Error("Failed to fetch users");
    }

    users.value = await response.json();
  } catch (error) {
    console.error("Error fetching users:", error);
    errorMessage.value = "Failed to fetch users. Please try again.";
  }
}

async function createAdmin() {
  if (!selectedUser.value) {
    return;
  }

  try {
    creating.value = true;
    errorMessage.value = null;

    const response = await fetch("/bootstrap/create-admin", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email: selectedUser.value.email,
      }),
    });

    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.error || "Failed to create admin account");
    }

    const authResponse = await response.json();

    // Store the JWT token
    localStorage.setItem("auth_token", authResponse.token);

    toast.add({
      severity: "success",
      summary: "Success",
      detail: "Admin account created successfully!",
      life: 3000,
    });

    // Redirect to home or admin page
    setTimeout(() => {
      router.push("/");
    }, 1000);
  } catch (error) {
    console.error("Error creating admin:", error);
    errorMessage.value =
      error instanceof Error ? error.message : "Failed to create admin account";
  } finally {
    creating.value = false;
  }
}

function loginWithGoogle() {
  // Redirect to Google OAuth login
  window.location.href = "/auth/login/google";
}

function loginWithFacebook() {
  // Redirect to Facebook OAuth login
  window.location.href = "/auth/login/facebook";
}

function goHome() {
  router.push("/");
}
</script>

<style scoped>
.bootstrap-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.bootstrap-card {
  max-width: 800px;
  width: 100%;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.title-container {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.title-container h1 {
  margin: 0;
  font-size: 1.75rem;
  color: var(--text-color);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 2rem;
}

.info-message {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.bootstrap-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.no-users-message {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.oauth-buttons {
  display: flex;
  gap: 1rem;
  justify-content: center;
  flex-wrap: wrap;
}

.user-selection {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.user-selection h3 {
  margin: 0;
  color: var(--text-color);
}

.user-table {
  margin: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.user-name {
  font-weight: 600;
  color: var(--text-color);
}

.user-email {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.user-id {
  font-size: 0.75rem;
  background: var(--surface-100);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  color: var(--text-color-secondary);
}

.action-buttons {
  display: flex;
  justify-content: flex-end;
  margin-top: 1rem;
}
</style>
