// ./stores/authStore.ts
import { defineStore } from 'pinia'

export interface CalendarUser {
  id: string
  email: string
  displayName?: string
  profileImageUrl?: string
  oauthProvider: 'GOOGLE' | 'FACEBOOK'
  oauthSubject: string
  createdAt: string
  lastLoginAt?: string
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as CalendarUser | null,
    token: localStorage.getItem('auth_token') || null,
    loading: false,
    error: null as string | null,
  }),

  actions: {
    /**
     * Initialize authentication by checking for existing token
     * and fetching current user if authenticated
     */
    async initialize() {
      if (this.token) {
        await this.fetchCurrentUser()
      }
    },

    /**
     * Fetch the currently authenticated user from the backend
     */
    async fetchCurrentUser() {
      if (!this.token) {
        this.user = null
        return
      }

      this.loading = true
      this.error = null
      try {
        const response = await fetch('/graphql', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${this.token}`,
          },
          body: JSON.stringify({
            query: `
              query GetCurrentUser {
                me {
                  id
                  email
                  displayName
                  profileImageUrl
                  oauthProvider
                  oauthSubject
                  createdAt
                  lastLoginAt
                }
              }
            `,
          }),
        })

        if (!response.ok) {
          throw new Error('Failed to fetch current user')
        }

        const result = await response.json()

        if (result.errors) {
          throw new Error(result.errors[0]?.message || 'GraphQL error')
        }

        if (result.data?.me) {
          this.user = result.data.me
        } else {
          // Token is invalid or expired
          this.logout()
        }
      } catch (err: any) {
        this.error = err.message || 'Failed to fetch current user'
        // Clear invalid token
        this.logout()
      } finally {
        this.loading = false
      }
    },

    /**
     * Initiate OAuth2 login flow by redirecting to provider
     * @param provider - 'google' or 'facebook'
     */
    initiateLogin(provider: 'google' | 'facebook') {
      // Store the current path to redirect back after login
      const returnTo = window.location.pathname + window.location.search
      sessionStorage.setItem('auth_return_to', returnTo)

      // Redirect to OAuth provider
      window.location.href = `/auth/login/${provider}`
    },

    /**
     * Handle OAuth2 callback after user returns from provider
     * This is called from the OAuth callback page with the JWT token
     */
    async handleOAuthCallback(token: string) {
      this.token = token
      localStorage.setItem('auth_token', token)

      // Fetch user info
      await this.fetchCurrentUser()

      // Redirect to the original page or home
      const returnTo = sessionStorage.getItem('auth_return_to') || '/'
      sessionStorage.removeItem('auth_return_to')
      window.location.href = returnTo
    },

    /**
     * Logout the current user
     */
    logout() {
      this.user = null
      this.token = null
      this.error = null
      localStorage.removeItem('auth_token')
    },

    /**
     * Check if a valid authentication token exists
     */
    hasToken(): boolean {
      return !!this.token
    },

    /**
     * Clear any authentication errors
     */
    clearError() {
      this.error = null
    },
  },

  getters: {
    /**
     * Check if user is authenticated
     */
    isAuthenticated: (state) => !!state.user && !!state.token,

    /**
     * Get user display name or email
     */
    userName: (state) => state.user?.displayName || state.user?.email || 'Guest',

    /**
     * Get user's profile image URL
     */
    userAvatar: (state) => state.user?.profileImageUrl || null,
  },
})
