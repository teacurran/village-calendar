/**
 * GraphQL mutations for order management
 */

/**
 * Place an order for printed calendars.
 * Creates a Stripe PaymentIntent and returns the checkout details.
 *
 * The response includes:
 * - id: Stripe PaymentIntent ID
 * - clientSecret: Used for Stripe.js payment confirmation
 * - amount: Order amount in cents
 * - calendarId: Associated calendar ID
 * - quantity: Number of items ordered
 * - status: Payment status
 */
export const PLACE_ORDER_MUTATION = `
  mutation PlaceOrder($input: PlaceOrderInput!) {
    placeOrder(input: $input) {
      id
      clientSecret
      amount
      calendarId
      quantity
      status
    }
  }
`;

/**
 * Alternative order creation mutation (legacy).
 * Use PLACE_ORDER_MUTATION for new implementations.
 */
export const CREATE_ORDER_MUTATION = `
  mutation CreateOrder(
    $calendarId: String!
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
`;

/**
 * Cancel an existing order.
 * Requires authentication and order ownership.
 * Initiates refund for paid orders.
 */
export const CANCEL_ORDER_MUTATION = `
  mutation CancelOrder($orderId: String!, $reason: String) {
    cancelOrder(orderId: $orderId, reason: $reason) {
      id
      status
      notes
    }
  }
`;

/**
 * Query to fetch user's orders.
 */
export const MY_ORDERS_QUERY = `
  query MyOrders($status: OrderStatus) {
    myOrders(status: $status) {
      id
      status
      quantity
      totalPrice
      unitPrice
      created
      paidAt
      shippedAt
      deliveredAt
      trackingNumber
      shippingAddress
      calendar {
        id
        name
        year
      }
    }
  }
`;

/**
 * Query to fetch a specific order by ID.
 */
export const ORDER_QUERY = `
  query GetOrder($id: String!) {
    order(id: $id) {
      id
      status
      quantity
      totalPrice
      unitPrice
      created
      paidAt
      shippedAt
      deliveredAt
      trackingNumber
      shippingAddress
      notes
      calendar {
        id
        name
        year
        template {
          id
          name
        }
      }
    }
  }
`;

/**
 * TypeScript interfaces for order-related types
 */

export interface AddressInput {
  street: string;
  street2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export interface PlaceOrderInput {
  calendarId: string;
  productType: "WALL_CALENDAR" | "DESK_CALENDAR" | "POSTER";
  quantity: number;
  shippingAddress: AddressInput;
}

export interface PaymentIntentResponse {
  id: string;
  clientSecret: string;
  amount: number;
  calendarId: string;
  quantity: number;
  status: string;
}

export interface OrderResponse {
  id: string;
  status: string;
  quantity: number;
  totalPrice: number;
  unitPrice: number;
  created: string;
  paidAt?: string;
  shippedAt?: string;
  deliveredAt?: string;
  trackingNumber?: string;
  shippingAddress: Record<string, any>;
  notes?: string;
  calendar: {
    id: string;
    name: string;
    year: number;
    template?: {
      id: string;
      name: string;
    };
  };
}
