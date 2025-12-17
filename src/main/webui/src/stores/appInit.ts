// ./stores/appInit.ts
// Centralized app initialization - fetches cart + currentUser in a single GraphQL call
import { defineStore } from "pinia";
import { getSessionId } from "@/services/sessionService";
import { useCartStore, type Cart } from "./cart";
import { useUserStore, type User } from "./user";

export const useAppInitStore = defineStore("appInit", {
  state: () => ({
    initialized: false,
    loading: false,
    error: null as string | null,
  }),

  actions: {
    /**
     * Initialize app data with a single combined GraphQL query.
     * Fetches both cart and currentUser together to reduce network calls.
     */
    async initialize() {
      // Skip if already initialized or currently loading
      if (this.initialized || this.loading) {
        return;
      }

      this.loading = true;
      this.error = null;

      try {
        const response = await fetch("/graphql", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Session-ID": getSessionId(),
          },
          body: JSON.stringify({
            query: `
              query InitializeApp {
                cart {
                  id
                  subtotal
                  taxAmount
                  totalAmount
                  itemCount
                  items {
                    id
                    templateId
                    templateName
                    year
                    quantity
                    unitPrice
                    lineTotal
                    productCode
                    configuration
                  }
                }
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
        });

        if (!response.ok) {
          throw new Error("Failed to initialize app");
        }

        const result = await response.json();

        if (result.errors) {
          // Log errors but don't fail - some queries may return null legitimately
          console.warn("GraphQL initialization warnings:", result.errors);
        }

        // Update cart store
        const cartStore = useCartStore();
        if (result.data?.cart) {
          cartStore.$patch({
            cart: result.data.cart as Cart,
            lastFetchTime: Date.now(),
          });
        } else {
          // Initialize empty cart
          cartStore.$patch({
            cart: {
              id: "",
              subtotal: 0,
              taxAmount: 0,
              totalAmount: 0,
              itemCount: 0,
              items: [],
            },
            lastFetchTime: Date.now(),
          });
        }

        // Update user store
        const userStore = useUserStore();
        if (result.data?.currentUser) {
          userStore.$patch({
            currentUser: result.data.currentUser as User,
            isAuthenticated: true,
          });
        } else {
          userStore.$patch({
            currentUser: null,
            isAuthenticated: false,
          });
        }

        this.initialized = true;
      } catch (err: unknown) {
        this.error =
          err instanceof Error ? err.message : "Failed to initialize app";
        console.error("App initialization error:", err);
      } finally {
        this.loading = false;
      }
    },

    /**
     * Reset initialization state (e.g., after logout)
     */
    reset() {
      this.initialized = false;
      this.loading = false;
      this.error = null;
    },
  },
});
