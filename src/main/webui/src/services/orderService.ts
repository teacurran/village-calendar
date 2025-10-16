/**
 * Order Service
 * Handles order creation and payment processing with Stripe
 */

import { loadStripe, Stripe, StripeElements } from '@stripe/stripe-js'
import type { OrderUpdateInput } from '../types/order'

let stripeInstance: Stripe | null = null
let stripePublishableKey: string | null = null

export interface PaymentIntent {
  id: string
  clientSecret: string
  amount: number
  calendarId: string
  quantity: number
  status: string
}

export interface ShippingAddress {
  street: string
  street2?: string
  city: string
  state: string
  postalCode: string
  country: string
}

export interface CreateOrderInput {
  calendarId: string
  quantity: number
  shippingAddress: ShippingAddress
}

/**
 * Initialize Stripe
 * Loads Stripe.js using the publishable key from environment variables
 */
export async function initializeStripe(): Promise<Stripe> {
  if (stripeInstance) {
    return stripeInstance
  }

  try {
    // Get Stripe publishable key from environment variable
    const publishableKey = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY

    if (!publishableKey) {
      throw new Error(
        'Stripe publishable key not configured. ' +
        'Please set VITE_STRIPE_PUBLISHABLE_KEY in your .env file.'
      )
    }

    stripePublishableKey = publishableKey

    // Load Stripe.js
    const stripe = await loadStripe(publishableKey)
    if (!stripe) {
      throw new Error('Failed to load Stripe')
    }

    stripeInstance = stripe
    return stripe
  } catch (error) {
    console.error('Error initializing Stripe:', error)
    throw error
  }
}

/**
 * Create order and payment intent
 * Calls the GraphQL mutation to create a payment intent
 */
export async function createOrder(
  input: CreateOrderInput,
  authToken: string
): Promise<PaymentIntent> {
  try {
    const response = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`,
      },
      body: JSON.stringify({
        query: `
          mutation CreateOrder(
            $calendarId: ID!
            $quantity: Int!
            $shippingAddress: AddressInput!
          ) {
            createOrder(
              calendarId: $calendarId
              quantity: $quantity
              shippingAddress: $shippingAddress
            ) {
              id
              clientSecret
              amount
              calendarId
              quantity
              status
            }
          }
        `,
        variables: {
          calendarId: input.calendarId,
          quantity: input.quantity,
          shippingAddress: input.shippingAddress,
        },
      }),
    })

    if (!response.ok) {
      throw new Error('Failed to create order')
    }

    const result = await response.json()

    if (result.errors) {
      throw new Error(result.errors[0]?.message || 'Failed to create order')
    }

    if (!result.data?.createOrder) {
      throw new Error('No payment intent returned from server')
    }

    return result.data.createOrder
  } catch (error) {
    console.error('Error creating order:', error)
    throw error
  }
}

/**
 * Confirm payment with Stripe
 * Uses Stripe Payment Element to confirm the payment
 */
export async function confirmPayment(
  stripe: Stripe,
  elements: StripeElements,
  returnUrl: string
): Promise<{ error?: { message: string } }> {
  try {
    const { error } = await stripe.confirmPayment({
      elements,
      confirmParams: {
        return_url: returnUrl,
      },
    })

    if (error) {
      return { error }
    }

    return {}
  } catch (error: any) {
    console.error('Error confirming payment:', error)
    return { error: { message: error.message || 'Payment confirmation failed' } }
  }
}

/**
 * Fetch order details by ID
 */
export async function fetchOrderById(orderId: string, authToken: string): Promise<any> {
  try {
    const response = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`,
      },
      body: JSON.stringify({
        query: `
          query GetOrder($id: ID!) {
            order(id: $id) {
              id
              quantity
              unitPrice
              totalPrice
              status
              shippingAddress
              paidAt
              shippedAt
              deliveredAt
              trackingNumber
              created
              updated
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
        `,
        variables: { id: orderId },
      }),
    })

    if (!response.ok) {
      throw new Error('Failed to fetch order')
    }

    const result = await response.json()

    if (result.errors) {
      throw new Error(result.errors[0]?.message || 'Failed to fetch order')
    }

    return result.data?.order || null
  } catch (error) {
    console.error('Error fetching order:', error)
    throw error
  }
}

/**
 * Fetch all orders for the authenticated user
 */
