/**
 * Order Store (Admin)
 * Pinia store for managing order fulfillment (admin functions)
 */

import { defineStore } from "pinia";
import {
  fetchAllOrdersAdmin,
  updateOrderStatusAdmin,
} from "../services/orderService";
import type { OrderUpdateInput } from "../types/order";

export interface CalendarOrderItem {
  id: string;
  productType: string;
  productName?: string;
  calendarYear?: number;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  itemStatus: string;
}

export interface CalendarOrder {
  id: string;
  orderNumber?: string;
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
  updated: string;
  paidAt?: string;
  shippedAt?: string;
  deliveredAt?: string;
  totalItemCount?: number;
  items?: CalendarOrderItem[];
  calendar?: {
    id: string;
    name: string;
    year: number;
  };
  user: {
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
  }),

  actions: {
    /**
     * Load all orders with optional status filter (admin only)
     */
    async loadOrders(authToken: string, status?: string, limit: number = 100) {
      this.loading = true;
      this.error = null;
      this.statusFilter = status || null;

      try {
        const orders = await fetchAllOrdersAdmin(authToken, status, limit);
        this.orders = orders;
        return true;
      } catch (err: any) {
        this.error = err.message || "Failed to load orders";
        return false;
      } finally {
        this.loading = false;
      }
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
          // Update in orders array
          const index = this.orders.findIndex((o) => o.id === orderId);
          if (index !== -1) {
            this.orders[index] = updatedOrder;
          }

          // Update current order if it matches
          if (this.currentOrder?.id === orderId) {
            this.currentOrder = updatedOrder;
          }
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
