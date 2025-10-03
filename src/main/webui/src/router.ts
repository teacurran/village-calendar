import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('./view/CalendarGenerator.vue'),
    },
    {
      path: '/marketing',
      name: 'marketing',
      component: () => import('./view/CalendarMarketing.vue'),
    },
    {
      path: '/cart',
      name: 'cart',
      component: () => import('./view/Cart.vue'),
    },
    {
      path: '/checkout',
      name: 'checkout',
      component: () => import('./view/Checkout.vue'),
    },
    {
      path: '/order-confirmation',
      name: 'order-confirmation',
      component: () => import('./view/OrderConfirmation.vue'),
    },
  ],
})

export default router
