<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Lesson } from '@/api'

defineProps<{
  lesson: Lesson
  progress: number | null
  soon?: number | null
  showParity: boolean
}>()

const { t } = useI18n()
</script>

<template>
  <article class="lesson" :class="[`k-${lesson.kind ?? 'X'}`, { live: progress !== null }]">
    <span class="tags">
      <span class="kind">{{ t(`timetable.kinds.${lesson.kind ?? 'X'}`) }}</span>
      <span v-if="showParity && lesson.weeks !== 'ALL'" class="dim">
        {{ t(`timetable.parity.${lesson.weeks}`) }}
      </span>
      <span v-if="progress !== null" class="now">{{ t('timetable.now') }}</span>
      <span v-else-if="soon != null" class="soon">{{ t('timetable.startsIn', { n: soon }) }}</span>
    </span>
    <strong>{{ lesson.title }}</strong>
    <span v-for="(line, i) in lesson.lines" :key="i" class="dim">{{ line }}</span>
    <span v-if="progress !== null" class="bar" aria-hidden="true">
      <span :style="{ width: `${progress}%` }"></span>
    </span>
  </article>
</template>

<style scoped>
.lesson {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--line);
  border-left: 4px solid var(--kind);
  border-radius: 0.55rem;
  background: var(--panel);
  font-size: 0.8125rem;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  font-family: var(--font-mono);
  font-size: 0.5625rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.kind {
  color: var(--kind);
  font-weight: 500;
}

.now {
  color: var(--copper);
  font-weight: 500;
}

.soon {
  color: var(--mask);
  font-weight: 500;
}

.dim {
  color: var(--muted);
}

.bar {
  display: block;
  height: 3px;
  margin-top: 0.4rem;
  border-radius: 2px;
  background: var(--line);
  overflow: hidden;
}

.bar span {
  display: block;
  height: 100%;
  background: var(--kind);
}
</style>
