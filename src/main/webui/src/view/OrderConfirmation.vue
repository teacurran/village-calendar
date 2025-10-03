<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ROUTE_NAMES } from '../navigation/routes'
import Breadcrumb from 'primevue/breadcrumb'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Timeline from 'primevue/timeline'
import { homeBreadcrumb } from '../navigation/breadcrumbs'

const { t } = useI18n({ useScope: 'global' })
const router = useRouter()

const order = ref(null)
const estimatedDelivery = ref(null)

const breadcrumbs = ref([
  { label: 'Store', url: '/store/products' },
  { label: 'Order Confirmation' },
])

const orderTimeline = ref([
  {
    status: 'Order Placed',
    date: new Date().toLocaleDateString(),
    icon: 'pi-shopping-cart',
    color: '#9C27B0',
    completed: true,
  },
  {
    status: 'Processing',
    date: 'In Progress',
    icon: 'pi-cog',
    color: '#673AB7',
    completed: false,
  },
  {
    status: 'Shipped',
    date: 'Pending',
    icon: 'pi-send',
    color: '#FF9800',
    completed: false,
  },
  {
    status: 'Delivered',
    date: 'Pending',
    icon: 'pi-check',
    color: '#607D8B',
    completed: false,
  },
])

onMounted(() => {
  // Scroll to top
  window.scrollTo({ top: 0, behavior: 'smooth' })

  // Get order data from session
  const orderData = sessionStorage.getItem('lastOrder')
  if (orderData) {
    order.value = JSON.parse(orderData)
    sessionStorage.removeItem('lastOrder')

    // Calculate estimated delivery
    if (order.value.shippingMethod) {
      const days = order.value.shippingMethod.estimatedDays
      const deliveryDate = new Date()
      deliveryDate.setDate(deliveryDate.getDate() + days[1])
      estimatedDelivery.value = deliveryDate.toLocaleDateString('en-US', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    }
  } else {
    // Redirect if no order data
    router.push({ name: ROUTE_NAMES.STORE_PRODUCTS })
  }
})

function formatCurrency(amount) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(amount)
}

function printOrder() {
  window.print()
}

function continueShopping() {
  router.push({ name: ROUTE_NAMES.STORE_PRODUCTS })
}
</script>

