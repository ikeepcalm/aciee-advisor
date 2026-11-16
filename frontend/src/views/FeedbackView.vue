<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Loader2 } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import { api, describeError, safeNext } from '@/api'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const route = useRoute()
const auth = useAuthStore()
const from = computed(() => safeNext({ next: route.query.from ?? null }))
const message = ref('')
const replyTo = ref('')
const sent = ref(false)
const busy = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await api('POST', '/api/feedback', {
      message: message.value,
      page: from.value,
      replyTo: auth.user ? null : replyTo.value.trim() || null,
    })
    sent.value = true
  } catch (e) {
    error.value = describeError(t, e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="page-narrow">
    <span class="eyebrow">{{ t('feedback.eyebrow') }}</span>
    <h1 class="h1">{{ t('feedback.title') }}</h1>
    <p class="lede text-sm">{{ t('feedback.lede') }}</p>

    <div v-if="sent" class="card mt-6 grid gap-4">
      <AppNotice kind="success" class="my-0">{{ t('feedback.sent') }}</AppNotice>
      <RouterLink :to="from" class="btn btn-primary w-full">{{ t('feedback.back') }}</RouterLink>
    </div>
    <form v-else class="card mt-6 grid gap-4" @submit.prevent="submit">
      <div class="field">
        <label for="feedback-message">{{ t('feedback.message') }}</label>
        <textarea
          id="feedback-message"
          v-model="message"
          class="input min-h-36"
          maxlength="2000"
          required
          :placeholder="t('feedback.messagePlaceholder')"
        ></textarea>
        <span class="mono text-xs font-normal break-all text-muted">
          {{ t('feedback.page', { page: from }) }}
        </span>
      </div>
      <p v-if="auth.user" class="text-sm text-muted">
        {{ t('feedback.replyAccount', { email: auth.user.email }) }}
      </p>
      <div v-else class="field">
        <label for="feedback-reply">{{ t('feedback.replyTo') }}</label>
        <input
          id="feedback-reply"
          v-model="replyTo"
          type="email"
          class="input"
          autocomplete="email"
          maxlength="254"
        />
        <span class="text-xs font-normal text-muted">{{ t('feedback.replyHint') }}</span>
      </div>
      <AppNotice v-if="error" kind="error" class="my-0">{{ error }}</AppNotice>
      <button type="submit" class="btn btn-primary w-full" :disabled="busy || !message.trim()">
        <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
        {{ t('feedback.send') }}
      </button>
    </form>
  </main>
</template>
