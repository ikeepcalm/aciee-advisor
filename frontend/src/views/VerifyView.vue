<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Loader2 } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'
import AppNotice from '@/components/AppNotice.vue'
import PasswordField from '@/components/PasswordField.vue'
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

function typeCode(event: Event) {
  code.value = (event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 6)
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
  <main class="page max-w-md">
    <BrandMark class="size-12" />
    <span class="eyebrow mt-4">ACIEE Advisor</span>
    <h1 class="h1">{{ t('auth.verifyTitle') }}</h1>
    <p class="lede text-sm">{{ t('auth.verifySent', { email }) }}</p>
    <form class="card mt-6 grid gap-4" @submit.prevent="submit">
      <div class="field">
        <label for="code">{{ t('auth.verifyCode') }}</label>
        <input
          id="code"
          :value="code"
          class="input mono text-center text-2xl tracking-[0.5em]"
          inputmode="numeric"
          autocomplete="one-time-code"
          maxlength="6"
          required
          @input="typeCode"
        />
      </div>
      <div v-if="askPassword" class="field">
        <label for="password">{{ t('auth.password') }}</label>
        <PasswordField id="password" v-model="password" autocomplete="current-password" />
      </div>
      <AppNotice v-if="error" kind="error" class="my-0">{{ error }}</AppNotice>
      <AppNotice v-if="notice" kind="success" class="my-0">{{ notice }}</AppNotice>
      <button
        type="submit"
        class="btn btn-primary w-full"
        :disabled="busy || code.length !== 6 || !password"
      >
        <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
        {{ t('auth.verifyAction') }}
      </button>
    </form>
    <button class="btn btn-ghost mx-auto mt-3 flex" :disabled="busy" @click="resend">
      {{ t('auth.resend') }}
    </button>
  </main>
</template>
