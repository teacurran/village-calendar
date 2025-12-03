/**
 * Admin GraphQL Queries and Mutations
 * Centralized GraphQL operations for admin order management
 */

/**
 * Query: Fetch all orders with optional status filter (admin only)
 * Requires ADMIN role in JWT
 */
export const ALL_ORDERS_QUERY = `
  query AllOrders($status: OrderStatus, $limit: Int) {
    allOrders(status: $status, limit: $limit) {
      id
      status
      quantity
      unitPrice
      totalPrice
      shippingAddress
      notes
      trackingNumber
      stripePaymentIntentId
      stripeChargeId
      created
      updated
      paidAt
      shippedAt
      deliveredAt
      calendar {
        id
        name
        year
      }
      user {
        id
        email
        displayName
      }
    }
  }
`;

/**
 * Mutation: Update order status (admin only)
 * Requires ADMIN role in JWT
 */
export const UPDATE_ORDER_STATUS_MUTATION = `
  mutation UpdateOrderStatus($id: ID!, $input: OrderUpdateInput!) {
    updateOrderStatus(id: $id, input: $input) {
      id
      status
      quantity
      unitPrice
      totalPrice
      shippingAddress
      notes
      trackingNumber
      stripePaymentIntentId
      stripeChargeId
      created
      updated
      paidAt
      shippedAt
      deliveredAt
      calendar {
        id
        name
        year
      }
      user {
        id
        email
        displayName
      }
    }
  }
`;

/**
 * Mutation: Cancel order (admin can cancel any order)
 * Users can only cancel their own orders
 */
export const CANCEL_ORDER_MUTATION = `
  mutation CancelOrder($orderId: ID!, $reason: String) {
    cancelOrder(orderId: $orderId, reason: $reason) {
      id
      status
      notes
      updated
    }
  }
`;

/**
 * Query: Get single order by ID
 * Admin can view any order, users can view their own
 */
export const GET_ORDER_QUERY = `
  query GetOrder($id: ID!) {
    order(id: $id) {
      id
      quantity
      unitPrice
      totalPrice
      status
      shippingAddress
      notes
      trackingNumber
      paidAt
      shippedAt
      deliveredAt
      created
      updated
      stripePaymentIntentId
      stripeChargeId
      calendar {
        id
        name
        year
        generatedPdfUrl
        template {
          id
          name
        }
      }
      user {
        id
        email
        displayName
      }
    }
  }
`;

/**
 * Query: Get orders for specific user (admin only)
 */
export const USER_ORDERS_QUERY = `
  query UserOrders($userId: ID!, $status: OrderStatus) {
    orders(userId: $userId, status: $status) {
      id
      status
      quantity
      totalPrice
      created
      paidAt
      shippedAt
      calendar {
        id
        name
        year
      }
    }
  }
`;

/**
 * Query: Get all users (admin only)
 */
export const ALL_USERS_QUERY = `
  query AllUsers($limit: Int) {
    allUsers(limit: $limit) {
      id
      email
      displayName
      profileImageUrl
      created
      lastLoginAt
      oauthProvider
      isAdmin
    }
  }
`;

/**
 * Mutation: Update user admin status (admin only)
 * Cannot remove admin from self to prevent lockout
 */
export const UPDATE_USER_ADMIN_MUTATION = `
  mutation UpdateUserAdmin($userId: ID!, $isAdmin: Boolean!) {
    updateUserAdmin(userId: $userId, isAdmin: $isAdmin) {
      id
      email
      displayName
      isAdmin
    }
  }
`;

/**
 * Type definitions for TypeScript
 */
export interface OrderUpdateInput {
  status: OrderStatus;
  notes?: string;
  trackingNumber?: string;
}

export enum OrderStatus {
  PENDING = "PENDING",
  PAID = "PAID",
  PROCESSING = "PROCESSING",
  SHIPPED = "SHIPPED",
  DELIVERED = "DELIVERED",
  CANCELLED = "CANCELLED",
  REFUNDED = "REFUNDED",
}

export interface CalendarOrder {
  id: string;
  status: OrderStatus;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
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
  calendar: {
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

export interface AdminUser {
  id: string;
  email: string;
  displayName?: string;
  profileImageUrl?: string;
  created: string;
  lastLoginAt?: string;
  oauthProvider: string;
  isAdmin: boolean;
}