<template>
  <Breadcrumb :home="homeBreadcrumb" :model="breadcrumbs" class="mb-4" />

  <div class="order-confirmation-container">
    <!-- Success message -->
    <Card class="confirmation-header mb-4">
      <template #content>
        <div class="text-center">
          <div class="success-icon mb-4">
            <i class="pi pi-check-circle text-6xl text-green-500"></i>
          </div>

          <h1 class="text-3xl font-bold mb-2">Thank You for Your Order!</h1>
          <p class="text-lg text-gray-600 mb-4">
            Your order has been successfully placed and is being processed.
          </p>

          <div v-if="order" class="order-number-box p-3 bg-gray-50 border-round mb-4 inline-block">
            <div class="text-sm text-gray-600 mb-1">Order Number</div>
            <div class="text-2xl font-bold text-primary">
              {{ order.orderNumber || 'VC-' + Date.now() }}
            </div>
          </div>

          <p class="text-gray-600">
            A confirmation email has been sent to
            <strong>{{ order?.email }}</strong>
          </p>
        </div>
      </template>
    </Card>

    <div class="grid">
      <!-- Order details -->
      <div class="col-12 lg:col-8">
        <!-- Order timeline -->
        <Card class="mb-4">
          <template #title>
            <h3>Order Status</h3>
          </template>
          <template #content>
            <Timeline :value="orderTimeline" align="horizontal" class="order-timeline">
              <template #marker="slotProps">
                <span
                  class="timeline-marker"
                  :class="{ completed: slotProps.item.completed }"
                  :style="{
                    backgroundColor: slotProps.item.completed ? slotProps.item.color : '#e0e0e0',
                  }"
                >
                  <i :class="`pi ${slotProps.item.icon}`"></i>
                </span>
              </template>
              <template #content="slotProps">
                <div class="text-center">
                  <div class="font-semibold">{{ slotProps.item.status }}</div>
                  <div class="text-sm text-gray-600">{{ slotProps.item.date }}</div>
                </div>
              </template>
            </Timeline>

            <div
              v-if="estimatedDelivery"
              class="estimated-delivery mt-4 p-3 bg-blue-50 border-round"
            >
              <div class="flex align-items-center gap-2">
                <i class="pi pi-calendar text-blue-600"></i>
                <span>
                  Estimated Delivery: <strong>{{ estimatedDelivery }}</strong>
                </span>
              </div>
            </div>
          </template>
        </Card>

        <!-- Order items -->
        <Card class="mb-4">
          <template #title>
            <h3>Order Items</h3>
          </template>
          <template #content>
            <div v-if="order?.items" class="order-items">
              <div
                v-for="item in order.items"
                :key="item.id"
                class="order-item pb-3 mb-3 border-bottom-1 border-gray-200"
              >
                <div class="flex gap-3">
                  <!-- Product image placeholder -->
                  <div class="item-image">
                    <div
                      class="w-5rem h-5rem bg-gray-100 border-round flex align-items-center justify-content-center"
                    >
                      <i class="pi pi-box text-2xl text-gray-400"></i>
                    </div>
                  </div>

                  <!-- Product details -->
                  <div class="item-details flex-1">
                    <h4 class="mb-1">{{ item.productName }}</h4>
                    <div class="text-sm text-gray-600 mb-2">
                      <span v-if="item.configuration">{{ item.configuration }}</span>
                    </div>
                    <div class="flex justify-content-between align-items-center">
                      <span class="text-gray-600">Quantity: {{ item.quantity }}</span>
                      <span class="font-semibold">{{ formatCurrency(item.lineTotal) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Order totals -->
            <div class="order-totals pt-3">
              <div class="flex justify-content-between mb-2">
                <span>Subtotal</span>
                <span>{{ formatCurrency(order?.subtotal || 0) }}</span>
              </div>
              <div class="flex justify-content-between mb-2">
                <span>Shipping</span>
                <span>{{ formatCurrency(order?.shippingCost || 0) }}</span>
              </div>
              <div class="flex justify-content-between mb-2">
                <span>Tax</span>
                <span>{{ formatCurrency(order?.taxAmount || 0) }}</span>
              </div>
              <div class="flex justify-content-between pt-2 border-top-1 border-gray-300">
                <span class="text-xl font-bold">Total</span>
                <span class="text-xl font-bold text-primary">
                  {{ formatCurrency(order?.totalAmount || 0) }}
                </span>
              </div>
            </div>
          </template>
        </Card>

        <!-- Addresses -->
        <Card>
          <template #title>
            <h3>Delivery Information</h3>
          </template>
          <template #content>
            <div class="grid">
              <div class="col-12 md:col-6">
                <h4 class="mb-3">Shipping Address</h4>
                <div v-if="order?.shippingAddress" class="text-gray-700">
                  {{ order.shippingAddress.firstName }} {{ order.shippingAddress.lastName }}<br />
                  <span v-if="order.shippingAddress.company">
                    {{ order.shippingAddress.company }}<br />
                  </span>
                  {{ order.shippingAddress.address1 }}<br />
                  <span v-if="order.shippingAddress.address2">
                    {{ order.shippingAddress.address2 }}<br />
                  </span>
                  {{ order.shippingAddress.city }}, {{ order.shippingAddress.state }}
                  {{ order.shippingAddress.postalCode }}<br />
                  {{ order.shippingAddress.phone }}
                </div>
              </div>

              <div class="col-12 md:col-6">
                <h4 class="mb-3">Billing Address</h4>
                <div v-if="order?.billingAddress" class="text-gray-700">
                  <span v-if="order.billingSameAsShipping"> Same as shipping address </span>
                  <template v-else>
                    {{ order.billingAddress.firstName }} {{ order.billingAddress.lastName }}<br />
                    <span v-if="order.billingAddress.company">
                      {{ order.billingAddress.company }}<br />
                    </span>
                    {{ order.billingAddress.address1 }}<br />
                    <span v-if="order.billingAddress.address2">
                      {{ order.billingAddress.address2 }}<br />
                    </span>
                    {{ order.billingAddress.city }}, {{ order.billingAddress.state }}
                    {{ order.billingAddress.postalCode }}
                  </template>
                </div>
              </div>
            </div>

            <div class="mt-4">
              <h4 class="mb-3">Shipping Method</h4>
              <div v-if="order?.shippingMethod" class="text-gray-700">
                {{ order.shippingMethod.name }} - {{ order.shippingMethod.description }}
              </div>
            </div>
          </template>
        </Card>
      </div>

      <!-- Actions sidebar -->
      <div class="col-12 lg:col-4">
        <!-- Quick actions -->
        <Card class="mb-4">
          <template #title>
            <h3>What's Next?</h3>
          </template>
          <template #content>
            <div class="next-steps">
              <div class="flex align-items-start gap-3 mb-3">
                <i class="pi pi-envelope text-primary"></i>
                <div>
                  <div class="font-semibold mb-1">Check Your Email</div>
                  <div class="text-sm text-gray-600">
                    We've sent a confirmation with order details and tracking information.
                  </div>
                </div>
              </div>

              <div class="flex align-items-start gap-3 mb-3">
                <i class="pi pi-truck text-primary"></i>
                <div>
                  <div class="font-semibold mb-1">Track Your Package</div>
                  <div class="text-sm text-gray-600">
                    You'll receive tracking information once your order ships.
                  </div>
                </div>
              </div>

              <div class="flex align-items-start gap-3">
                <i class="pi pi-phone text-primary"></i>
                <div>
                  <div class="font-semibold mb-1">Need Help?</div>
                  <div class="text-sm text-gray-600">
                    Contact us at (617) 776-4948 or support@villagecompute.com
                  </div>
                </div>
              </div>
            </div>

            <div class="action-buttons mt-4">
              <Button
                label="Continue Shopping"
                icon="pi pi-shopping-bag"
                class="w-full mb-2"
                @click="continueShopping"
              />
              <Button
                label="Print Order"
                icon="pi pi-print"
                class="w-full p-button-outlined"
                @click="printOrder"
              />
            </div>
          </template>
        </Card>

        <!-- Create account prompt -->
        <Card v-if="!order?.userId">
          <template #title>
            <h3>Save Time Next Time!</h3>
          </template>
          <template #content>
            <p class="text-gray-600 mb-3">
              Create an account to track orders, save addresses, and checkout faster next time.
            </p>
            <Button
              label="Create Account"
              icon="pi pi-user-plus"
              class="w-full"
              @click="() => router.push('/register')"
            />
          </template>
        </Card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-confirmation-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem;
}

.success-icon {
  animation: successPulse 1s ease-in-out;
}

@keyframes successPulse {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.order-number-box {
  border: 2px dashed var(--primary-color);
}

/* Timeline customization */
.order-timeline :deep(.p-timeline-event-connector) {
  background-color: var(--surface-200);
}

.timeline-marker {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  transition: all 0.3s ease;
}

.timeline-marker.completed {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.timeline-marker i {
  font-size: 1.2rem;
}

/* Order items */
.order-item:last-child {
  border-bottom: none !important;
  margin-bottom: 0 !important;
  padding-bottom: 0 !important;
}

/* Estimated delivery */
.estimated-delivery {
  border: 1px solid #90caf9;
}

/* Next steps */
.next-steps i {
  font-size: 1.25rem;
  margin-top: 0.25rem;
}

/* Print styles */
@media print {
  .action-buttons,
  .breadcrumb,
  .pi-print {
    display: none !important;
  }

  .order-confirmation-container {
    padding: 0;
  }

  .card {
    box-shadow: none !important;
    border: 1px solid #ddd !important;
  }
}

/* Responsive */
@media (max-width: 991px) {
  .order-confirmation-container {
    padding: 1rem;
  }

  .order-timeline :deep(.p-timeline) {
    flex-direction: column;
  }

  .order-timeline :deep(.p-timeline-event-connector) {
    width: 2px;
    height: 100%;
  }
}
</style>
