<template>
  <div class="payment-callback-page">
    <div class="callback-container">
      <div v-if="!error">
        <ProgressSpinner />
        <h2>Processing payment...</h2>
        <p class="status-text">{{ statusMessage }}</p>
        <p class="help-text">Please wait while we confirm your order. This may take a few moments.</p>
      </div>
      <div v-else class="error-container">
        <i class="pi pi-times-circle error-icon"></i>
        <h2>Payment Issue</h2>
        <Message severity="error" :closable="false">
          {{ error }}
        </Message>
        <div class="action-buttons">
          <Button
            label="Go Home"
            icon="pi pi-home"
            @click="router.push('/')"
          />
          <Button
            label="Contact Support"
            icon="pi pi-question-circle"
            outlined
            @click="contactSupport"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '../../stores/authStore'
import { fetchOrderByPaymentIntent } from '../../services/orderService'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import Button from 'primevue/button'

const router = useRouter()
const route = useRoute()
const toast = useToast()
const authStore = useAuthStore()

const error = ref<string | null>(null)
const statusMessage = ref('Confirming payment with Stripe...')

/**
 * Poll for order creation after Stripe payment
 * The backend creates the order via webhook, which may take a few seconds
 */
const pollForOrder = async (paymentIntentId: string): Promise<string | null> => {
  const maxAttempts = 30 // Poll for up to 30 seconds
  const pollInterval = 1000 // Check every 1 second

  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    try {
      statusMessage.value = `Checking order status... (${attempt + 1}/${maxAttempts})`

      // Fetch order by payment intent ID
      const order = await fetchOrderByPaymentIntent(paymentIntentId, authStore.token!)

      if (order) {
        // Order found! Return the order ID
        return order.id
      }

      // Wait before next poll
      await new Promise(resolve => setTimeout(resolve, pollInterval))
    } catch (err: any) {
      console.error('Error polling for order:', err)
      // Continue polling even on error (might be a temporary issue)
    }
  }

  // Timeout - order not found
  return null
}

/**
 * Handle payment callback
 */
onMounted(async () => {
  try {
    // Check authentication
    if (!authStore.isAuthenticated) {
      error.value = 'You must be logged in to view order details.'
      return
    }

    // Extract payment_intent from URL
    const paymentIntentId = route.query.payment_intent as string

    if (!paymentIntentId) {
      error.value = 'Missing payment information. Please check your email for order confirmation.'
      return
    }

    // Check payment_intent_client_secret to verify this is a return from Stripe
    const clientSecret = route.query.payment_intent_client_secret as string
    if (!clientSecret) {
      error.value = 'Invalid payment confirmation. Please check your email for order confirmation.'
      return
    }

    // Check redirect_status to see if payment succeeded
    const redirectStatus = route.query.redirect_status as string
    if (redirectStatus === 'failed') {
      error.value = 'Payment was not successful. Please try again or contact support.'
      return
    }

    // Poll for order creation (webhook processing)
    statusMessage.value = 'Payment confirmed! Creating your order...'
    const orderId = await pollForOrder(paymentIntentId)

    if (!orderId) {
      // Timeout - show helpful message
      error.value = 'Your payment was successful, but order creation is taking longer than expected. ' +
        'You will receive an email confirmation shortly. Please check your email or contact support if you don\'t receive it within 10 minutes.'
      return
    }

    // Success! Redirect to order confirmation page
    toast.add({
      severity: 'success',
      summary: 'Order Complete',
      detail: 'Your order has been placed successfully!',
      life: 3000,
    })

    router.push(`/order/${orderId}/confirmation`)
  } catch (err: any) {
    console.error('Payment callback error:', err)
    error.value = err.message || 'Failed to process payment. Please check your email for order confirmation or contact support.'
  }
})

/**
 * Contact support (placeholder - would open support chat or email)
 */
const contactSupport = () => {
  window.location.href = 'mailto:support@example.com?subject=Order%20Issue'
}
</script>

<style scoped>
.payment-callback-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
  padding: 1rem;
}

.callback-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 3rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  max-width: 500px;
  text-align: center;
}

.callback-container h2 {
  font-size: 1.5rem;
  font-weight: 600;
  color: #111827;
  margin: 0.5rem 0;
}

.status-text {
  color: #3b82f6;
  font-weight: 500;
  margin: 0;
}

.help-text {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0;
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.error-icon {
  font-size: 4rem;
  color: #ef4444;
}

.action-buttons {
  display: flex;
  gap: 1rem;
  margin-top: 1rem;
  flex-wrap: wrap;
  justify-content: center;
}
</style>
