<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { describeError } from '@/api'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const email = ref('')
const password = ref('')
const confirm = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  if (!/^[^@\s]+@student\.ugal\.ro$/i.test(email.value)) {
    error.value = t('errors.domain')
    return
  }
  if (password.value.length < 10 || password.value.length > 128) {
    error.value = t('errors.passwordLength')
    return
  }
  if (password.value !== confirm.value) {
    error.value = t('errors.passwordsDiffer')
    return
  }
  error.value = ''
  busy.value = true
  try {
    await auth.signUp(email.value, password.value)
  } catch (e) {
    error.value = describeError(t, e)
    return
  } finally {
    busy.value = false
  }
  await router.push({ name: 'verify', query: { email: email.value } })
}
</script>

<template>
  <main class="auth">
    <h1>{{ t('auth.signUp') }}</h1>
    <form @submit.prevent="submit">
      <label for="email">{{ t('auth.email') }}</label>
      <InputText id="email" v-model.trim="email" type="email" autocomplete="email" :placeholder="t('auth.emailPlaceholder')" required fluid />
      <label for="password">{{ t('auth.password') }}</label>
      <Password input-id="password" v-model="password" :feedback="false" toggle-mask :input-props="{ autocomplete: 'new-password' }" required fluid />
      <label for="confirm">{{ t('auth.confirmPassword') }}</label>
      <Password input-id="confirm" v-model="confirm" :feedback="false" :input-props="{ autocomplete: 'new-password' }" required fluid />
      <Message v-if="error" severity="error">{{ error }}</Message>
      <Button type="submit" :label="t('auth.signUp')" :loading="busy" fluid />
    </form>
    <p>
      {{ t('auth.haveAccount') }} <RouterLink :to="{ name: 'signin' }">{{ t('auth.signInAction') }}</RouterLink>
    </p>
  </main>
</template>
