import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/500.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/martian-mono/400.css'
import '@fontsource/martian-mono/500.css'
import './app.css'
import './theme'

import App from './App.vue'
import router from './router'
import { i18n, localeReady } from './i18n'

localeReady.then(() => createApp(App).use(createPinia()).use(router).use(i18n).mount('#app'))

if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () =>
    navigator.serviceWorker.register('/sw.js').catch(() => undefined),
  )
}
