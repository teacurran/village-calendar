import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "./stores/authStore";
import { adminRoutes } from "./navigation/adminRoutes";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "home",
      component: () => import("./view/public/CalendarBrowser.vue"),
      meta: { requiresAuth: false },
    },
    {
      path: "/editor/:templateId",
      name: "editor",
      component: () => import("./view/public/CalendarEditor.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/dashboard",
      name: "dashboard",
      component: () => import("./view/Dashboard.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/checkout/:calendarId",
      name: "checkout",
      component: () => import("./view/public/Checkout.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/order/:orderId/confirmation",
      name: "order-confirmation",
      component: () => import("./view/public/OrderConfirmation.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/auth/callback",
      name: "oauth-callback",
      component: () => import("./view/public/OAuthCallback.vue"),
      meta: { requiresAuth: false },
    },
    {
      path: "/payment/callback",
      name: "payment-callback",
      component: () => import("./view/public/PaymentCallback.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/bootstrap",
      name: "bootstrap",
      component: () => import("./view/public/Bootstrap.vue"),
      meta: { requiresAuth: false },
    },
    {
      path: "/refresh-auth",
      name: "refresh-auth",
      component: () => import("./view/public/RefreshAuth.vue"),
      meta: { requiresAuth: false },
    },
    // Admin routes
    ...adminRoutes,
    // Legacy routes - keep for backward compatibility
    {
      path: "/generator",
      name: "generator",
      component: () => import("./view/CalendarGenerator.vue"),
      meta: { requiresAuth: false },
    },
    {
      path: "/marketing",
      name: "marketing",
      component: () => import("./view/CalendarMarketing.vue"),
      meta: { requiresAuth: false },
    },
    {
      path: "/cart",
      name: "cart",
      component: () => import("./view/Cart.vue"),
      meta: { requiresAuth: false },
    },
  ],
});

// Navigation guard for authentication
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore();

  // Check if route requires authentication
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    // Save the intended destination
    sessionStorage.setItem("auth_return_to", to.fullPath);

    // Redirect to login (initiate OAuth flow)
    // For now, just redirect to home and show a message
    // The component will handle the OAuth initiation
    next({ name: "home" });
  } else {
    next();
  }
});

export default router;
