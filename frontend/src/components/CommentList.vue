<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ChevronRight } from 'lucide-vue-next'
import ReportButton from '@/components/ReportButton.vue'
import type { PublicComment } from '@/api'
import { academicYear } from '@/professors'

defineProps<{ comments: PublicComment[]; open?: boolean }>()
const { t, d } = useI18n()
</script>

<template>
  <details v-if="comments.length" class="group mt-1" :open="open">
    <summary
      class="flex min-h-11 cursor-pointer list-none items-center gap-1 text-sm font-medium text-mask [&::-webkit-details-marker]:hidden"
    >
      <ChevronRight class="size-4 transition-transform group-open:rotate-90" aria-hidden="true" />
      {{ t('course.comments', { n: comments.length }) }}
    </summary>
    <blockquote v-for="c in comments" :key="c.id" class="quote mb-3 text-sm">
      {{ c.body }}
      <footer class="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
        <span class="tag bg-board text-ink">{{ academicYear(c.week) }}</span>
        {{ t('course.week', { date: d(c.week, 'short') }) }}
        <ReportButton :post-id="c.id" />
      </footer>
    </blockquote>
  </details>
</template>
