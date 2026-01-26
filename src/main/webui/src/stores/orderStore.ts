/**
 * Order Store (Admin)
 * Pinia store for managing order fulfillment (admin functions)
 */

import { defineStore } from "pinia";
import {
  fetchAdminOrdersPaginated,
  updateOrderStatusAdmin,
} from "../services/orderService";
import type { OrderUpdateInput } from "../types/order";

/**
 * Parse a JSON field that may be returned as a string from GraphQL.
 * GraphQL JsonNodeAdapter returns JsonNode as JSON strings.
 */
function parseJsonField(value: any): any {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === "string") {
    try {
      return JSON.parse(value);
    } catch {
      return value;
    }
  }
  return value;
}

export interface CalendarOrderItem {
  id: string;
  productType: string;
  productName?: string;
  description?: string;
  calendarYear?: number;
  year?: number;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  itemStatus: string;
  configuration?: any;
  assets?: any[];
}

export interface CalendarOrder {
  id: string;
  orderNumber?: string;
  customerName?: string;
  customerEmail?: string;
  status: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  subtotal?: number;
  shippingCost?: number;
  taxAmount?: number;
  shippingAddress: any;
  notes?: string;
  trackingNumber?: string;
  stripePaymentIntentId?: string;
  stripeChargeId?: string;
  created: string;
  updated?: string;
  paidAt?: string;
  shippedAt?: string;
  deliveredAt?: string;
  totalItemCount?: number;
  items?: CalendarOrderItem[];
  calendar?: {
    id: string;
    name: string;
    year: number;
    generatedSvg?: string;
  };
  user?: {
    id: string;
    email: string;
    displayName?: string;
  };
}

export const useOrderStore = defineStore("order", {
  state: () => ({
    orders: [] as CalendarOrder[],
    currentOrder: null as CalendarOrder | null,
    loading: false,
    error: null as string | null,
    statusFilter: null as string | null,
    // Pagination state
    currentPage: 0,
    pageSize: 25,
    totalCount: 0,
    totalPages: 0,
  }),

  actions: {
    /**
     * Load paginated orders with optional status filter (admin only)
     */
    async loadOrders(
      authToken: string,
      status?: string,
      page: number = 0,
      pageSize: number = 25,
    ) {
      this.loading = true;
      this.error = null;
      this.statusFilter = status || null;

      try {
        const response = await fetchAdminOrdersPaginated(
          authToken,
          page,
          pageSize,
          status,
        );

        // REST endpoint returns parsed objects, no need for parseJsonField
        this.orders = response.orders;
        this.currentPage = response.page;
        this.pageSize = response.pageSize;
        this.totalCount = response.totalCount;
        this.totalPages = response.totalPages;

        return true;
      } catch (err: any) {
        this.error = err.message || "Failed to load orders";
        return false;
      } finally {
        this.loading = false;
      }
    },

    /**
     * Go to specific page
     */
    async goToPage(authToken: string, page: number) {
      return this.loadOrders(
        authToken,
        this.statusFilter || undefined,
        page,
        this.pageSize,
      );
    },

    /**
     * Change page size and reload
     */
    async setPageSize(authToken: string, pageSize: number) {
      this.pageSize = pageSize;
      return this.loadOrders(
        authToken,
        this.statusFilter || undefined,
        0,
        pageSize,
      );
    },

    /**
     * Update order status (admin only)
     */
    async updateOrderStatus(
      orderId: string,
      input: OrderUpdateInput,
      authToken: string,
    ) {
      this.loading = true;
      this.error = null;

      try {
        const updatedOrder = await updateOrderStatusAdmin(
          orderId,
          input,
          authToken,
        );

        if (updatedOrder) {
          // Parse shippingAddress JSON string
          const parsedOrder = {
            ...updatedOrder,
            shippingAddress: parseJsonField(updatedOrder.shippingAddress),
          };

          // Update in orders array
          const index = this.orders.findIndex((o) => o.id === orderId);
          if (index !== -1) {
            this.orders[index] = parsedOrder;
          }

          // Update current order if it matches
          if (this.currentOrder?.id === orderId) {
            this.currentOrder = parsedOrder;
          }

          return parsedOrder;
        }

        return updatedOrder;
      } catch (err: any) {
        this.error = err.message || "Failed to update order status";
        return null;
      } finally {
        this.loading = false;
      }
    },

    /**
     * Set current order
     */
    setCurrentOrder(order: CalendarOrder | null) {
      this.currentOrder = order;
    },

    /**
     * Clear error message
     */
    clearError() {
      this.error = null;
    },

    /**
     * Clear all orders
     */
    clearOrders() {
      this.orders = [];
      this.currentOrder = null;
    },
  },

  getters: {
    /**
     * Get orders by status
     */
    ordersByStatus: (state) => (status: string) =>
      state.orders.filter((o) => o.status === status),

    /**
     * Get order by ID
     */
    getOrderById: (state) => (id: string) =>
      state.orders.find((o) => o.id === id),

    /**
     * Check if orders are loaded
     */
    hasOrders: (state) => state.orders.length > 0,

    /**
     * Get order counts by status
     */
    orderCounts: (state) => {
      const counts: Record<string, number> = {};
      state.orders.forEach((order) => {
        counts[order.status] = (counts[order.status] || 0) + 1;
      });
      return counts;
    },
  },
});
