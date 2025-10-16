/**
 * Payment Service
 * Handles integration with multiple payment providers:
 * - Stripe (Credit Cards, Google Pay, Apple Pay)
 * - PayPal
 * - Shop Pay
 * - Affirm
 */

// Stripe types (simplified - would normally import from @stripe/stripe-js)
interface StripeGooglePayPaymentData {
  token: string
  paymentMethodData: any
}

interface StripePaymentMethod {
  id: string
  type: string
}

/**
 * Payment service singleton
 */
class PaymentService {
  private stripePromise: Promise<any> | null = null
  private googlePayClient: any = null
  private paypalSDKLoaded = false
  private shopPayLoaded = false
  private affirmLoaded = false

  /**
   * Initialize Stripe
   */
  private async loadStripe(): Promise<any> {
    if (!this.stripePromise) {
      // In production, load Stripe from CDN or npm package
      // For now, return a mock for development
      console.warn('Stripe not loaded - using mock')
      this.stripePromise = Promise.resolve({
        elements: () => ({
          create: () => ({
            mount: () => {},
            on: () => {},
          }),
        }),
        confirmCardPayment: async () => ({
          paymentIntent: { id: 'mock_payment_intent' },
        }),
      })
    }
    return this.stripePromise
  }

  /**
   * Check if Google Pay is available and ready
   */
  async isGooglePayReady(amount: number): Promise<boolean> {
    try {
      if (!window.google?.payments) {
        return false
      }

      if (!this.googlePayClient) {
        this.googlePayClient = new window.google.payments.api.PaymentsClient({
          environment: 'TEST', // Change to 'PRODUCTION' in production
        })
      }

      const isReadyToPay = await this.googlePayClient.isReadyToPay({
        apiVersion: 2,
        apiVersionMinor: 0,
        allowedPaymentMethods: [
          {
            type: 'CARD',
            parameters: {
              allowedAuthMethods: ['PAN_ONLY', 'CRYPTOGRAM_3DS'],
              allowedCardNetworks: ['MASTERCARD', 'VISA'],
            },
          },
        ],
      })

      return isReadyToPay.result
    } catch (error) {
      console.error('Google Pay availability check failed:', error)
      return false
    }
  }

  /**
   * Process Google Pay payment
   */
  async processGooglePayPayment(cartData: any): Promise<StripeGooglePayPaymentData> {
    if (!this.googlePayClient) {
      throw new Error('Google Pay not initialized')
    }

    const paymentDataRequest = {
      apiVersion: 2,
      apiVersionMinor: 0,
      allowedPaymentMethods: [
        {
          type: 'CARD',
          parameters: {
            allowedAuthMethods: ['PAN_ONLY', 'CRYPTOGRAM_3DS'],
            allowedCardNetworks: ['MASTERCARD', 'VISA'],
          },
          tokenizationSpecification: {
            type: 'PAYMENT_GATEWAY',
            parameters: {
              gateway: 'stripe',
              'stripe:version': '2023-10-16',
              'stripe:publishableKey': 'pk_test_placeholder', // Replace with actual key
            },
          },
        },
      ],
      merchantInfo: {
        merchantName: 'Village Calendar',
        merchantId: '01234567890123456789', // Replace with actual merchant ID
      },
      transactionInfo: {
        totalPriceStatus: 'FINAL',
        totalPrice: cartData.total.toFixed(2),
        currencyCode: 'USD',
        countryCode: 'US',
      },
    }

    const paymentData = await this.googlePayClient.loadPaymentData(paymentDataRequest)

    return {
      token: paymentData.paymentMethodData.tokenizationData.token,
      paymentMethodData: paymentData.paymentMethodData,
    }
  }