export async function fetchUserOrders(authToken: string): Promise<any[]> {
  try {
    const response = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`,
      },
      body: JSON.stringify({
        query: `
          query GetMyOrders {
            myOrders {
              id
              quantity
              unitPrice
              totalPrice
              status
              paidAt
              shippedAt
              deliveredAt
              trackingNumber
              created
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
            }
          }
        `,
      }),
    })

    if (!response.ok) {
      throw new Error('Failed to fetch orders')
    }

    const result = await response.json()

    if (result.errors) {
      throw new Error(result.errors[0]?.message || 'Failed to fetch orders')
    }

    return result.data?.myOrders || []
  } catch (error) {
    console.error('Error fetching orders:', error)
    throw error
  }
}

/**
 * Format order status for display
 */
export function formatOrderStatus(status: string): string {
  const statusMap: Record<string, string> = {
    PENDING: 'Pending Payment',
    PAID: 'Paid',
    PROCESSING: 'Processing',
    SHIPPED: 'Shipped',
    DELIVERED: 'Delivered',
    CANCELLED: 'Cancelled',
    REFUNDED: 'Refunded',
  }
  return statusMap[status] || status
}

/**
 * Get order status severity for PrimeVue Tag component
 */
export function getOrderStatusSeverity(
  status: string
): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
  const severityMap: Record<string, 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast'> = {
    PENDING: 'warn',
    PAID: 'info',
    PROCESSING: 'info',
    SHIPPED: 'success',
    DELIVERED: 'success',
    CANCELLED: 'danger',
    REFUNDED: 'secondary',
  }
  return severityMap[status] || 'secondary'
}

/**
 * Format currency amount
 */
export function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(amount)
}

/**
 * Calculate order total
 */
export function calculateOrderTotal(unitPrice: number, quantity: number): number {
  return unitPrice * quantity
}

/**
 * Fetch order by Stripe Payment Intent ID
 * Used after payment callback to find the order created by webhook
 * Returns null if order not found yet (webhook still processing)
 */
export async function fetchOrderByPaymentIntent(
  paymentIntentId: string,
  authToken: string
): Promise<any | null> {
  try {
    const response = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`,
      },
      body: JSON.stringify({
        query: `
          query GetOrderByPaymentIntent {
            myOrders(status: PAID) {
              id
              quantity
              unitPrice
              totalPrice
              status
              shippingAddress
              paidAt
              shippedAt
              deliveredAt
              trackingNumber
              created
              updated
              stripePaymentIntentId
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
        `,
      }),
    })

    if (!response.ok) {
      throw new Error('Failed to fetch orders')
    }

    const result = await response.json()

    if (result.errors) {
      throw new Error(result.errors[0]?.message || 'Failed to fetch orders')
    }

    const orders = result.data?.myOrders || []

    // Find the order with matching payment intent ID
    const order = orders.find((o: any) => o.stripePaymentIntentId === paymentIntentId)

    return order || null
  } catch (error) {
    console.error('Error fetching order by payment intent:', error)
    throw error
  }
}

// ============================================================================
// ADMIN FUNCTIONS
// ============================================================================

/**
 * Fetch all orders with optional status filter (admin only)
 */
export async function fetchAllOrdersAdmin(
  authToken: string,
  status?: string,
  limit: number = 100
): Promise<any[]> {
  try {
    const response = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`,
      },
      body: JSON.stringify({
        query: `
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
        `,
        variables: { status, limit },
      }),
    })

    if (!response.ok) {
      throw new Error('Failed to fetch orders')
    }

    const result = await response.json()

    if (result.errors) {
      throw new Error(result.errors[0]?.message || 'Failed to fetch orders')
    }

    return result.data?.allOrders || []
  } catch (error) {
    console.error('Error fetching all orders:', error)
    throw error
  }
}

/**
 * Update order status (admin only)
 */
export async function updateOrderStatusAdmin(
  orderId: string,
  input: OrderUpdateInput,
  authToken: string
): Promise<any> {
  try {
    const response = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`,
      },
      body: JSON.stringify({
        query: `
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
        `,
        variables: { id: orderId, input },
      }),
    })

    if (!response.ok) {
      throw new Error('Failed to update order status')
    }

    const result = await response.json()

    if (result.errors) {
      throw new Error(result.errors[0]?.message || 'Failed to update order status')
    }

    return result.data?.updateOrderStatus || null
  } catch (error) {
    console.error('Error updating order status:', error)
    throw error
  }
}
