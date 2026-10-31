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
    { path: '/group', name: 'group', component: () => import('../views/GroupView.vue'), meta: { signedIn: true } },
    { path: '/join/:token', name: 'join', component: () => import('../views/JoinView.vue'), meta: { signedIn: true } },
    { path: '/admin', redirect: { name: 'admin-leaders' } },
    { path: '/admin/leaders', name: 'admin-leaders', component: () => import('../views/AdminLeadersView.vue'), meta: { role: 'admin' } },
    { path: '/admin/history', name: 'admin-history', component: () => import('../views/AdminHistoryView.vue'), meta: { role: 'admin' } },
    { path: '/admin/:table', name: 'admin-table', component: () => import('../views/AdminTableView.vue'), meta: { role: 'admin' } },
    { path: '/forbidden', name: 'forbidden', component: () => import('../views/ForbiddenView.vue') },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!to.meta.guestOnly && !to.meta.signedIn && !to.meta.role) {
    auth.load()
    return
  }
  await auth.load()
  if (to.meta.guestOnly) {
    return auth.user ? safeNext(to.query) : undefined
  }
  if (!auth.user) {
    return { name: 'signin', query: { next: to.fullPath } }
  }
  if (to.meta.role && !auth.user.roles.includes(to.meta.role as string)) {
    return { name: 'forbidden' }
  }
})

export default router
