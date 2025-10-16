// Route name constants for type-safe routing
export const ROUTE_NAMES = {
  HOME: 'home',
  MARKETING: 'marketing',
  CART: 'cart',
  CHECKOUT: 'checkout',
  ORDER_CONFIRMATION: 'order-confirmation',
} as const

export type RouteName = (typeof ROUTE_NAMES)[keyof typeof ROUTE_NAMES]
