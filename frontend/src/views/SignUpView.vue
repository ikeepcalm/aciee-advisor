<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Loader2 } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'
import MicrosoftSignIn from '@/components/MicrosoftSignIn.vue'
import AppNotice from '@/components/AppNotice.vue'
import PasswordField from '@/components/PasswordField.vue'
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
  <main class="page max-w-md">
    <BrandMark class="size-12" />
    <span class="eyebrow mt-4">ACIEE Advisor</span>
    <h1 class="h1">{{ t('auth.signUp') }}</h1>
    <p class="lede text-sm">{{ t('errors.domain') }}</p>
    <form class="card mt-6 grid gap-4" @submit.prevent="submit">
      <div class="field">
        <label for="email">{{ t('auth.email') }}</label>
        <input
          id="email"
          v-model.trim="email"
          type="email"
          autocomplete="email"
          :placeholder="t('auth.emailPlaceholder')"
          required
          class="input"
        />
      </div>
      <div class="field">
        <label for="password">{{ t('auth.password') }}</label>
        <PasswordField id="password" v-model="password" autocomplete="new-password" />
        <span class="text-xs font-normal text-muted">{{ t('errors.passwordLength') }}</span>
      </div>
      <div class="field">
        <label for="confirm">{{ t('auth.confirmPassword') }}</label>
        <PasswordField id="confirm" v-model="confirm" autocomplete="new-password" />
      </div>
      <AppNotice v-if="error" kind="error" class="my-0">{{ error }}</AppNotice>
      <button type="submit" class="btn btn-primary w-full" :disabled="busy">
        <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
        {{ t('auth.signUp') }}
      </button>
      <MicrosoftSignIn />
    </form>
    <p class="mt-5 text-center text-sm text-muted">
      {{ t('auth.haveAccount') }}
      <RouterLink :to="{ name: 'signin' }">{{ t('auth.signInAction') }}</RouterLink>
    </p>
  </main>
</template>
