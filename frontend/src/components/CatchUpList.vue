<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight, ChevronDown } from 'lucide-vue-next'
import type { Lesson } from '@/api'
import type { CatchUpSlot } from '@/timetable/catchUp'
import { ROMAN } from '@/timetable/calendar'

const props = defineProps<{
  slots: (CatchUpSlot & { dates: Date[] })[]
  missed: Lesson
  course: string
  courseId: number | null
  ownProgramCode: string
}>()

const { t, locale } = useI18n()
const dateFormat = computed(
  () => new Intl.DateTimeFormat(locale.value, { weekday: 'short', day: 'numeric', month: 'short' }),
)
const sameTeacherSlots = computed(() => props.slots.filter((slot) => slot.sameTeacher))
const otherSlots = computed(() => props.slots.filter((slot) => !slot.sameTeacher))
const showAll = ref(!sameTeacherSlots.value.length)
const sections = computed(() => [
  { heading: t('timetable.catchUp.sameTeacher'), slots: sameTeacherSlots.value },
  { heading: t('timetable.catchUp.otherTeachers'), slots: showAll.value ? otherSlots.value : [] },
])
</script>

<template>
  <div class="flex flex-col gap-4">
    <div>
      <p class="font-semibold">
        {{ t(`timetable.kinds.${missed.kind ?? 'X'}`) }} · {{ course }}
        <span v-if="missed.teacher" class="font-normal text-muted">· {{ missed.teacher }}</span>
      </p>
      <p class="text-sm text-muted">{{ t('timetable.catchUp.lede') }}</p>
    </div>

    <p v-if="!slots.length" class="card border-dashed text-center text-muted">
      {{ t('timetable.catchUp.empty') }}
    </p>
    <template v-else>
      <p v-if="!sameTeacherSlots.length" class="text-sm text-muted">
        {{ t('timetable.catchUp.noSameTeacher') }}
      </p>
      <template v-for="(section, index) in sections" :key="index">
        <button
          v-if="index === 1 && sameTeacherSlots.length && otherSlots.length"
          type="button"
          class="btn btn-outline"
          :aria-expanded="showAll"
          @click="showAll = !showAll"
        >
          {{ t('timetable.catchUp.showAll', { n: otherSlots.length }) }}
          <ChevronDown
            class="chevron size-4"
            :class="{ 'rotate-180': showAll }"
            aria-hidden="true"
          />
        </button>
        <section v-if="section.slots.length">
          <h3 class="eyebrow">{{ section.heading }}</h3>
          <ul class="grid gap-2">
            <li
              v-for="(slot, slotIndex) in section.slots"
              :key="slotIndex"
              class="card grid gap-0.5 p-3 text-sm"
              :class="{ 'border-dashed text-muted': slot.clash }"
            >
              <p class="flex flex-wrap items-baseline justify-between gap-x-2">
                <strong class="display text-lg">
                  {{ t(`timetable.days.${slot.lesson.day}`) }} · {{ slot.lesson.start }}–{{
                    slot.lesson.end
                  }}
                </strong>
                <span v-if="slot.lesson.weeks !== 'ALL'" class="mono text-xs text-copper uppercase">
                  {{ t(`timetable.parity.${slot.lesson.weeks}`) }}
                </span>
              </p>
              <p>
                <span class="font-semibold">{{ slot.lesson.groups.join(', ') }}</span>
                <span v-if="slot.program.code !== ownProgramCode" class="text-muted">
                  · {{ slot.program.name }} ·
                  {{ t('timetable.year', { n: ROMAN[slot.program.year] }) }}
                </span>
              </p>
              <p v-if="slot.lesson.room || slot.lesson.teacher" class="text-muted">
                {{ [slot.lesson.room, slot.lesson.teacher].filter(Boolean).join(' · ') }}
              </p>
              <p v-if="slot.dates.length" class="mono text-xs">
                {{ slot.dates.map((date) => dateFormat.format(date)).join(' · ') }}
              </p>
              <p v-if="slot.clash" class="text-xs font-semibold text-copper">
                {{ t('timetable.catchUp.clash') }}
              </p>
            </li>
          </ul>
        </section>
      </template>
    </template>

    <p class="text-sm text-muted">
      {{ t('guide.catchUp') }}
      <RouterLink to="/guide#attendance" class="inline-flex min-h-11 items-center">
        {{ t('guide.catchUpLink') }}
      </RouterLink>
    </p>

    <RouterLink
      :to="courseId ? `/courses/${courseId}` : { name: 'catalog' }"
      class="btn btn-ghost justify-start px-0 text-left"
    >
      {{ t('timetable.catchUp.reviews', { course }) }}
      <ArrowRight class="size-4 flex-none" aria-hidden="true" />
    </RouterLink>
  </div>
</template>

<style scoped>
@media (prefers-reduced-motion: no-preference) {
  .chevron {
    transition: rotate 150ms ease-out;
  }
}
</style>
