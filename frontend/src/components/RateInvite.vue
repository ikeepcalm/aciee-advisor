<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowRight, Star } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

defineProps<{ to?: string; label?: string }>()
const { t } = useI18n()
const auth = useAuthStore()
</script>

<template>
  <aside
    v-if="auth.user"
    class="rate-invite card flex flex-wrap items-center justify-between gap-x-4 gap-y-3 py-3"
  >
    <p class="flex min-w-0 items-center gap-2 text-sm font-medium">
      <Star class="size-5 shrink-0 text-copper" aria-hidden="true" />
      {{ t('nav.rateInvite') }}
    </p>
    <RouterLink :to="to ?? { name: 'rate' }" class="btn btn-primary w-full sm:w-auto">
      {{ label ?? t('nav.rate') }}
      <ArrowRight class="size-4" aria-hidden="true" />
    </RouterLink>
  </aside>
</template>

<style scoped>
.rate-invite {
  border-color: var(--ink);
  background: color-mix(in srgb, var(--mark) 16%, var(--panel));
}
</style>
