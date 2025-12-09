/**
 * Admin Routes Configuration
 * Routes for admin-only pages with authorization guards
 */

import type { RouteRecordRaw } from "vue-router";

/**
 * Check if user has admin role in JWT token
 * JWT format: { groups: ['ADMIN', ...], ... }
 */
export function hasAdminRole(): boolean {
  const token = localStorage.getItem("auth_token");
  if (!token) {
    return false;
  }

  try {
    // Decode JWT token (split by '.' and decode the payload)
    const parts = token.split(".");
    if (parts.length !== 3) {
      return false;
    }

    const payload = JSON.parse(atob(parts[1]));
    const groups = payload.groups || [];

    // Check if ADMIN group exists
    return groups.includes("ADMIN");
  } catch (err) {
    console.error("Error parsing JWT token:", err);
    return false;
  }
}

/**
 * Admin route guard
 * Checks for authentication and admin role
 */
export async function adminGuard(to: any, from: any, next: any) {
  const token = localStorage.getItem("auth_token");

  if (!token) {
    // Save intended destination and redirect to login
    sessionStorage.setItem("auth_return_to", to.fullPath);
    next({ name: "login" });
    return;
  }

  const isAdmin = hasAdminRole();

  if (!isAdmin) {
    next({ name: "home", query: { error: "forbidden" } });
    return;
  }

  next();
}

/**
 * Admin routes definition
 */
export const adminRoutes: RouteRecordRaw[] = [
  {
    path: "/admin",
    name: "admin-dashboard",
    component: () => import("../view/admin/AdminDashboard.vue"),
    meta: {
      requiresAuth: true,
      requiresAdmin: true,
    },
    beforeEnter: adminGuard,
  },
  {
    path: "/admin/templates",
    name: "admin-templates",
    component: () => import("../view/admin/TemplateManager.vue"),
    meta: {
      requiresAuth: true,
      requiresAdmin: true,
    },
    beforeEnter: adminGuard,
  },
  {
    path: "/admin/orders",
    name: "admin-orders",
    component: () => import("../view/admin/OrderDashboard.vue"),
    meta: {
      requiresAuth: true,
      requiresAdmin: true,
    },
    beforeEnter: adminGuard,
  },
  {
    path: "/admin/users",
    name: "admin-users",
    component: () => import("../view/admin/UserManager.vue"),
    meta: {
      requiresAuth: true,
      requiresAdmin: true,
    },
    beforeEnter: adminGuard,
  },
];
