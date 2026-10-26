import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import PrimeVue from 'primevue/config'
import Aura from '@primeuix/themes/aura'

import App from './App.vue'
import router from './router'
import ro from './i18n/ro.json'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(createI18n({ legacy: false, locale: 'ro-RO', messages: { 'ro-RO': ro } }))
app.use(PrimeVue, { theme: { preset: Aura } })

app.mount('#app')
