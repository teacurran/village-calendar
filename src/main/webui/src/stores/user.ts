// ./stores/user.ts
import { defineStore } from 'pinia'

export interface User {
  id: string
  username: string
  email: string
  name: string
  provider?: string // OAuth2 provider (google, facebook)
  avatarUrl?: string
}

export const useUserStore = defineStore('user', {
  state: () => ({
    currentUser: null as User | null,
    loading: false,
    error: null as string | null,
    isAuthenticated: false,
  }),

  actions: {
    async fetchCurrentUser() {
      this.loading = true
      this.error = null
      try {
        // GraphQL query to get current authenticated user
        const response = await fetch('/graphql', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            query: `
              query GetCurrentUser {
                currentUser {
                  id
                  username
                  email
                  name
                  provider
                  avatarUrl
                }
              }
            `,
          }),
        })

        if (!response.ok) {
          throw new Error('Failed to fetch user')
        }

        const result = await response.json()

        if (result.errors) {
          throw new Error(result.errors[0]?.message || 'GraphQL error')
        }

        if (result.data?.currentUser) {
          this.currentUser = result.data.currentUser
          this.isAuthenticated = true
        } else {
          this.currentUser = null
          this.isAuthenticated = false
        }
      } catch (err: any) {
        // User not authenticated or error occurred
        this.currentUser = null
        this.isAuthenticated = false
        this.error = err.message || 'Failed to load user'
      } finally {
        this.loading = false
      }
    },

    async login(provider: 'google' | 'facebook') {
      // Redirect to OAuth2 login endpoint
      window.location.href = `/oauth2/authorization/${provider}`
    },

    async logout() {
      this.loading = true
      this.error = null
      try {
        const response = await fetch('/api/auth/logout', {
          method: 'POST',
          credentials: 'include'
        })

        if (!response.ok) {
          throw new Error('Logout failed')
        }

        this.currentUser = null
        this.isAuthenticated = false

        // Redirect to home page after logout
        window.location.href = '/'
      } catch (err: any) {
        this.error = err.message || 'Logout failed'
      } finally {
        this.loading = false
      }
    },

    clearError() {
      this.error = null
    },
  },

  getters: {
    isLoggedIn: (state) => state.isAuthenticated && !!state.currentUser,
    userName: (state) => state.currentUser?.name || state.currentUser?.username || 'Guest',
    userEmail: (state) => state.currentUser?.email || '',
    userAvatar: (state) => state.currentUser?.avatarUrl || '',
  },
})
