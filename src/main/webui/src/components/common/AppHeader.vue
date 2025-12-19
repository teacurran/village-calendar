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
        <div class="header-end-section">
          <!-- Calendar Editor Toolbar (only visible when editing a calendar) -->
          <div v-if="calendarEditorStore.isActive" class="editor-toolbar">
            <Button
              v-tooltip.bottom="'Zoom In'"
              icon="pi pi-search-plus"
              text
              rounded
              :disabled="!calendarEditorStore.hasGeneratedSVG"
              @click="calendarEditorStore.zoomIn()"
            />
            <Button
              v-tooltip.bottom="'Zoom Out'"
              icon="pi pi-search-minus"
              text
              rounded
              :disabled="!calendarEditorStore.hasGeneratedSVG"
              @click="calendarEditorStore.zoomOut()"
            />
            <Button
              v-tooltip.bottom="'Reset Zoom'"
              icon="pi pi-refresh"
              text
              rounded
              :disabled="!calendarEditorStore.hasGeneratedSVG"
              @click="calendarEditorStore.resetZoom()"
            />
            <Button
              v-tooltip.bottom="'Toggle Rulers'"
              icon="pi pi-arrows-h"
              text
              rounded
              :disabled="!calendarEditorStore.hasGeneratedSVG"
              :class="{ 'ruler-active': calendarEditorStore.showRulers }"
              @click="calendarEditorStore.toggleRulers()"
            />
            <div class="toolbar-divider"></div>
            <Button
              v-tooltip.bottom="'Download PDF'"
              icon="pi pi-download"
              text
              rounded
              :disabled="!calendarEditorStore.hasGeneratedSVG"
              @click="calendarEditorStore.openAddToCartModal('pdf')"
            />
            <Button
              v-if="authStore.isAdmin"
              v-tooltip.bottom="'Save as Template'"
              icon="pi pi-bookmark"
              text
              rounded
              :disabled="!calendarEditorStore.hasGeneratedSVG"
              @click="calendarEditorStore.saveAsTemplate()"
            />
            <Button
              label="Order a Print"
              icon="pi pi-print"
              text
              :disabled="!calendarEditorStore.hasGeneratedSVG"
              class="order-print-btn"
              @click="calendarEditorStore.openAddToCartModal('print')"
            />
          </div>

          <!-- Maze Editor Toolbar (only visible when editing a maze) -->
          <div v-if="mazeEditorStore.isActive" class="editor-toolbar">
            <Button
              v-tooltip.bottom="'Zoom In'"
              icon="pi pi-search-plus"
              text
              rounded
              :disabled="!mazeEditorStore.hasGeneratedSVG"
              @click="mazeEditorStore.zoomIn()"
            />
            <Button
              v-tooltip.bottom="'Zoom Out'"
              icon="pi pi-search-minus"
              text
              rounded
              :disabled="!mazeEditorStore.hasGeneratedSVG"
              @click="mazeEditorStore.zoomOut()"
            />
            <Button
              v-tooltip.bottom="'Reset Zoom'"
              icon="pi pi-refresh"
              text
              rounded
              :disabled="!mazeEditorStore.hasGeneratedSVG"
              @click="mazeEditorStore.resetZoom()"
            />
            <Button
              v-tooltip.bottom="'Toggle Rulers'"
              icon="pi pi-arrows-h"
              text
              rounded
              :disabled="!mazeEditorStore.hasGeneratedSVG"
              :class="{ 'ruler-active': mazeEditorStore.showRulers }"
              @click="mazeEditorStore.toggleRulers()"
            />
            <div class="toolbar-divider"></div>
            <Button
              v-tooltip.bottom="'Download PDF'"
              icon="pi pi-download"
              text
              rounded
              :disabled="!mazeEditorStore.hasGeneratedSVG"
              @click="mazeEditorStore.openAddToCartModal('pdf')"
            />
            <Button
              label="Order a Print"
              icon="pi pi-print"
              text
              :disabled="!mazeEditorStore.hasGeneratedSVG"
              class="order-print-btn"
              @click="mazeEditorStore.openAddToCartModal('print')"
            />
          </div>

          <!-- Cart Icon (always visible) -->
          <div class="cart-icon-wrapper">
            <Button
              icon="pi pi-shopping-cart"
              text
              rounded
              @click="toggleCart"
            />
            <Badge
              v-if="cartStore.itemCount > 0"
              :value="cartStore.itemCount.toString()"
              severity="danger"
              class="cart-badge"
            />
          </div>

          <!-- User menu for authenticated users -->
          <div v-if="authStore.isAuthenticated" class="user-section">
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
        </div>
      </template>
    </Menubar>
  </header>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/authStore";
import { useCartStore } from "@/stores/cart";
import { useCalendarEditorStore } from "@/stores/calendarEditor";
import { useMazeEditorStore } from "@/stores/mazeEditor";
import Menubar from "primevue/menubar";
import Button from "primevue/button";
import Badge from "primevue/badge";
import Menu from "primevue/menu";
import type { MenuItem } from "primevue/menuitem";
// Note: Cart is fetched centrally in App.vue via appInit store

const router = useRouter();
const authStore = useAuthStore();
const cartStore = useCartStore();
const calendarEditorStore = useCalendarEditorStore();
const mazeEditorStore = useMazeEditorStore();

// State
const userMenuRef = ref();

// Navigation menu items
const menuItems = computed<MenuItem[]>(() => {
  const items: MenuItem[] = [];

  // Show "Customize" and "Generate New" for maze editor, "Design Your Own" for calendar
  if (mazeEditorStore.isActive) {
    items.push({
      label: "Customize",
      icon: "pi pi-sparkles",
      command: () => mazeEditorStore.openWizard(),
    });
    items.push({
      icon: "pi pi-refresh",
      command: () => mazeEditorStore.generateNew(),
    });
  } else {
    items.push({
      label: "Design Your Own",
      icon: "pi pi-sparkles",
      command: () => router.push("/?wizard=true"),
    });
  }

  // Add admin menu items (only for calendar, not maze)
  if (authStore.isAdmin && !mazeEditorStore.isActive) {
    items.push({
      label: "Manage Templates",
      icon: "pi pi-book",
      command: () => router.push("/?manage-templates=true"),
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

// Handle logout
const handleLogout = () => {
  authStore.logout();
  router.push("/");
};

// Toggle cart drawer
const toggleCart = () => {
  cartStore.toggleCart();
};
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

.header-end-section {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding-right: 0.5rem;
  margin-right: 0.5rem;
  border-right: 1px solid var(--surface-200);
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  background: var(--surface-200);
  margin: 0 0.25rem;
}

/* Style Order a Print button to match menu items */
.order-print-btn {
  font-weight: 500;
  padding: 0.5rem 0.75rem;
}

/* Active state for ruler toggle button */
.ruler-active {
  background-color: var(--primary-100) !important;
  color: var(--primary-700) !important;
}

.cart-icon-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.cart-badge {
  position: absolute;
  top: 0;
  right: 0;
  min-width: 18px;
  height: 18px;
  font-size: 0.7rem;
  pointer-events: none;
}

.user-section {
  display: flex;
  align-items: center;
}

/* Responsive styles */
@media (max-width: 768px) {
  .logo-text {
    display: none;
  }

  .editor-toolbar {
    gap: 0;
  }

  /* Hide "Order a Print" label on mobile, show only icon */
  .editor-toolbar :deep(.p-button-label) {
    display: none;
  }
}

@media (max-width: 480px) {
  /* Hide zoom controls on very small screens */
  .editor-toolbar {
    display: none;
  }
}
</style>
