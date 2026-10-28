<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { ApiError, describeError, safeNext } from '@/api'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const email = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await auth.signIn(email.value, password.value)
    await router.push(safeNext(route.query))
  } catch (e) {
    if (e instanceof ApiError && e.problem.detail === 'unverified') {
      await router.push({ name: 'verify', query: { email: email.value, next: route.query.next } })
    } else {
      error.value = describeError(t, e)
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth">
    <h1>{{ t('auth.signIn') }}</h1>
    <form @submit.prevent="submit">
      <label for="email">{{ t('auth.email') }}</label>
      <InputText id="email" v-model.trim="email" type="email" autocomplete="email" :placeholder="t('auth.emailPlaceholder')" required fluid />
      <label for="password">{{ t('auth.password') }}</label>
      <Password input-id="password" v-model="password" :feedback="false" toggle-mask :input-props="{ autocomplete: 'current-password' }" required fluid />
      <Message v-if="error" severity="error">{{ error }}</Message>
      <Button type="submit" :label="t('auth.signInAction')" :loading="busy" fluid />
    </form>
    <p>
      {{ t('auth.noAccount') }} <RouterLink :to="{ name: 'signup' }">{{ t('auth.signUp') }}</RouterLink>
    </p>
  </main>
</template>
