<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Select from 'primevue/select'
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
  signedIn: boolean
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

onMounted(async () => {
  await load()
  labels.value = await lookups()
  if (auth.user && page.value) {
    const tokens = await localTokens(auth.user.id).catch(() => [])
    rateOffering.value =
      tokens.find((tk) => !tk.submitted && page.value!.offeringIds.includes(tk.offeringId))
        ?.offeringId ?? null
  }
})
</script>

<template>
  <main>
    <RouterLink to="/">← {{ t('common.back') }}</RouterLink>
    <Message v-if="error" severity="error">{{ error }}</Message>
    <template v-if="page">
      <h1>{{ page.subjectName }}</h1>
      <p class="muted">
        {{
          t('course.subtitle', {
            specialization: page.specialization,
            year: page.studyYear,
            semester: page.semesterNo,
          })
        }}
      </p>

      <Button
        v-if="rateOffering"
        as="router-link"
        :to="`/rate/${rateOffering}`"
        :label="t('course.rateThis')"
        class="rate"
      />

      <section>
        <h2>
          {{
            page.survival && page.survival.n >= page.threshold
              ? t('course.survivalCount', { n: page.survival.n })
              : t('course.survival')
          }}
        </h2>
        <div v-if="!page.signedIn" class="card">
          <p>{{ t('course.signInPrompt') }}</p>
          <RouterLink :to="{ name: 'signin', query: { next: `/courses/${itemId}` } }">{{
            t('auth.signIn')
          }}</RouterLink>
        </div>
        <p v-else-if="!page.survival || page.survival.n < page.threshold" class="locked">
          {{ missing(page.survival?.n ?? 0) }}
        </p>
        <dl v-else class="survival">
          <dt>{{ t('rateForm.examDifficulty') }}</dt>
          <dd>{{ score(page.survival.examDifficulty) }}</dd>
          <dt>{{ t('rateForm.labsHelp') }}</dt>
          <dd>{{ score(page.survival.labsHelp) }}</dd>
          <dt>{{ t('rateForm.attendanceUseful') }}</dt>
          <dd>{{ page.survival.attendanceUseful ?? '—' }}</dd>
          <dt>{{ t('rateForm.weeklyHours') }}</dt>
          <dd>{{ page.survival.weeklyHours?.toFixed(1) ?? '—' }}</dd>
          <dt>{{ t('rateForm.examFormat') }}</dt>
          <dd>{{ page.survival.examFormat ?? '—' }}</dd>
          <dt>{{ t('rateForm.attendanceStrictness') }}</dt>
          <dd>{{ score(page.survival.attendanceStrictness) }}</dd>
        </dl>
      </section>

      <section>
        <h2>{{ t('course.teachers') }}</h2>
        <label v-if="page.years.length > 1" class="year">
          {{ t('course.academicYear') }}
          <Select
            :model-value="page.academicYearId"
            :options="page.years"
            option-label="label"
            option-value="id"
            @update:model-value="load"
          />
        </label>
        <p v-if="!page.teachers.length" class="muted">{{ t('course.noTeachers') }}</p>
        <article
          v-for="teacher in page.teachers"
          :key="`${teacher.professorId}-${teacher.componentLabel}`"
          class="card"
        >
          <strong>
            {{ teacher.componentLabel }}:
            <RouterLink v-if="page.signedIn" :to="`/professors/${teacher.slug}`"
              >{{ teacher.title }} {{ teacher.fullName }}</RouterLink
            >
            <template v-else>{{ teacher.title }} {{ teacher.fullName }}</template>
          </strong>
          <span v-if="teacher.subgroups" class="muted">
            · {{ t('course.subgroups', { list: teacher.subgroups }) }}</span
          >
          <template v-if="page.signedIn">
            <p v-if="!teacher.radar || teacher.radar.n < page.threshold" class="locked">
              {{ missing(teacher.radar?.n ?? 0) }}
            </p>
            <template v-else-if="labels">
              <p class="muted">n = {{ teacher.radar.n }}</p>
              <RadarChart
                :scores="teacher.radar.scores"
                :axes="labels.axes"
                :pillars="labels.pillars"
              />
            </template>
            <details v-if="teacher.comments?.length">
              <summary>{{ t('course.comments', { n: teacher.comments.length }) }}</summary>
              <blockquote v-for="c in teacher.comments" :key="c.id">
                {{ c.body }}
                <footer class="muted">
                  {{ t('course.week', { date: d(c.week, 'short') }) }}
                  <ReportButton :post-id="c.id" />
                </footer>
              </blockquote>
            </details>
          </template>
        </article>
      </section>

      <section v-if="page.signedIn">
        <h2>{{ t('course.tips', { n: page.tips.length }) }}</h2>
        <p v-if="!page.tips.length" class="muted">{{ t('course.noTips') }}</p>
        <blockquote v-for="tip in page.tips" :key="tip.id" class="card">
          {{ tip.body }}
          <footer class="muted">
            {{ t('course.week', { date: d(tip.week, 'short') }) }}
            <ReportButton :post-id="tip.id" />
          </footer>
        </blockquote>
      </section>
      <p class="muted">{{ t('course.updated') }}</p>
    </template>
  </main>
</template>

<style scoped>
h2 {
  font-size: 1.05rem;
  margin: 1.5rem 0 0.5rem;
}

.survival {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.35rem 1rem;
}

.survival dd {
  margin: 0;
  font-weight: 600;
}

.year {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

blockquote {
  margin: 0.5rem 0;
  white-space: pre-wrap;
}

.rate {
  margin: 0.5rem 0;
}
</style>
