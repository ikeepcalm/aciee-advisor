<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Message from 'primevue/message'
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
    page.value = await api<ProfessorPage>('GET', `/api/professors/${encodeURIComponent(slug)}`)
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
  <main>
    <a href="#" @click.prevent="$router.back()">← {{ t('common.back') }}</a>
    <Message v-if="error" severity="error">{{ error }}</Message>
    <template v-if="page">
      <h1>{{ page.title }} {{ page.fullName }}</h1>
      <p class="muted">{{ page.department }}</p>
      <Message v-if="page.archived" severity="info">{{ t('professor.archived') }}</Message>
      <h2>{{ t('professor.subjects') }}</h2>
      <p v-if="!page.subjects.length" class="muted">{{ t('professor.noSubjects') }}</p>
      <article v-for="subject in page.subjects" :key="subject.subjectId" class="card">
        <RouterLink :to="`/courses/${subject.itemId}`"
          ><strong>{{ subject.subjectName }}</strong></RouterLink
        >
        <p class="muted">
          {{ subject.components
          }}<template v-if="subject.radar"> · n = {{ subject.radar.n }}</template>
        </p>
        <p v-if="!subject.radar || subject.radar.n < page.threshold" class="locked">
          {{ missing(subject.radar?.n ?? 0) }}
        </p>
        <template v-else>
          <RadarChart
            v-if="labels"
            :scores="subject.radar.scores"
            :axes="labels.axes"
            :pillars="labels.pillars"
          />
          <details v-if="subject.comments.length">
            <summary>{{ t('course.comments', { n: subject.comments.length }) }}</summary>
            <blockquote v-for="c in subject.comments" :key="c.id">
              {{ c.body }}
              <footer class="muted">
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

<style scoped>
h2 {
  font-size: 1.05rem;
  margin: 1.5rem 0 0.5rem;
}

blockquote {
  margin: 0.5rem 0;
  white-space: pre-wrap;
}
</style>
