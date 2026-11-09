<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Loader2 } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'
import MicrosoftSignIn from '@/components/MicrosoftSignIn.vue'
import AppNotice from '@/components/AppNotice.vue'
import PasswordField from '@/components/PasswordField.vue'
import { ApiError, describeError, safeNext } from '@/api'
import { useAuthStore } from '@/stores/auth'

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const email = ref('')
const password = ref('')
const redirectError = typeof route.query.error === 'string' ? `errors.${route.query.error}` : ''
const error = ref(redirectError && te(redirectError) ? t(redirectError) : '')
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
  <main class="page max-w-md">
    <BrandMark class="size-12" />
    <span class="eyebrow mt-4">ACIEE Advisor</span>
    <h1 class="h1">{{ t('auth.signIn') }}</h1>
    <p class="lede text-sm">{{ t('auth.signInLede') }}</p>
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
        <PasswordField id="password" v-model="password" autocomplete="current-password" />
      </div>
      <AppNotice v-if="error" kind="error" class="my-0">{{ error }}</AppNotice>
      <button type="submit" class="btn btn-primary w-full" :disabled="busy">
        <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
        {{ t('auth.signInAction') }}
      </button>
      <MicrosoftSignIn />
    </form>
    <p class="mt-5 text-center text-sm text-muted">
      {{ t('auth.noAccount') }}
      <RouterLink :to="{ name: 'signup' }">{{ t('auth.signUp') }}</RouterLink>
    </p>
  </main>
</template>
