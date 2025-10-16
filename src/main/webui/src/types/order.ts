/**
 * TypeScript type definitions for CalendarOrder entities
 * Matches GraphQL schema types exactly
 */

export enum OrderStatus {
  PENDING = 'PENDING',
  PAID = 'PAID',
  PROCESSING = 'PROCESSING',
  SHIPPED = 'SHIPPED',
  DELIVERED = 'DELIVERED',
  CANCELLED = 'CANCELLED',
  REFUNDED = 'REFUNDED',
}

export interface ShippingAddress {
  street: string
  street2?: string
  city: string
  state: string
  postalCode: string
  country: string
}

export interface CalendarOrder {
  id: string
  status: OrderStatus
  quantity: number
  unitPrice: number // BigDecimal
  totalPrice: number // BigDecimal
  shippingAddress: ShippingAddress
  notes?: string
  trackingNumber?: string
  stripePaymentIntentId?: string
  stripeChargeId?: string
  createdAt: string // DateTime
  updatedAt: string // DateTime
  paidAt?: string // DateTime
  shippedAt?: string // DateTime
  deliveredAt?: string // DateTime
  calendar: {
    id: string
    name: string
    year: number
  }
  user: {
    id: string
    email: string
    displayName?: string
  }
}

export interface OrderUpdateInput {
  status: OrderStatus
  notes?: string
  trackingNumber?: string
}
