// Breadcrumb utilities for navigation
import { ROUTE_NAMES } from "./routes";

export interface BreadcrumbItem {
  label: string;
  to?: string | { name: string };
  icon?: string;
}

/**
 * Home breadcrumb - common starting point for all breadcrumb trails
 */
export const homeBreadcrumb: BreadcrumbItem = {
  label: "Home",
  to: { name: ROUTE_NAMES.HOME },
  icon: "pi pi-home",
};

/**
 * Cart breadcrumb
 */
export const cartBreadcrumb: BreadcrumbItem = {
  label: "Cart",
  to: { name: ROUTE_NAMES.CART },
  icon: "pi pi-shopping-cart",
};

/**
 * Checkout breadcrumb
 */
export const checkoutBreadcrumb: BreadcrumbItem = {
  label: "Checkout",
  to: { name: ROUTE_NAMES.CHECKOUT },
};

/**
 * Order Confirmation breadcrumb (no link - final step)
 */
export const orderConfirmationBreadcrumb: BreadcrumbItem = {
  label: "Order Confirmation",
  icon: "pi pi-check-circle",
};

/**
 * Build breadcrumb trail for cart page
 */
export function getCartBreadcrumbs(): BreadcrumbItem[] {
  return [homeBreadcrumb, cartBreadcrumb];
}

/**
 * Build breadcrumb trail for checkout page
 */
export function getCheckoutBreadcrumbs(): BreadcrumbItem[] {
  return [homeBreadcrumb, cartBreadcrumb, checkoutBreadcrumb];
}

/**
 * Build breadcrumb trail for order confirmation page
 */
export function getOrderConfirmationBreadcrumbs(): BreadcrumbItem[] {
  return [
    homeBreadcrumb,
    cartBreadcrumb,
    checkoutBreadcrumb,
    orderConfirmationBreadcrumb,
  ];
}
