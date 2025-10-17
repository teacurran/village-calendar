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
  console.log("Admin guard triggered for route:", to.path);

  const token = localStorage.getItem("auth_token");
  console.log("Token exists?", !!token);

  if (!token) {
    // Not authenticated - redirect to home
    console.log("No token - redirecting to home");
    next({ name: "home", query: { error: "auth_required" } });
    return;
  }

  const isAdmin = hasAdminRole();
  console.log("Has admin role?", isAdmin);

  if (!isAdmin) {
    // Not an admin - show forbidden error
    console.log("Not admin - redirecting to home");

    // Debug: decode token to see what's inside
    try {
      const parts = token.split(".");
      const payload = JSON.parse(atob(parts[1]));
      console.log("JWT payload:", payload);
      console.log("JWT groups:", payload.groups);
    } catch (e) {
      console.error("Failed to decode token:", e);
    }

    next({ name: "home", query: { error: "forbidden" } });
    return;
  }

  // User is admin - allow access
  console.log("Admin access granted");
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
];
