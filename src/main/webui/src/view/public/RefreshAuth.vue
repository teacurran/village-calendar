<template>
  <div class="refresh-auth-page">
    <div class="refresh-container">
      <ProgressSpinner />
      <h2>Refreshing your authentication...</h2>
      <p>Please wait while we log you in again with updated permissions.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '../../stores/authStore'
import ProgressSpinner from 'primevue/progressspinner'

const authStore = useAuthStore()

onMounted(() => {
  // Clear old token
  authStore.logout()

  // Redirect to Google login after a short delay
  setTimeout(() => {
    window.location.href = '/auth/login/google'
  }, 1000)
})
</script>

<style scoped>
.refresh-auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
}

.refresh-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 3rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  max-width: 400px;
  text-align: center;
}

.refresh-container h2 {
  font-size: 1.5rem;
  font-weight: 600;
  color: #111827;
  margin: 0;
}

.refresh-container p {
  color: #6b7280;
  margin: 0;
}
</style>
