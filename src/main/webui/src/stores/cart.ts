// ./stores/cart.ts
import { defineStore } from 'pinia'
import { useUserStore } from './user'

export interface CartItem {
  id: string
  templateId: string
  templateName: string
  year: number
  quantity: number
  unitPrice: number
  lineTotal: number
  configuration?: {
    coverImage?: string
    customization?: Record<string, any>
  }
}

export interface Cart {
  id: string
  subtotal: number
  taxAmount: number
  totalAmount: number
  itemCount: number
  items: CartItem[]
}

export const useCartStore = defineStore('cart', {
  state: () => ({
    cart: null as Cart | null,
    loading: false,
    error: null as string | null,
    isOpen: false,
    lastFetchTime: 0,
  }),

  actions: {
    async fetchCart(force: boolean = false) {
      // Skip if we recently fetched (within 5 seconds) unless forced
      const now = Date.now()
      if (!force && this.lastFetchTime && now - this.lastFetchTime < 5000) {
        return
      }

      this.loading = true
      this.error = null
      try {
        const response = await fetch('/graphql', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            query: `
              query GetCart {
                cart {
                  id
                  subtotal
                  taxAmount
                  totalAmount
                  itemCount
                  items {
                    id
                    templateId
                    templateName
                    year
                    quantity
                    unitPrice
                    lineTotal
                    configuration
                  }
                }
              }
            `,
          }),
        })

        if (!response.ok) {
          throw new Error('Failed to fetch cart')
        }

        const result = await response.json()

        if (result.errors) {
          throw new Error(result.errors[0]?.message || 'GraphQL error')
        }

        if (result.data?.cart) {
          this.cart = result.data.cart
          this.lastFetchTime = now
        } else {
          // Initialize empty cart
          this.cart = {
            id: '',
            subtotal: 0,
            taxAmount: 0,
            totalAmount: 0,
            itemCount: 0,
            items: [],
          }
        }
      } catch (err: any) {
        this.error = err.message || 'Failed to fetch cart'
        // Initialize empty cart on error
        this.cart = {
          id: '',
          subtotal: 0,
          taxAmount: 0,
          totalAmount: 0,
          itemCount: 0,
          items: [],
        }
      } finally {
        this.loading = false
      }
    },

    async addToCart(
      templateId: string,
      templateName: string,
      year: number,
      quantity: number,
      unitPrice: number,
      configuration?: any
    ) {
      this.loading = true
      this.error = null
      try {
        const response = await fetch('/graphql', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            query: `
              mutation AddToCart($input: AddToCartInput!) {
                addToCart(input: $input) {
                  id
                  subtotal
                  taxAmount
                  totalAmount
                  itemCount
                  items {
                    id
                    templateId
                    templateName
                    year
                    quantity
                    unitPrice
                    lineTotal
                    configuration
                  }
                }
              }
            `,
            variables: {
              input: {
                templateId,
                templateName,
                year,
                quantity,
                unitPrice,
                configuration: configuration ? JSON.stringify(configuration) : null,
              },
            },
          }),
        })

        if (!response.ok) {
          throw new Error('Failed to add item to cart')
        }

        const result = await response.json()

        if (result.errors) {
          throw new Error(result.errors[0]?.message || 'Failed to add item to cart')
        }

        if (result.data?.addToCart) {
          this.cart = result.data.addToCart
          this.lastFetchTime = Date.now()
        }
      } catch (err: any) {
        this.error = err.message || 'Failed to add item to cart'
        throw err
      } finally {
        this.loading = false
      }
    },

    async updateQuantity(itemId: string, quantity: number) {
      this.loading = true
      this.error = null
      try {
        const response = await fetch('/graphql', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            query: `
              mutation UpdateCartItemQuantity($itemId: ID!, $quantity: Int!) {
                updateCartItemQuantity(itemId: $itemId, quantity: $quantity) {
                  id
                  subtotal
                  taxAmount
                  totalAmount
                  itemCount
                  items {
                    id
                    templateId
                    templateName
                    year
                    quantity
                    unitPrice
                    lineTotal
                    configuration
                  }
                }
              }
            `,
            variables: {
              itemId,
              quantity,
            },
          }),
        })

        if (!response.ok) {
          throw new Error('Failed to update item quantity')
        }

        const result = await response.json()

        if (result.errors) {
          throw new Error(result.errors[0]?.message || 'Failed to update item')
        }

        if (result.data?.updateCartItemQuantity) {
          this.cart = result.data.updateCartItemQuantity
          this.lastFetchTime = Date.now()
        }
      } catch (err: any) {
        this.error = err.message || 'Failed to update item quantity'
        throw err
      } finally {
        this.loading = false
      }
    },

    async removeFromCart(itemId: string) {
      this.loading = true
      this.error = null
      try {
        const response = await fetch('/graphql', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            query: `
              mutation RemoveFromCart($itemId: ID!) {
                removeFromCart(itemId: $itemId) {
                  id
                  subtotal
                  taxAmount
                  totalAmount
                  itemCount
                  items {
                    id
                    templateId
                    templateName
                    year
                    quantity
                    unitPrice
                    lineTotal
                    configuration
                  }
                }
              }
            `,
            variables: {
              itemId,
            },
          }),
        })

        if (!response.ok) {
          throw new Error('Failed to remove item from cart')
        }

        const result = await response.json()

        if (result.errors) {
          throw new Error(result.errors[0]?.message || 'Failed to remove item')
        }

        if (result.data?.removeFromCart) {
          this.cart = result.data.removeFromCart
          this.lastFetchTime = Date.now()
        }
      } catch (err: any) {
        this.error = err.message || 'Failed to remove item from cart'
        throw err
      } finally {
        this.loading = false
      }
    },

    async clearCart() {
      this.loading = true
      this.error = null
      try {
        const response = await fetch('/graphql', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            query: `
              mutation ClearCart {
                clearCart {
                  id
                  itemCount
                }
              }
            `,
          }),
        })

        if (!response.ok) {
          throw new Error('Failed to clear cart')
        }

        const result = await response.json()

        if (result.errors) {
          throw new Error(result.errors[0]?.message || 'Failed to clear cart')
        }

        // Fetch the updated cart state
        await this.fetchCart(true)
      } catch (err: any) {
        this.error = err.message || 'Failed to clear cart'
        throw err
      } finally {
        this.loading = false
      }
    },

    toggleCart() {
      this.isOpen = !this.isOpen
    },

    openCart() {
      this.isOpen = true
    },

    closeCart() {
      this.isOpen = false
    },

    clearError() {
      this.error = null
    },
  },

  getters: {
    items: (state) => state.cart?.items || [],
    itemCount: (state) => state.cart?.itemCount || 0,
    totalAmount: (state) => state.cart?.totalAmount || 0,
    subtotal: (state) => state.cart?.subtotal || 0,
    taxAmount: (state) => state.cart?.taxAmount || 0,
    totalDisplayAmount: (state) => {
      const total = state.cart?.totalAmount || 0
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD',
      }).format(total)
    },
    isEmpty: (state) => !state.cart || state.cart.items.length === 0,
    hasItems: (state) => state.cart && state.cart.items.length > 0,
  },
})
