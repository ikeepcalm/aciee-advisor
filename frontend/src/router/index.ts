import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '@/stores/auth'
import { safeNext } from '@/api'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/signup', name: 'signup', component: () => import('../views/SignUpView.vue'), meta: { guestOnly: true } },
    { path: '/signin', name: 'signin', component: () => import('../views/SignInView.vue'), meta: { guestOnly: true } },
    { path: '/verify', name: 'verify', component: () => import('../views/VerifyView.vue'), meta: { guestOnly: true } },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!to.meta.guestOnly) {
    auth.load()
    return
  }
  await auth.load()
  if (auth.user) {
    return safeNext(to.query)
  }
})

export default router
