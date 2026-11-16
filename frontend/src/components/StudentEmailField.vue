<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const DOMAIN = '@student.ugal.ro'
const email = defineModel<string>({ required: true })
const { t } = useI18n()

// Students type only their username; a pasted full address is kept as typed.
const username = computed({
  get: () =>
    email.value.toLowerCase().endsWith(DOMAIN) ? email.value.slice(0, -DOMAIN.length) : email.value,
  set: (value) => {
    const trimmed = value.trim()
    email.value = !trimmed || trimmed.includes('@') ? trimmed : trimmed + DOMAIN
  },
})
</script>

<template>
  <div class="field">
    <label for="email">{{ t('auth.email') }}</label>
    <div class="flex items-center gap-2">
      <input
        id="email"
        v-model="username"
        type="text"
        autocomplete="username"
        autocapitalize="none"
        spellcheck="false"
        :placeholder="t('auth.emailPlaceholder')"
        required
        class="input min-w-0 flex-1"
      />
      <span class="text-sm text-muted">{{ DOMAIN }}</span>
    </div>
  </div>
</template>
