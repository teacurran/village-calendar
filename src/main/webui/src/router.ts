import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "./stores/authStore";
import { adminRoutes } from "./navigation/adminRoutes";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "home",
      redirect: "/generator",
      meta: { requiresAuth: false },
    },
    {
      path: "/browse",
      name: "browse",
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
      path: "/calendar/:id/edit",
      name: "calendar-editor",
      component: () => import("./views/CalendarEditor.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/dashboard",
      name: "dashboard",
      component: () => import("./view/Dashboard.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/templates",
      name: "templates",
      component: () => import("./view/TemplateGallery.vue"),
      meta: { requiresAuth: false, title: "Browse Templates" },
    },
    {
      path: "/checkout",
      name: "checkout",
      component: () => import("./view/CheckoutEmbedded.vue"),
      meta: { requiresAuth: false },
    },
    {
      path: "/checkout/legacy",
      name: "checkout-legacy",
      component: () => import("./view/Checkout.vue"),
      meta: { requiresAuth: false },
    },
    {
      path: "/order/:orderId/confirmation",
      name: "order-confirmation",
      component: () => import("./view/OrderConfirmation.vue"),
      meta: { requiresAuth: false },
    },
    {
      path: "/order-confirmation",
      name: "order-confirmation-stripe",
      component: () => import("./view/OrderConfirmationStripe.vue"),
      meta: { requiresAuth: false },
    },
    {
      path: "/login",
      name: "login",
      component: () => import("./view/Login.vue"),
      meta: { requiresAuth: false },
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
      path: "/order/success",
      name: "order-success",
      component: () => import("./views/OrderSuccess.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/order/cancelled",
      name: "order-cancelled",
      component: () => import("./views/OrderCancelled.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/order/:orderNumber/:orderId",
      name: "order-status",
      component: () => import("./views/OrderStatus.vue"),
      meta: { requiresAuth: false },
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
    // Calendar generator routes
    {
      path: "/generator",
      name: "generator-legacy",
      redirect: "/calendars/generator",
      meta: { requiresAuth: false },
    },
    {
      path: "/calendars/generator",
      name: "calendar-generator",
      component: () => import("./view/CalendarGenerator.vue"),
      meta: { requiresAuth: false },
    },
    // Maze generator route
    {
      path: "/mazes/generator",
      name: "maze-generator",
      component: () => import("./view/maze/MazeGenerator.vue"),
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
