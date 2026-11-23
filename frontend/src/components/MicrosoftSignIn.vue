<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { api, rememberNext } from '@/api'

const { t } = useI18n()
const route = useRoute()
const enabled = ref(false)

onMounted(async () => {
  enabled.value = await api<{ microsoft: boolean }>('GET', '/api/auth/providers').then(
    (providers) => providers.microsoft,
    () => false,
  )
})
</script>

<template>
  <template v-if="enabled">
    <p
      class="flex items-center gap-3 text-xs text-muted before:h-px before:flex-1 before:bg-line after:h-px after:flex-1 after:bg-line"
    >
      {{ t('auth.or') }}
    </p>
    <a
      href="/api/auth/oauth2/microsoft"
      class="btn btn-outline w-full"
      @click="rememberNext(route.query)"
    >
      <svg viewBox="0 0 16 16" class="size-4" aria-hidden="true">
        <rect width="7" height="7" fill="#f25022" />
        <rect x="9" width="7" height="7" fill="#7fba00" />
        <rect y="9" width="7" height="7" fill="#00a4ef" />
        <rect x="9" y="9" width="7" height="7" fill="#ffb900" />
      </svg>
      {{ t('auth.microsoft') }}
    </a>
  </template>
</template>
