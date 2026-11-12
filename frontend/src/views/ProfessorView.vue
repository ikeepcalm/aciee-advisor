<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import RadarChart from '@/components/RadarChart.vue'
import CommentList from '@/components/CommentList.vue'
import RateInvite from '@/components/RateInvite.vue'
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

const { t } = useI18n()
const router = useRouter()
const slug = useRoute().params.slug as string
const page = ref<ProfessorPage | null>(null)
const labels = ref<Lookups | null>(null)
const error = ref('')

async function load() {
  error.value = ''
  try {
    ;[page.value, labels.value] = await Promise.all([
      api<ProfessorPage>(`GET`, `/api/professors/${encodeURIComponent(slug)}`),
      lookups(),
    ])
  } catch (e) {
    error.value = describeError(t, e)
  }
}

function goBack() {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push({ name: 'catalog' })
  }
}

onMounted(load)

function missing(n: number) {
  return n === 0
    ? t('course.noRatings')
    : t('course.locked', { missing: page.value!.threshold - n })
}
</script>

<template>
  <main class="page-wide">
    <button
      class="inline-flex min-h-11 items-center gap-1 text-sm text-mask hover:underline"
      @click="goBack"
    >
      <ArrowLeft class="size-4" aria-hidden="true" /> {{ t('common.back') }}
    </button>
    <AppNotice v-if="error" kind="error">
      {{ error }}
      <button class="btn btn-outline mt-2 flex" @click="load">{{ t('common.retry') }}</button>
    </AppNotice>
    <p v-else-if="!page" class="mt-4 text-muted" role="status">{{ t('common.loading') }}</p>
    <template v-if="page">
      <span class="eyebrow mt-2">{{ page.department }}</span>
      <h1 class="h1">{{ page.title }} {{ page.fullName }}</h1>
      <AppNotice v-if="page.archived">{{ t('professor.archived') }}</AppNotice>
      <RateInvite class="mt-5" />
      <h2 class="h2 mt-8">{{ t('professor.subjects') }}</h2>
      <p v-if="!page.subjects.length" class="mt-3 text-sm text-muted">
        {{ t('professor.noSubjects') }}
      </p>
      <div class="card-grid mt-3">
        <article v-for="subject in page.subjects" :key="subject.subjectId" class="card">
          <RouterLink
            :to="`/courses/${subject.itemId}`"
            class="flex min-h-11 items-center font-semibold"
            >{{ subject.subjectName }}</RouterLink
          >
          <p class="text-sm text-muted">
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
            <CommentList :comments="subject.comments" />
          </template>
        </article>
      </div>
    </template>
  </main>
</template>
