<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const open = ref(false)

watch(
  () => route.fullPath,
  () => (open.value = false),
)

const links = computed(() => {
  const user = auth.user
  const items = [{ to: '/', label: t('nav.courses') }]
  if (!user) {
    return items
  }
  items.push(
    { to: '/rate', label: t('nav.rate') },
    { to: '/me/submissions', label: t('nav.submissions') },
    { to: '/group', label: t('nav.groups') },
    { to: '/me/recovery', label: t('recovery.title') },
  )
  if (user.roles.includes('moderator') || user.roles.includes('admin')) {
    items.push({ to: '/moderation', label: t('nav.moderation') })
  }
  if (user.roles.includes('admin')) {
    items.push({ to: '/admin', label: t('nav.admin') })
  }
  return items
})

async function signOut() {
  await auth.signOut().catch(() => {})
  await router.push('/')
}
</script>

<template>
  <header class="bar">
    <RouterLink to="/" class="brand">{{ t('app.title') }}</RouterLink>
    <span class="right">
      <span v-if="auth.user" class="who">{{ auth.user.email.split('@')[0] }}</span>
      <RouterLink v-else :to="{ name: 'signin' }">{{ t('auth.signIn') }}</RouterLink>
      <Button :label="t('nav.menu')" text :aria-expanded="open" @click="open = !open" />
    </span>
  </header>
  <nav v-if="open" class="menu">
    <RouterLink v-for="link in links" :key="link.to" :to="link.to">{{ link.label }}</RouterLink>
    <template v-if="auth.user">
      <span class="who">{{ t('auth.signedInAs', { email: auth.user.email }) }}</span>
      <Button :label="t('auth.signOut')" text @click="signOut" />
    </template>
    <template v-else>
      <RouterLink :to="{ name: 'signin' }">{{ t('auth.signIn') }}</RouterLink>
      <RouterLink :to="{ name: 'signup' }">{{ t('auth.signUp') }}</RouterLink>
    </template>
  </nav>
  <RouterView />
</template>

<style>
body {
  margin: 0;
  font-family: system-ui, sans-serif;
  background: var(--p-content-background);
  color: var(--p-text-color);
  color-scheme: light dark;
}

a {
  color: var(--p-primary-color);
}

main {
  max-width: 40rem;
  margin: 0 auto;
  padding: 1rem;
}

.auth form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.card {
  padding: 0.75rem 1rem;
  margin-bottom: 0.75rem;
  border: 1px solid var(--p-content-border-color, #e5e7eb);
  border-radius: 0.75rem;
}

.muted {
  color: var(--p-text-muted-color, #6b7280);
  font-size: 0.875rem;
}

.locked {
  color: var(--p-text-muted-color, #6b7280);
  font-style: italic;
}
</style>

<style scoped>
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 40rem;
  margin: 0 auto;
  padding: 0.5rem 1rem;
  border-bottom: 1px solid var(--p-content-border-color, #e5e7eb);
}

.brand {
  font-weight: 700;
  text-decoration: none;
  color: inherit;
}

.menu {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-width: 40rem;
  margin: 0 auto;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--p-content-border-color, #e5e7eb);
}

.right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.who {
  font-size: 0.875rem;
  color: var(--p-text-muted-color, #6b7280);
}

.menu .p-button {
  align-self: flex-start;
  padding-left: 0;
}
</style>
