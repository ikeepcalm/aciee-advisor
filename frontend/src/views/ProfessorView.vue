<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import RadarChart from '@/components/RadarChart.vue'
import ReportButton from '@/components/ReportButton.vue'
import { api, describeError, lookups, type Lookups } from '@/api'

interface Subject {
  subjectId: number
  subjectName: string
  itemId: number
  components: string
  radar: { n: number; scores: (number | null)[] } | null
  comments: { id: string; body: string; week: string }[]
}

interface ProfessorPage {
  fullName: string
  title: string
  department: string
  archived: boolean
  threshold: number
  subjects: Subject[]
}

const { t, d } = useI18n()
const slug = useRoute().params.slug as string
const page = ref<ProfessorPage | null>(null)
const labels = ref<Lookups | null>(null)
const error = ref('')

onMounted(async () => {
  try {
    page.value = await api<ProfessorPage>(`GET`, `/api/professors/${encodeURIComponent(slug)}`)
    labels.value = await lookups()
  } catch (e) {
    error.value = describeError(t, e)
  }
})

function missing(n: number) {
  return n === 0
    ? t('course.noRatings')
    : t('course.locked', { missing: page.value!.threshold - n })
}
</script>

<template>
  <main class="page">
    <button
      class="inline-flex min-h-11 items-center gap-1 text-sm text-mask hover:underline"
      @click="$router.back()"
    >
      <ArrowLeft class="size-4" aria-hidden="true" /> {{ t('common.back') }}
    </button>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <template v-if="page">
      <span class="eyebrow mt-2">{{ page.department }}</span>
      <h1 class="h1">{{ page.title }} {{ page.fullName }}</h1>
      <AppNotice v-if="page.archived">{{ t('professor.archived') }}</AppNotice>
      <h2 class="h2 mt-8">{{ t('professor.subjects') }}</h2>
      <p v-if="!page.subjects.length" class="mt-3 text-sm text-muted">
        {{ t('professor.noSubjects') }}
      </p>
      <article v-for="subject in page.subjects" :key="subject.subjectId" class="card mt-3">
        <RouterLink :to="`/courses/${subject.itemId}`" class="font-semibold">{{
          subject.subjectName
        }}</RouterLink>
        <p class="mt-1 text-sm text-muted">
          {{ subject.components
          }}<template v-if="subject.radar">
            · <span class="mono text-xs">n = {{ subject.radar.n }}</span></template
          >
        </p>
        <p
          v-if="!subject.radar || subject.radar.n < page.threshold"
          class="mt-2 text-sm text-muted italic"
        >
          {{ missing(subject.radar?.n ?? 0) }}
        </p>
        <template v-else>
          <RadarChart
            v-if="labels"
            :scores="subject.radar.scores"
            :axes="labels.axes"
            :pillars="labels.pillars"
          />
          <details v-if="subject.comments.length" class="mt-3">
            <summary class="cursor-pointer text-sm font-medium text-mask">
              {{ t('course.comments', { n: subject.comments.length }) }}
            </summary>
            <blockquote v-for="c in subject.comments" :key="c.id" class="quote mt-3 text-sm">
              {{ c.body }}
              <footer class="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
                {{ t('course.week', { date: d(c.week, 'short') }) }}
                <ReportButton :post-id="c.id" />
              </footer>
            </blockquote>
          </details>
        </template>
      </article>
    </template>
  </main>
</template>
