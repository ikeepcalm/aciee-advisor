<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download, Share2 } from 'lucide-vue-next'
import type { Lesson, TimetableProgram } from '@/api'
import { isoDate, ROMAN, type Parity } from '@/timetable/calendar'
import { drawWeekImage } from '@/timetable/shareImage'

const props = defineProps<{
  group: string
  program: TimetableProgram
  monday: Date
  parity: Parity | null
  lessons: Lesson[]
}>()

const { t, locale } = useI18n()
const file = ref<File | null>(null)
const url = ref('')
const failed = ref(false)

const dayMonth = computed(
  () => new Intl.DateTimeFormat(locale.value, { day: 'numeric', month: 'short' }),
)
const range = computed(() => dayMonth.value.formatRange(dateOf(1), dateOf(5)))
const canShare = computed(
  () => !!file.value && !!navigator.canShare?.({ files: [file.value] }) && !!navigator.share,
)

function dateOf(day: number) {
  const m = props.monday
  return new Date(m.getFullYear(), m.getMonth(), m.getDate() + day - 1)
}

async function share() {
  await navigator
    .share({ files: [file.value!], title: t('timetable.share.alt', alt.value) })
    .catch(() => undefined)
}

const alt = computed(() => ({ group: props.group, range: range.value }))

onMounted(async () => {
  try {
    const blob = await drawWeekImage({
      group: props.group,
      program: `${props.program.name} · ${t('timetable.year', { n: ROMAN[props.program.year] })}`,
      range: range.value,
      parity: props.parity && {
        label: t(`timetable.share.week.${props.parity}`),
        odd: props.parity === 'ODD',
      },
      days: [1, 2, 3, 4, 5].map((day) => ({
        name: t(`timetable.days.${day}`),
        date: dayMonth.value.format(dateOf(day)),
        lessons: props.lessons
          .filter((lesson) => lesson.day === day)
          .sort(
            (a, b) =>
              a.start.localeCompare(b.start) ||
              Number(a.weeks === 'EVEN') - Number(b.weeks === 'EVEN'),
          )
          .map((lesson) => ({
            start: lesson.start,
            end: lesson.end,
            title: lesson.course,
            kind: lesson.kind,
            kindLabel: t(`timetable.kinds.${lesson.kind ?? 'X'}`),
            room: lesson.room,
            teacher: lesson.teacher,
            tag:
              !props.parity && lesson.weeks !== 'ALL'
                ? t(`timetable.parity.${lesson.weeks}`)
                : null,
          })),
      })),
      free: t('timetable.share.free'),
      updated:
        props.program.updatedAt &&
        t('timetable.share.updated', {
          date: new Date(props.program.updatedAt).toLocaleDateString(locale.value, {
            dateStyle: 'medium',
          }),
        }),
    })
    file.value = new File([blob], `orar-${props.group}-${isoDate(props.monday)}.png`, {
      type: 'image/png',
    })
    url.value = URL.createObjectURL(blob)
  } catch {
    failed.value = true
  }
})

onUnmounted(() => URL.revokeObjectURL(url.value))
</script>

<template>
  <div class="flex flex-col gap-4">
    <p class="text-sm text-muted">{{ t('timetable.share.lede') }}</p>
    <p v-if="failed" role="alert" class="card border-dashed text-center text-danger">
      {{ t('timetable.share.failed') }}
    </p>
    <p v-else-if="!file" role="status" class="card border-dashed text-center text-muted">
      {{ t('timetable.share.preparing') }}
    </p>
    <template v-else>
      <img
        :src="url"
        :alt="t('timetable.share.alt', alt)"
        class="mx-auto max-h-[52dvh] w-auto max-w-full rounded-md border border-line md:max-h-[68dvh]"
      />
      <div class="flex flex-col gap-2">
        <button v-if="canShare" type="button" class="btn btn-primary" @click="share">
          <Share2 class="size-4" aria-hidden="true" />
          {{ t('timetable.share.share') }}
        </button>
        <a
          class="btn"
          :class="canShare ? 'btn-outline' : 'btn-primary'"
          :href="url"
          :download="file.name"
        >
          <Download class="size-4" aria-hidden="true" />
          {{ t('timetable.share.download') }}
        </a>
      </div>
    </template>
  </div>
</template>
