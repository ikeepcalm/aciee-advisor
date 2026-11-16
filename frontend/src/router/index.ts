import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '@/stores/auth'
import { safeNext } from '@/api'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  // A new page starts at the top; hash targets are scrolled by the page itself (the guide).
  scrollBehavior: (to, from, saved) =>
    saved ?? (to.path === from.path || to.hash ? false : { top: 0 }),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      beforeEnter: (to) => ('year' in to.query ? { name: 'catalog', query: to.query } : true),
    },
    {
      path: '/courses',
      name: 'catalog',
      component: () => import('../views/CatalogView.vue'),
    },
    {
      path: '/courses/:itemId',
      name: 'course',
      component: () => import('../views/CourseView.vue'),
    },
    {
      path: '/professors',
      name: 'professors',
      component: () => import('../views/ProfessorsView.vue'),
    },
    {
      path: '/professors/:slug',
      name: 'professor',
      component: () => import('../views/ProfessorView.vue'),
    },
    { path: '/orar', redirect: '/timetable' },
    {
      path: '/timetable',
      name: 'timetable',
      component: () => import('../views/TimetableView.vue'),
    },
    {
      path: '/guide',
      name: 'guide',
      component: () => import('../views/GuideView.vue'),
    },
    {
      path: '/feedback',
      name: 'feedback',
      component: () => import('../views/FeedbackView.vue'),
    },
    {
      path: '/signup',
      name: 'signup',
      component: () => import('../views/SignUpView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/signin',
      name: 'signin',
      component: () => import('../views/SignInView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/verify',
      name: 'verify',
      component: () => import('../views/VerifyView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/group',
      name: 'group',
      component: () => import('../views/GroupView.vue'),
      meta: { signedIn: true },
    },
    {
      path: '/rate',
      name: 'rate',
      component: () => import('../views/RateView.vue'),
      meta: { signedIn: true },
    },
    {
      path: '/rate/:offeringId',
      name: 'rate-form',
      component: () => import('../views/RateFormView.vue'),
      meta: { signedIn: true },
    },
    {
      path: '/me/submissions',
      name: 'submissions',
      component: () => import('../views/SubmissionsView.vue'),
      meta: { signedIn: true },
    },
    {
      path: '/moderation',
      name: 'moderation',
      component: () => import('../views/ModerationView.vue'),
      meta: { role: ['moderator', 'admin'] },
    },
    {
      path: '/me/recovery',
      name: 'recovery',
      component: () => import('../views/RecoveryView.vue'),
      meta: { signedIn: true },
    },
    {
      path: '/join/:token',
      name: 'join',
      component: () => import('../views/JoinView.vue'),
      meta: { signedIn: true },
    },
    { path: '/admin', redirect: { name: 'admin-leaders' } },
    {
      path: '/admin/leaders',
      name: 'admin-leaders',
      component: () => import('../views/AdminLeadersView.vue'),
      meta: { role: 'admin' },
    },
    {
      path: '/admin/history',
      name: 'admin-history',
      component: () => import('../views/AdminHistoryView.vue'),
      meta: { role: 'admin' },
    },
    {
      path: '/admin/reports',
      name: 'admin-reports',
      component: () => import('../views/AdminReportsView.vue'),
      meta: { role: 'admin' },
    },
    {
      path: '/admin/:table',
      name: 'admin-table',
      component: () => import('../views/AdminTableView.vue'),
      meta: { role: 'admin' },
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('../views/ForbiddenView.vue'),
    },
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
  if (
    to.meta.role &&
    ![to.meta.role].flat().some((role) => auth.user!.roles.includes(role as string))
  ) {
    return { name: 'forbidden' }
  }
})

export default router
