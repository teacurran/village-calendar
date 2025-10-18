<template>
  <header class="app-header">
    <Menubar :model="menuItems" class="border-none">
      <template #start>
        <router-link to="/" class="logo-link">
          <i class="pi pi-calendar text-2xl mr-2"></i>
          <span class="logo-text">Village Calendar</span>
        </router-link>
      </template>
      <template #end>
        <!-- Cart Icon (always visible) -->
        <div class="cart-icon-wrapper">
          <Button
            icon="pi pi-shopping-cart"
            text
            rounded
            @click="toggleCart"
            class="relative"
          >
            <Badge
              v-if="cartStore.itemCount > 0"
              :value="cartStore.itemCount.toString()"
              severity="danger"
              class="cart-badge"
            />
          </Button>
        </div>

        <!-- Auth buttons for non-authenticated users -->
        <div v-if="!authStore.isAuthenticated" class="auth-buttons">
          <Button
            label="Sign In"
            icon="pi pi-sign-in"
            text
            @click="showLoginDialog = true"
          />
        </div>

        <!-- User menu for authenticated users -->
        <div v-else class="user-section">
          <!-- Admin button for admin users -->
          <Button
            v-if="authStore.isAdmin"
            label="Admin"
            icon="pi pi-cog"
            severity="secondary"
            outlined
            class="mr-2"
            @click="navigateToAdmin"
          />

          <!-- User profile dropdown -->
          <Button
            :label="authStore.userName"
            icon="pi pi-user"
            outlined
            aria-haspopup="true"
            aria-controls="user_menu"
            @click="toggleUserMenu"
          />
          <Menu
            id="user_menu"
            ref="userMenuRef"
            :model="userMenuItems"
            :popup="true"
          />
        </div>
      </template>
    </Menubar>

    <!-- Login Dialog -->
    <Dialog
      v-model:visible="showLoginDialog"
      header="Sign In"
      :modal="true"
      :closable="true"
      :style="{ width: '400px' }"
    >
      <div class="login-dialog-content">
        <p class="mb-4 text-gray-600">
          Choose a provider to sign in to your account
        </p>
        <div class="login-buttons">
          <Button
            label="Sign in with Google"
            icon="pi pi-google"
            class="w-full mb-3"
            @click="loginWithProvider('google')"
          />
          <Button
            label="Sign in with Facebook"
            icon="pi pi-facebook"
            severity="info"
            class="w-full"
            @click="loginWithProvider('facebook')"
          />
        </div>
      </div>
    </Dialog>
  </header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/authStore";
import { useCartStore } from "@/stores/cart";
import Menubar from "primevue/menubar";
import Button from "primevue/button";
import Badge from "primevue/badge";
import Menu from "primevue/menu";
import Dialog from "primevue/dialog";
import type { MenuItem } from "primevue/menuitem";

const router = useRouter();
const authStore = useAuthStore();
const cartStore = useCartStore();

// State
const showLoginDialog = ref(false);
const userMenuRef = ref();

// Navigation menu items
const menuItems = computed<MenuItem[]>(() => {
  const items: MenuItem[] = [
    {
      label: "Home",
      icon: "pi pi-home",
      command: () => router.push("/"),
    },
    {
      label: "Templates",
      icon: "pi pi-calendar",
      command: () => router.push("/"),
    },
  ];

  // Add authenticated user menu items
  if (authStore.isAuthenticated) {
    items.push({
      label: "My Orders",
      icon: "pi pi-shopping-bag",
      command: () => router.push("/dashboard"),
    });
  }

  return items;
});

// User dropdown menu items
const userMenuItems = computed<MenuItem[]>(() => [
  {
    label: authStore.userName,
    icon: "pi pi-user",
    disabled: true,
  },
  {
    separator: true,
  },
  {
    label: "My Orders",
    icon: "pi pi-shopping-bag",
    command: () => router.push("/dashboard"),
  },
  {
    separator: true,
  },
  {
    label: "Logout",
    icon: "pi pi-sign-out",
    command: () => handleLogout(),
  },
]);

// Toggle user menu
const toggleUserMenu = (event: Event) => {
  userMenuRef.value.toggle(event);
};

// Navigate to admin panel
const navigateToAdmin = () => {
  router.push("/admin");
};

// Login with OAuth provider
const loginWithProvider = (provider: "google" | "facebook") => {
  showLoginDialog.value = false;
  authStore.initiateLogin(provider);
};

// Handle logout
const handleLogout = () => {
  authStore.logout();
  router.push("/");
};

// Toggle cart drawer
const toggleCart = () => {
  cartStore.toggleCart();
};

// Fetch cart on mount
onMounted(() => {
  cartStore.fetchCart();
});
</script>

<style scoped>
.app-header {
  background: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.logo-link {
  display: flex;
  align-items: center;
  text-decoration: none;
  color: var(--primary-color);
  font-weight: 700;
  font-size: 1.25rem;
  padding: 0 1rem;
  transition: opacity 0.2s;
}

.logo-link:hover {
  opacity: 0.8;
}

.logo-text {
  white-space: nowrap;
}

.auth-buttons {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.cart-icon-wrapper {
  display: flex;
  align-items: center;
  margin-right: 0.5rem;
}

.cart-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  font-size: 0.7rem;
}

.user-section {
  display: flex;
  align-items: center;
}

.login-dialog-content {
  padding: 1rem 0;
}

.login-buttons {
  display: flex;
  flex-direction: column;
}

/* Responsive styles */
@media (max-width: 768px) {
  .logo-text {
    display: none;
  }
}
</style>
