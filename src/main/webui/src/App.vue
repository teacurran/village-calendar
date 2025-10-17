<template>
  <div id="app">
    <Toast />
    <ConfirmDialog />
    <AppHeader />
    <main class="main-content">
      <RouterView />
    </main>
    <AppFooter />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from "vue";
import { RouterView } from "vue-router";
import { useAuthStore } from "@/stores/authStore";
import Toast from "primevue/toast";
import ConfirmDialog from "primevue/confirmdialog";
import AppHeader from "@/components/common/AppHeader.vue";
import AppFooter from "@/components/common/AppFooter.vue";

const authStore = useAuthStore();

// Initialize authentication on app mount
onMounted(() => {
  authStore.initialize();
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
