/**
 * TypeScript type definitions for CalendarOrder entities
 * Matches GraphQL schema types exactly
 */

export enum OrderStatus {
  PENDING = "PENDING",
  PAID = "PAID",
  PROCESSING = "PROCESSING",
  SHIPPED = "SHIPPED",
  DELIVERED = "DELIVERED",
  CANCELLED = "CANCELLED",
  REFUNDED = "REFUNDED",
}

export interface ShippingAddress {
  street: string;
  street2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export interface CalendarOrder {
  id: string;
  status: OrderStatus;
  quantity: number;
  unitPrice: number; // BigDecimal
  totalPrice: number; // BigDecimal
  shippingAddress: ShippingAddress;
  notes?: string;
  trackingNumber?: string;
  stripePaymentIntentId?: string;
  stripeChargeId?: string;
  created: string; // DateTime
  updated: string; // DateTime
  paidAt?: string; // DateTime
  shippedAt?: string; // DateTime
  deliveredAt?: string; // DateTime
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

export interface OrderUpdateInput {
  status: OrderStatus;
  notes?: string;
  trackingNumber?: string;
}

/**
 * Order filter options for admin dashboard
 */
export interface OrderFilters {
  status: OrderStatus | string | null;
  dateRange: Date[] | null;
  search: string;
}

/**
 * Status transition validation
 * Defines which status transitions are valid from each current status
 */
export const VALID_STATUS_TRANSITIONS: Record<
  OrderStatus | string,
  (OrderStatus | string)[]
> = {
  PENDING: ["PAID", "CANCELLED"],
  PAID: ["PROCESSING", "CANCELLED", "REFUNDED"],
  PROCESSING: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["DELIVERED"],
  DELIVERED: [],
  CANCELLED: ["REFUNDED"],
  REFUNDED: [],
};

/**
 * Check if a status transition is valid
 * @param currentStatus Current order status
 * @param newStatus Proposed new status
 * @returns true if transition is allowed
 */
export function isValidStatusTransition(
  currentStatus: OrderStatus | string,
  newStatus: OrderStatus | string,
): boolean {
  if (currentStatus === newStatus) return true;
  const allowedTransitions = VALID_STATUS_TRANSITIONS[currentStatus] || [];
  return allowedTransitions.includes(newStatus);
}

/**
 * Get allowed status transitions for current status
 * @param currentStatus Current order status
 * @returns Array of valid next statuses
 */
export function getAllowedStatusTransitions(
  currentStatus: OrderStatus | string,
): (OrderStatus | string)[] {
  return VALID_STATUS_TRANSITIONS[currentStatus] || [];
}
