// ./stores/authStore.ts
import { defineStore } from "pinia";
import { hasAdminRole } from "../navigation/adminRoutes";
import { getSessionId, clearSessionId } from "../utils/session";

export interface CalendarUser {
  id: string;
  email: string;
  displayName?: string;
  profileImageUrl?: string;
  oauthProvider: "GOOGLE" | "FACEBOOK";
  oauthSubject: string;
  created: string;
  lastLoginAt?: string;
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
    user: null as CalendarUser | null,
    token: localStorage.getItem("auth_token") || null,
    loading: false,
    error: null as string | null,
  }),

  actions: {
    /**
     * Initialize authentication by checking for existing token
     * and validating it with the backend
     */
    async initialize() {
      if (this.token) {
        // Validate token with backend - this will logout if token is invalid
        // (e.g., after server restart when OIDC session is lost)
        await this.fetchCurrentUser();
      }
    },

    /**
     * Fetch the currently authenticated user from the backend
     */
    async fetchCurrentUser() {
      if (!this.token) {
        this.user = null;
        return;
      }

      this.loading = true;
      this.error = null;
      try {
        const response = await fetch("/graphql", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${this.token}`,
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
                  created
                  lastLoginAt
                }
              }
            `,
          }),
        });

        if (!response.ok) {
          throw new Error("Failed to fetch current user");
        }

        const result = await response.json();

        if (result.errors) {
          throw new Error(result.errors[0]?.message || "GraphQL error");
        }

        if (result.data?.me) {
          this.user = result.data.me;
        } else {
          // Token is invalid or expired
          this.logout();
        }
      } catch (err: any) {
        this.error = err.message || "Failed to fetch current user";
        // Clear invalid token
        this.logout();
      } finally {
        this.loading = false;
      }
    },

    /**
     * Initiate OAuth2 login flow by redirecting to provider
     * @param provider - 'google' or 'facebook'
     */
    initiateLogin(provider: "google" | "facebook") {
      // Store the current path to redirect back after login
      const returnTo = window.location.pathname + window.location.search;
      sessionStorage.setItem("auth_return_to", returnTo);

      // Get session ID to include in OAuth flow for session conversion
      const sessionId = getSessionId();
      if (sessionId) {
        sessionStorage.setItem("guest_session_id", sessionId);
        console.log("[Auth] Stored session ID for OAuth flow:", sessionId);
      }

      // Redirect to OAuth provider
      window.location.href = `/api/auth/login/${provider}`;
    },

    /**
     * Handle OAuth2 callback after user returns from provider
     * This is called from the OAuth callback page with the JWT token
     */
    async handleOAuthCallback(token: string) {
      this.token = token;
      localStorage.setItem("auth_token", token);

      // Decode user info from JWT token directly
      try {
        const parts = token.split(".");
        const payload = JSON.parse(atob(parts[1]));

        // Create user object from JWT payload
        this.user = {
          id: payload.sub,
          email: payload.email,
          displayName: payload.name,
          profileImageUrl: undefined,
          oauthProvider: "GOOGLE",
          oauthSubject: "",
          created: "",
          lastLoginAt: "",
        };
      } catch (err) {
        console.error("Failed to decode JWT token:", err);
      }

      // Convert guest session if exists
      const guestSessionId = sessionStorage.getItem("guest_session_id");
      if (guestSessionId) {
        try {
          console.log(
            "[Auth] Converting guest session to user:",
            guestSessionId,
          );
          await this.convertGuestSession(guestSessionId);
          sessionStorage.removeItem("guest_session_id");
        } catch (err) {
          console.error("[Auth] Failed to convert guest session:", err);
          // Don't block login if session conversion fails
        }
      }

      // Clear guest session ID from localStorage after conversion
      clearSessionId();

      // Get the return URL (set before OAuth redirect)
      const returnTo = sessionStorage.getItem("auth_return_to");
      sessionStorage.removeItem("auth_return_to");

      // If there's a specific return URL, use it (e.g., /bootstrap)
      if (returnTo) {
        window.location.href = returnTo;
      } else if (hasAdminRole()) {
        // Redirect admin users to the admin dashboard
        window.location.href = "/admin";
      } else {
        // Redirect regular users to home
        window.location.href = "/";
      }
    },

    /**
     * Convert guest session calendars to authenticated user
     */
    async convertGuestSession(sessionId: string): Promise<number> {
      if (!this.token) {
        throw new Error("Authentication required to convert guest session");
      }

      try {
        const response = await fetch("/graphql", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${this.token}`,
          },
          body: JSON.stringify({
            query: `
              mutation ConvertGuestSession($sessionId: String!) {
                convertGuestSession(sessionId: $sessionId)
              }
            `,
            variables: { sessionId },
          }),
        });

        if (!response.ok) {
          throw new Error("Failed to convert guest session");
        }

        const result = await response.json();

        if (result.errors) {
          throw new Error(
            result.errors[0]?.message || "GraphQL error during conversion",
          );
        }

        const convertedCount = result.data?.convertGuestSession || 0;
        console.log(
          `[Auth] Converted ${convertedCount} calendars from guest session`,
        );
        return convertedCount;
      } catch (err: any) {
        console.error("[Auth] Error converting guest session:", err);
        throw err;
      }
    },

    /**
     * Logout the current user
     */
    logout() {
      this.user = null;
      this.token = null;
      this.error = null;
      localStorage.removeItem("auth_token");
    },

    /**
     * Check if a valid authentication token exists
     */
    hasToken(): boolean {
      return !!this.token;
    },

    /**
     * Clear any authentication errors
     */
    clearError() {
      this.error = null;
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
    userName: (state) =>
      state.user?.displayName || state.user?.email || "Guest",

    /**
     * Get user's profile image URL
     */
    userAvatar: (state) => state.user?.profileImageUrl || null,

    /**
     * Check if current user has admin role
     */
    isAdmin: () => hasAdminRole(),
  },
});
