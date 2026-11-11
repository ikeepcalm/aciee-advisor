<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import RadarChart from '@/components/RadarChart.vue'
import ReportButton from '@/components/ReportButton.vue'
import { api, describeError, lookups, type Lookups } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { localTokens } from '@/crypto/tokens'

interface Radar {
  n: number
  scores: (number | null)[]
}

interface Text {
  id: string
  body: string
  week: string
}

interface Teacher {
  professorId: number
  slug: string
  fullName: string
  title: string
  componentLabel: string
  subgroups: string | null
  radar: Radar | null
  comments: Text[] | null
}

interface Survival {
  n: number
  examDifficulty: number | null
  labsHelp: number | null
  attendanceStrictness: number | null
  weeklyHours: number | null
  attendanceUseful: string | null
  examFormat: string | null
}

interface CoursePage {
  subjectName: string
  specialization: string
  studyYear: number
  semesterNo: number
  threshold: number
  academicYearId: number | null
  years: { id: number; label: string }[]
  teachers: Teacher[]
  survival: Survival | null
  tips: Text[]
  offeringIds: number[]
}

const { t, d } = useI18n()
const itemId = useRoute().params.itemId as string
const auth = useAuthStore()
const page = ref<CoursePage | null>(null)
const labels = ref<Lookups | null>(null)
const rateOffering = ref<number | null>(null)
const error = ref('')

async function load(year?: number | null) {
  error.value = ''
  try {
    page.value = await api<CoursePage>(
      'GET',
      `/api/catalog/courses/${itemId}${year ? `?year=${year}` : ''}`,
    )
  } catch (e) {
    error.value = describeError(t, e)
  }
}

function missing(n: number) {
  return n === 0
    ? t('course.noRatings')
    : t('course.locked', { missing: page.value!.threshold - n })
}

function score(value: number | null) {
  return value === null ? '—' : `${value.toFixed(1)} / 10`
}

const stats = computed(() => {
  const s = page.value!.survival!
  return [
    [t('rateForm.examDifficulty'), score(s.examDifficulty)],
    [t('rateForm.labsHelp'), score(s.labsHelp)],
    [t('rateForm.attendanceStrictness'), score(s.attendanceStrictness)],
    [t('rateForm.weeklyHours'), s.weeklyHours?.toFixed(1) ?? '—'],
    [t('rateForm.attendanceUseful'), s.attendanceUseful ?? '—'],
    [t('rateForm.examFormat'), s.examFormat ?? '—'],
  ]
})

onMounted(async () => {
  lookups().then(
    (l) => (labels.value = l),
    (e) => (error.value = describeError(t, e)),
  )
  await Promise.all([load(), auth.load()])
  if (auth.user && page.value) {
    const tokens = await localTokens(auth.user.id).catch(() => [])
    rateOffering.value =
      tokens.find((tk) => !tk.submitted && page.value!.offeringIds.includes(tk.offeringId))
        ?.offeringId ?? null
  }
})
</script>

