<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import InputOtp from 'primevue/inputotp'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { api, describeError, safeNext } from '@/api'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const email = typeof route.query.email === 'string' ? route.query.email : ''
const askPassword = !auth.pendingPassword
const password = ref(auth.pendingPassword)
const code = ref('')
const error = ref('')
const notice = ref('')
const busy = ref(false)

if (!email) {
  router.replace({ name: 'signup' })
}

async function submit() {
  error.value = ''
  notice.value = ''
  busy.value = true
  try {
    await auth.verify(email, code.value, password.value)
    await router.push(safeNext(route.query))
  } catch (e) {
    error.value = describeError(t, e)
  } finally {
    busy.value = false
  }
}

async function resend() {
  error.value = ''
  notice.value = ''
  busy.value = true
  try {
    await api('POST', '/api/auth/verify/resend', { email })
    code.value = ''
    notice.value = t('auth.resent')
  } catch (e) {
    error.value = describeError(t, e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth">
    <h1>{{ t('auth.verifyTitle') }}</h1>
    <p>{{ t('auth.verifySent', { email }) }}</p>
    <form @submit.prevent="submit">
      <InputOtp v-model="code" :length="6" integer-only />
      <template v-if="askPassword">
        <label for="password">{{ t('auth.password') }}</label>
        <Password input-id="password" v-model="password" :feedback="false" toggle-mask :input-props="{ autocomplete: 'current-password' }" required fluid />
      </template>
      <Message v-if="error" severity="error">{{ error }}</Message>
      <Message v-if="notice" severity="success">{{ notice }}</Message>
      <Button type="submit" :label="t('auth.verifyAction')" :loading="busy" :disabled="code.length !== 6 || !password" fluid />
    </form>
    <Button :label="t('auth.resend')" link :disabled="busy" @click="resend" />
  </main>
</template>
