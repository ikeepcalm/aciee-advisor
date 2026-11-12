<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight } from 'lucide-vue-next'
import type { TimetableChange } from '@/api'
import { changedFields, type ChangedField, type ChangeLine } from '@/timetable/changes'

defineProps<{ lines: ChangeLine[]; group: string }>()

const { t, locale } = useI18n()
const dateFormat = computed(
  () => new Intl.DateTimeFormat(locale.value, { day: 'numeric', month: 'short' }),
)
const tone = { added: 'text-mask', removed: 'text-copper', moved: 'text-ink' }

function weeksLabel(change: TimetableChange) {
  return change.weeks === 'ALL'
    ? t('timetable.changes.everyWeek')
    : t(`timetable.parity.${change.weeks}`)
}

function value(change: TimetableChange, field: ChangedField) {
  const values: Record<ChangedField, string | null> = {
    room: change.room,
    time: `${change.startTime}–${change.endTime}`,
    teacher: change.teacher,
    weeks: weeksLabel(change),
    kind: t(`timetable.kinds.${change.kind ?? 'X'}`),
  }
  return values[field] ?? '—'
}

function details(change: TimetableChange) {
  return [
    `${t(`timetable.days.${change.day}`)}, ${change.startTime}–${change.endTime}`,
    change.weeks === 'ALL' ? null : weeksLabel(change),
    change.room,
    change.teacher,
  ]
    .filter(Boolean)
    .join(' · ')
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <p class="text-sm text-muted">{{ t('timetable.changes.lede', { group }) }}</p>
    <ul class="m-0 flex list-none flex-col gap-2 p-0">
      <li
        v-for="(line, index) in lines"
        :key="index"
        class="card flex flex-col gap-1 py-3"
        :class="`k-${line.slot.kind ?? 'X'}`"
      >
        <p class="flex items-baseline justify-between gap-3">
          <span class="tag -ml-2 border border-current" :class="tone[line.type]">
            {{ t(`timetable.changes.${line.type}`) }}
          </span>
          <time class="mono text-xs text-muted" :datetime="line.slot.detectedAt">
            {{ dateFormat.format(new Date(line.slot.detectedAt)) }}
          </time>
        </p>
        <p class="font-semibold">
          <span class="kind-tag mr-1">{{ t(`timetable.kinds.${line.slot.kind ?? 'X'}`) }}</span>
          <span :class="{ 'line-through decoration-copper': line.type === 'removed' }">
            {{ line.slot.subject }}
          </span>
        </p>
        <p class="text-sm text-muted">{{ details(line.slot) }}</p>
        <p
          v-for="field in changedFields(line)"
          :key="field"
          class="flex flex-wrap items-center gap-x-2 text-sm"
        >
          <span class="text-muted">{{ t(`timetable.changes.fields.${field}`) }}</span>
          <span class="line-through decoration-copper">{{ value(line.previous!, field) }}</span>
          <ArrowRight class="size-4 text-copper" aria-hidden="true" />
          <strong>{{ value(line.slot, field) }}</strong>
        </p>
      </li>
    </ul>
  </div>
</template>