<template>
  <main class="page">
    <RouterLink
      to="/courses"
      class="inline-flex min-h-11 items-center gap-1 text-sm no-underline hover:underline"
    >
      <ArrowLeft class="size-4" aria-hidden="true" /> {{ t('common.back') }}
    </RouterLink>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <template v-if="page">
      <header class="mt-2">
        <span class="eyebrow">
          {{
            t('course.subtitle', {
              specialization: page.specialization,
              year: page.studyYear,
              semester: page.semesterNo,
            })
          }}
        </span>
        <h1 class="h1">{{ page.subjectName }}</h1>
        <RouterLink v-if="rateOffering" :to="`/rate/${rateOffering}`" class="btn btn-primary mt-5">
          {{ t('course.rateThis') }}
        </RouterLink>
        <div
          v-if="auth.loaded && !auth.user"
          class="card mt-5 flex flex-wrap items-center justify-between gap-3"
        >
          <p class="text-sm text-muted">{{ t('course.signInPrompt') }}</p>
          <RouterLink
            :to="{ name: 'signin', query: { next: `/courses/${itemId}` } }"
            class="btn btn-outline"
          >
            {{ t('auth.signInAction') }}
          </RouterLink>
        </div>
      </header>

      <section class="mt-8">
        <h2 class="h2">
          {{
            page.survival && page.survival.n >= page.threshold
              ? t('course.survivalCount', { n: page.survival.n })
              : t('course.survival')
          }}
        </h2>
        <p
          v-if="!page.survival || page.survival.n < page.threshold"
          class="mt-3 text-sm text-muted italic"
        >
          {{ missing(page.survival?.n ?? 0) }}
        </p>
        <dl
          v-else
          class="mt-3 grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-line bg-line md:grid-cols-3"
        >
          <div v-for="[label, value] in stats" :key="label" class="bg-panel p-3">
            <dt class="text-xs text-muted">{{ label }}</dt>
            <dd class="mono mt-1 text-base font-medium md:text-lg">{{ value }}</dd>
          </div>
        </dl>
      </section>

      <section class="mt-8">
        <div class="flex flex-wrap items-end justify-between gap-3">
          <h2 class="h2">{{ t('course.teachers') }}</h2>
          <label v-if="page.years.length > 1" class="field text-xs">
            {{ t('course.academicYear') }}
            <select
              :value="page.academicYearId"
              class="input min-h-9 text-sm"
              @change="load(Number(($event.target as HTMLSelectElement).value))"
            >
              <option v-for="y in page.years" :key="y.id" :value="y.id">{{ y.label }}</option>
            </select>
          </label>
        </div>
        <p v-if="!page.teachers.length" class="mt-3 text-sm text-muted">
          {{ t('course.noTeachers') }}
        </p>
        <article
          v-for="teacher in page.teachers"
          :key="`${teacher.professorId}-${teacher.componentLabel}`"
          class="card mt-3"
        >
          <div class="flex flex-wrap items-center gap-2">
            <span class="tag bg-board text-muted">{{ teacher.componentLabel }}</span>
            <span v-if="teacher.subgroups" class="text-xs text-muted">{{
              t('course.subgroups', { list: teacher.subgroups })
            }}</span>
          </div>
          <strong class="mt-2 block text-base">
            <RouterLink :to="`/professors/${teacher.slug}`"
              >{{ teacher.title }} {{ teacher.fullName }}</RouterLink
            >
          </strong>
          <p
            v-if="!teacher.radar || teacher.radar.n < page.threshold"
            class="mt-2 text-sm text-muted italic"
          >
            {{ missing(teacher.radar?.n ?? 0) }}
          </p>
          <template v-else-if="labels">
            <p class="mono mt-1 text-xs text-muted">n = {{ teacher.radar.n }}</p>
            <RadarChart
              :scores="teacher.radar.scores"
              :axes="labels.axes"
              :pillars="labels.pillars"
            />
          </template>
          <details v-if="teacher.comments?.length" class="mt-3">
            <summary class="cursor-pointer text-sm font-medium text-mask">
              {{ t('course.comments', { n: teacher.comments.length }) }}
            </summary>
            <blockquote v-for="c in teacher.comments" :key="c.id" class="quote mt-3 text-sm">
              {{ c.body }}
              <footer class="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
                {{ t('course.week', { date: d(c.week, 'short') }) }}
                <ReportButton :post-id="c.id" />
              </footer>
            </blockquote>
          </details>
        </article>
      </section>

      <section class="mt-8">
        <h2 class="h2">{{ t('course.tips', { n: page.tips.length }) }}</h2>
        <p v-if="!page.tips.length" class="mt-3 text-sm text-muted">{{ t('course.noTips') }}</p>
        <blockquote v-for="tip in page.tips" :key="tip.id" class="card quote mt-3 text-sm">
          {{ tip.body }}
          <footer class="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted">
            {{ t('course.week', { date: d(tip.week, 'short') }) }}
            <ReportButton :post-id="tip.id" />
          </footer>
        </blockquote>
      </section>
      <p class="mt-8 text-xs text-muted">{{ t('course.updated') }}</p>
    </template>
  </main>
</template>
