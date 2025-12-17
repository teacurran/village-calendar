<template>
  <div id="app">
    <Toast position="bottom-right" />
    <ConfirmDialog />
    <AppHeader />
    <main class="main-content">
      <RouterView />
    </main>
    <AppFooter />
    <CartDrawer />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from "vue";
import { RouterView } from "vue-router";
import { useAppInitStore } from "@/stores/appInit";
import Toast from "primevue/toast";
import ConfirmDialog from "primevue/confirmdialog";
import AppHeader from "@/components/common/AppHeader.vue";
import AppFooter from "@/components/common/AppFooter.vue";
import CartDrawer from "@/components/CartDrawer.vue";

const appInitStore = useAppInitStore();

// Initialize app data (cart + user) in single GraphQL call on mount
// Note: JWT auth is initialized earlier in main.ts before mount
onMounted(() => {
  appInitStore.initialize();
});
</script>

<style scoped>
#app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