  /**
   * Initialize PayPal SDK
   */
  async initializePayPal(): Promise<void> {
    if (this.paypalSDKLoaded) {
      return
    }

    return new Promise((resolve, reject) => {
      if (window.paypal) {
        this.paypalSDKLoaded = true
        resolve()
        return
      }

      // Load PayPal SDK
      const script = document.createElement('script')
      script.src =
        'https://www.paypal.com/sdk/js?client-id=YOUR_CLIENT_ID&currency=USD&components=buttons'
      script.async = true
      script.onload = () => {
        this.paypalSDKLoaded = true
        resolve()
      }
      script.onerror = () => {
        reject(new Error('Failed to load PayPal SDK'))
      }
      document.head.appendChild(script)
    })
  }

  /**
   * Create PayPal buttons
   */
  async createPayPalButtons(config: {
    createOrder: () => Promise<string>
    onApprove: (data: any) => Promise<void>
    onError?: (err: any) => void
  }): Promise<any> {
    await this.initializePayPal()

    if (!window.paypal) {
      throw new Error('PayPal SDK not loaded')
    }

    return window.paypal.Buttons({
      createOrder: config.createOrder,
      onApprove: config.onApprove,
      onError: config.onError,
    })
  }

  /**
   * Initialize Shop Pay
   */
  async initializeShopPay(): Promise<boolean> {
    if (this.shopPayLoaded) {
      return true
    }

    // Shop Pay requires merchant to be enrolled
    // For now, return false as it's not widely available
    console.warn('Shop Pay not available')
    return false
  }

  /**
   * Initialize Affirm SDK
   */
  async initializeAffirm(): Promise<void> {
    if (this.affirmLoaded) {
      return
    }

    return new Promise((resolve, reject) => {
      if (window.affirm) {
        this.affirmLoaded = true
        resolve()
        return
      }

      // Load Affirm SDK
      const script = document.createElement('script')
      script.src = 'https://cdn1.affirm.com/js/v2/affirm.js'
      script.async = true
      script.onload = () => {
        // Initialize Affirm with public key
        if (window.affirm) {
          window.affirm.ui.ready(() => {
            window.affirm.ui.setPublicKey('YOUR_PUBLIC_KEY') // Replace with actual key
          })
          this.affirmLoaded = true
          resolve()
        } else {
          reject(new Error('Affirm SDK loaded but not available'))
        }
      }
      script.onerror = () => {
        reject(new Error('Failed to load Affirm SDK'))
      }
      document.head.appendChild(script)
    })
  }

  /**
   * Open Affirm checkout modal
   */
  async openAffirmCheckout(checkoutData: {
    merchant: { name: string }
    items: Array<{ display_name: string; unit_price: number; qty: number }>
    shipping: { name: { first: string; last: string }; address: any }
    billing: { name: { first: string; last: string }; address: any }
    total: number
  }): Promise<void> {
    await this.initializeAffirm()

    if (!window.affirm) {
      throw new Error('Affirm SDK not loaded')
    }

    return new Promise((resolve, reject) => {
      window.affirm.checkout(checkoutData)
      window.affirm.checkout.open({
        onFail: (error: any) => {
          reject(error)
        },
        onSuccess: () => {
          resolve()
        },
      })
    })
  }

  /**
   * Create Stripe payment method from card element
   */
  async createPaymentMethod(cardElement: any): Promise<StripePaymentMethod> {
    const stripe = await this.loadStripe()
    const { paymentMethod, error } = await stripe.createPaymentMethod({
      type: 'card',
      card: cardElement,
    })

    if (error) {
      throw new Error(error.message)
    }

    return paymentMethod
  }

  /**
   * Confirm card payment with Stripe
   */
  async confirmCardPayment(
    clientSecret: string,
    paymentMethodId: string
  ): Promise<any> {
    const stripe = await this.loadStripe()
    const result = await stripe.confirmCardPayment(clientSecret, {
      payment_method: paymentMethodId,
    })

    if (result.error) {
      throw new Error(result.error.message)
    }

    return result.paymentIntent
  }
}

// Export singleton instance
export const paymentService = new PaymentService()

// Type declarations for external payment SDKs
declare global {
  interface Window {
    google?: {
      payments?: {
        api: {
          PaymentsClient: new (config: any) => any
        }
      }
    }
    paypal?: any
    affirm?: any
  }
}
