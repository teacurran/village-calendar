import { createApp } from 'vue'
import Ripple from 'primevue/ripple'
import './styles/main.css'
import i18n from './i18n'

import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import StyleClass from 'primevue/styleclass'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'

const app = createApp(App)
app.use(router)
app.use(createPinia())
app.use(PrimeVue, {
  theme: {
    preset: Aura,
    options: {
      prefix: 'p',
      darkModeSelector: '.p-dark',
      cssLayer: {
        name: 'primevue',
        order: 'base, primevue, theme',
      },
    },
  },
  ripple: true,
})

app.use(ToastService)
app.use(ConfirmationService)
app.directive('styleclass', StyleClass)
app.directive('ripple', Ripple)
app.use(i18n)
app.mount('#app')
