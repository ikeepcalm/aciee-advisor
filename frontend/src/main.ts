import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@fontsource/chakra-petch/600.css'
import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/500.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/martian-mono/400.css'
import '@fontsource/martian-mono/500.css'
import './app.css'
import './theme'

import App from './App.vue'
import router from './router'
import { i18n } from './i18n'

createApp(App).use(createPinia()).use(router).use(i18n).mount('#app')
