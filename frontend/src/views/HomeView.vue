<script setup lang="ts">
import Button from 'primevue/button'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<template>
  <main>
    <h1>{{ $t('app.title') }}</h1>
    <p>{{ $t('app.tagline') }}</p>
    <p v-if="auth.user">
      {{ $t('auth.signedInAs', { email: auth.user.email }) }}
      <Button :label="$t('auth.signOut')" link @click="auth.signOut().catch(() => {})" />
    </p>
    <nav v-if="auth.user">
      <RouterLink :to="{ name: 'rate' }">{{ $t('nav.rate') }}</RouterLink> ·
      <RouterLink :to="{ name: 'group' }">{{ $t('nav.groups') }}</RouterLink>
      <template v-if="auth.user.roles.includes('admin')">
        · <RouterLink :to="{ name: 'admin-leaders' }">{{ $t('nav.admin') }}</RouterLink>
      </template>
    </nav>
    <p v-else>
      <RouterLink :to="{ name: 'signin' }">{{ $t('auth.signIn') }}</RouterLink> ·
      <RouterLink :to="{ name: 'signup' }">{{ $t('auth.signUp') }}</RouterLink>
    </p>
  </main>
</template>
