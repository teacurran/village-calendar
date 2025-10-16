<template>
  <div class="oauth-callback-page">
    <div class="callback-container">
      <ProgressSpinner />
      <h2>Completing login...</h2>
      <p v-if="error" class="error-text">{{ error }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/authStore'
import ProgressSpinner from 'primevue/progressspinner'

const router = useRouter()
const authStore = useAuthStore()
const error = ref<string | null>(null)

/**
 * Extract JWT token from various possible locations:
 * 1. URL query parameter (e.g., ?token=...)
 * 2. URL hash fragment (e.g., #token=...)
 * 3. HTTP-only cookie (set by backend)
 */
const extractToken = (): string | null => {
  // Check URL query parameters
  const urlParams = new URLSearchParams(window.location.search)
  const tokenFromQuery = urlParams.get('token')
  if (tokenFromQuery) return tokenFromQuery

  // Check URL hash fragment
  const hash = window.location.hash.substring(1)
  const hashParams = new URLSearchParams(hash)
  const tokenFromHash = hashParams.get('token')
  if (tokenFromHash) return tokenFromHash

  // Check cookies
  const tokenFromCookie = getCookie('auth_token')
  if (tokenFromCookie) return tokenFromCookie

  return null
}

/**
 * Get cookie value by name
 */
const getCookie = (name: string): string | null => {
  const value = `; ${document.cookie}`
  const parts = value.split(`; ${name}=`)
  if (parts.length === 2) {
    const cookieValue = parts.pop()?.split(';').shift()
    return cookieValue || null
  }
  return null
}

/**
 * Handle OAuth callback on component mount
 */
onMounted(async () => {
  try {
    // Extract JWT token from URL or cookies
    const token = extractToken()

    if (!token) {
      error.value = 'No authentication token received. Please try logging in again.'

      // Redirect to home after 3 seconds
      setTimeout(() => {
        router.push('/')
      }, 3000)
      return
    }

    // Store token and fetch user info
    await authStore.handleOAuthCallback(token)

    // The handleOAuthCallback method redirects automatically,
    // but if it doesn't, redirect to home
    if (!authStore.isAuthenticated) {
      error.value = 'Failed to authenticate. Redirecting...'
      setTimeout(() => {
        router.push('/')
      }, 2000)
    }
  } catch (err: any) {
    console.error('OAuth callback error:', err)
    error.value = err.message || 'Authentication failed. Redirecting to home...'

    // Redirect to home after error
    setTimeout(() => {
      router.push('/')
    }, 3000)
  }
})
</script>

<style scoped>
.oauth-callback-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
}

.callback-container {
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

.callback-container h2 {
  font-size: 1.5rem;
  font-weight: 600;
  color: #111827;
  margin: 0;
}

.callback-container p {
  color: #6b7280;
  margin: 0;
}

.error-text {
  color: #ef4444 !important;
  font-weight: 500;
}
</style>
