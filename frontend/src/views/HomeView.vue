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
    <p v-else>
      <RouterLink :to="{ name: 'signin' }">{{ $t('auth.signIn') }}</RouterLink> ·
      <RouterLink :to="{ name: 'signup' }">{{ $t('auth.signUp') }}</RouterLink>
    </p>
  </main>
</template>
