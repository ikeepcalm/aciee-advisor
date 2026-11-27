<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import RadarChart from '@/components/RadarChart.vue'
import ReportButton from '@/components/ReportButton.vue'
import CommentList from '@/components/CommentList.vue'
import RateInvite from '@/components/RateInvite.vue'
import { ApiError, api, describeError, lookups, type Lookups, type Radar } from '@/api'
import { radarFor } from '@/professors'
import { showNotFound } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { localTokens } from '@/crypto/tokens'

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
  component: string
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
  examForm: string | null
  examContent: string | null
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
const tipsPreview = 6
const allTips = ref(false)
const tips = computed(() =>
  allTips.value ? (page.value?.tips ?? []) : (page.value?.tips ?? []).slice(0, tipsPreview),
)

async function load(year?: number | null) {
  error.value = ''
  try {
    page.value = await api<CoursePage>(
      'GET',
      `/api/catalog/courses/${itemId}${year ? `?year=${year}` : ''}`,
    )
  } catch (e) {
    if (!page.value && e instanceof ApiError && e.status === 404) return showNotFound()
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
    [t('rateForm.examForm'), s.examForm ?? '—'],
    [t('rateForm.examContent'), s.examContent ?? '—'],
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
  <main class="page-wide">
    <RouterLink
      to="/courses"
      class="inline-flex min-h-11 items-center gap-1 text-sm no-underline hover:underline"
    >
      <ArrowLeft class="size-4" aria-hidden="true" /> {{ t('common.back') }}
    </RouterLink>
    <AppNotice v-if="error" kind="error">
      {{ error }}
      <button class="btn btn-outline mt-2 flex" @click="load(page?.academicYearId)">
        {{ t('common.retry') }}
      </button>
    </AppNotice>
    <p v-else-if="!page" class="mt-4 text-muted" role="status">{{ t('common.loading') }}</p>
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
        <RateInvite
          class="mt-5"
          :to="rateOffering ? `/rate/${rateOffering}` : undefined"
          :label="rateOffering ? t('course.rateThis') : undefined"
        />
        <div
          v-if="auth.loaded && !auth.user"
          class="card mt-5 flex flex-wrap items-center justify-between gap-3"
        >
          <p class="max-w-prose text-sm text-muted">{{ t('course.signInPrompt') }}</p>
          <RouterLink
            :to="{ name: 'signin', query: { next: `/courses/${itemId}` } }"
            class="btn btn-outline w-full sm:w-auto"
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
          class="mt-3 grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-line bg-line md:grid-cols-3 lg:grid-cols-6"
        >
          <div v-for="[label, value] in stats" :key="label" class="bg-panel p-3">
            <dt class="text-xs text-muted">{{ label }}</dt>
            <dd class="mono m-0 mt-1 text-base font-medium break-words md:text-lg">{{ value }}</dd>
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
              class="input text-sm"
              @change="load(Number(($event.target as HTMLSelectElement).value))"
            >
              <option v-for="y in page.years" :key="y.id" :value="y.id">{{ y.label }}</option>
            </select>
          </label>
        </div>
        <p v-if="!page.teachers.length" class="mt-3 text-sm text-muted">
          {{ t('course.noTeachers') }}
        </p>
        <div class="card-grid mt-3">
          <article
            v-for="teacher in page.teachers"
            :key="`${teacher.professorId}-${teacher.componentLabel}`"
            class="card"
          >
            <div class="flex flex-wrap items-center gap-2">
              <span class="tag bg-board text-muted">{{ teacher.componentLabel }}</span>
              <span v-if="teacher.subgroups" class="text-xs text-muted">{{
                t('course.subgroups', { list: teacher.subgroups })
              }}</span>
              <span
                v-if="teacher.radar && teacher.radar.n >= page.threshold"
                class="ml-auto text-xs text-muted"
                >{{ t('course.ratingsCount', { n: teacher.radar.n }) }}</span
              >
            </div>
            <RouterLink
              :to="`/professors/${teacher.slug}`"
              class="flex min-h-11 items-center text-base font-semibold"
              >{{ teacher.title }} {{ teacher.fullName }}</RouterLink
            >
            <p
              v-if="!teacher.radar || teacher.radar.n < page.threshold"
              class="text-sm text-muted italic"
            >
              {{ missing(teacher.radar?.n ?? 0) }}
            </p>
            <RadarChart
              v-else-if="labels"
              v-bind="
                radarFor(labels.axes, labels.pillars, teacher.radar.scores, teacher.component)
              "
            />
            <CommentList :comments="teacher.comments ?? []" />
          </article>
        </div>
      </section>

      <section class="mt-8">
        <h2 class="h2">{{ t('course.tips', { n: page.tips.length }) }}</h2>
        <p v-if="!page.tips.length" class="mt-3 text-sm text-muted">{{ t('course.noTips') }}</p>
        <div class="mt-3 grid items-start gap-3 md:grid-cols-2 xl:grid-cols-3">
          <blockquote
            v-for="tip in tips"
            :key="tip.id"
            class="card border-l-[3px] border-l-copper text-sm"
          >
            {{ tip.body }}
            <footer class="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted">
              {{ t('course.week', { date: d(tip.week, 'short') }) }}
              <ReportButton :post-id="tip.id" />
            </footer>
          </blockquote>
        </div>
        <button
          v-if="!allTips && page.tips.length > tipsPreview"
          class="btn btn-outline mt-4 w-full sm:w-auto"
          @click="allTips = true"
        >
          {{ t('course.showAllTips', { n: page.tips.length }) }}
        </button>
      </section>
      <p class="mt-8 text-xs text-muted">{{ t('course.updated') }}</p>
    </template>
  </main>
</template>
