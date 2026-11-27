<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, ArrowRight, CalendarDays, ExternalLink } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import RadarChart from '@/components/RadarChart.vue'
import CommentList from '@/components/CommentList.vue'
import RateInvite from '@/components/RateInvite.vue'
import ProfessorAvatar from '@/components/ProfessorAvatar.vue'
import SegmentedChips from '@/components/SegmentedChips.vue'
import {
  ApiError,
  api,
  describeError,
  lookups,
  type Lookups,
  type ProfessorPage,
  type Radar,
} from '@/api'
import { showNotFound } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { academicYear, pillarAverages, radarFor } from '@/professors'

const ALL = 'all'
const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const slug = route.params.slug as string
const page = ref<ProfessorPage | null>(null)
const labels = ref<Lookups | null>(null)
const error = ref('')
const year = ref(ALL)
const wide = window.matchMedia('(min-width: 64rem)').matches

async function load() {
  error.value = ''
  try {
    ;[page.value, labels.value] = await Promise.all([
      api<ProfessorPage>(`GET`, `/api/professors/${encodeURIComponent(slug)}`),
      lookups(),
    ])
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return showNotFound()
    error.value = describeError(t, e)
  }
}

function goBack() {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push({ name: 'professors' })
  }
}

onMounted(load)

function missing(n: number) {
  return n === 0
    ? t('course.noRatings')
    : t('course.locked', { missing: page.value!.threshold - n })
}

const bio = computed(() =>
  locale.value === 'ro'
    ? (page.value?.bioRo ?? page.value?.bioEn)
    : (page.value?.bioEn ?? page.value?.bioRo),
)

const pillars = computed(() =>
  (labels.value?.pillars ?? [])
    .map((pillar) => ({
      label: pillar.label,
      value:
        page.value?.[pillar.code as 'lecture' | 'lab' | 'seminar' | 'project' | 'grading'] ?? null,
    }))
    .filter((pillar) => pillar.value !== null),
)

function subjectPillars(radar: Radar) {
  const own = radarFor(
    labels.value?.axes ?? [],
    labels.value?.pillars ?? [],
    radar.scores,
    radar.component,
  )
  return pillarAverages(own.axes, own.pillars, own.scores)
}

const years = computed(() => {
  const present = new Set(
    (page.value?.subjects ?? []).flatMap((s) => s.comments.map((c) => academicYear(c.week))),
  )
  return [...present].sort().reverse()
})

const yearOptions = computed(() => [
  { value: ALL, label: t('professor.allYears') },
  ...years.value.map((value) => ({ value, label: value })),
])

const subjects = computed(() =>
  (page.value?.subjects ?? [])
    .map((subject) => ({
      ...subject,
      comments: subject.comments.filter(
        (c) => year.value === ALL || academicYear(c.week) === year.value,
      ),
    }))
    .filter((subject) => {
      const start = Number(year.value.slice(0, 4))
      return (
        year.value === ALL ||
        subject.comments.length ||
        (subject.firstYear <= start && subject.lastYear > start)
      )
    }),
)
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
      <AppNotice v-if="page.archived">{{ t('professor.archived') }}</AppNotice>
      <div class="max-lg:flex max-lg:flex-col">
        <header class="mt-2 grid gap-4 max-lg:contents lg:grid-cols-2 lg:items-center lg:gap-x-10">
          <div class="flex items-center gap-4 max-lg:mt-2 md:gap-6">
            <ProfessorAvatar
              :name="page.fullName"
              :photo="page.photo"
              class="size-20 shrink-0 md:size-28"
            />
            <div class="min-w-0">
              <span class="eyebrow">{{ page.department }}</span>
              <h1 class="h1">{{ page.fullName }}</h1>
              <p class="mt-1 text-sm text-muted">{{ page.title }}</p>
            </div>
          </div>

          <section v-if="bio || page.cvUrl" class="max-w-prose max-lg:order-1 max-lg:mt-4">
            <p v-if="bio" class="text-sm md:text-base">{{ bio }}</p>
            <p class="flex flex-wrap items-center gap-x-2 text-xs text-muted">
              <span v-if="bio">{{ t('professor.bioNote') }}</span>
              <a
                v-if="page.cvUrl"
                :href="page.cvUrl"
                target="_blank"
                rel="noopener"
                class="inline-flex min-h-11 items-center gap-1 font-medium"
              >
                {{ t('professor.cv') }}
                <ExternalLink class="size-3.5" aria-hidden="true" />
              </a>
            </p>
          </section>
        </header>

        <section class="score card mt-5">
          <h2 class="sr-only">{{ t('professor.score') }}</h2>
          <div
            v-if="page.overall !== null"
            class="grid gap-5 lg:grid-cols-[20rem_minmax(0,1fr)] lg:gap-10"
          >
            <div class="lg:self-start">
              <p class="flex items-baseline gap-2">
                <span class="score-value display text-6xl leading-none">{{
                  page.overall.toFixed(1)
                }}</span>
                <span class="mono text-sm text-muted">/ 10</span>
              </p>
              <p class="mt-2 text-sm text-muted">
                {{
                  t('professor.basis', { ratings: page.ratings, subjects: page.subjects.length })
                }}
              </p>
              <ul class="m-0 mt-4 grid list-none gap-2 p-0">
                <li
                  v-for="pillar in pillars"
                  :key="pillar.label"
                  class="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 text-sm"
                >
                  <span>{{ pillar.label }}</span>
                  <strong class="mono font-medium">
                    {{ pillar.value === null ? '—' : pillar.value.toFixed(1) }}
                  </strong>
                  <span class="bar col-span-2 mt-1" aria-hidden="true">
                    <span :style="{ width: `${(pillar.value ?? 0) * 10}%` }" />
                  </span>
                </li>
              </ul>
            </div>
            <div
              v-if="page.radar && labels"
              class="border-t border-line pt-4 lg:border-t-0 lg:border-l lg:pt-0 lg:pl-10"
            >
              <h3 class="eyebrow">{{ t('professor.radar') }}</h3>
              <RadarChart v-bind="radarFor(labels.axes, [], page.radar.scores)" :open="wide" />
            </div>
          </div>
          <template v-else>
            <p class="display text-3xl leading-tight">{{ t('professor.fewRatings') }}</p>
            <p class="mt-1 text-sm text-muted">{{ t('professor.fewRatingsBody') }}</p>
            <RouterLink v-if="auth.user" :to="{ name: 'rate' }" class="btn btn-primary mt-4 w-full">
              {{ t('nav.rate') }}
              <ArrowRight class="size-4" aria-hidden="true" />
            </RouterLink>
            <RouterLink
              v-else-if="auth.loaded"
              :to="{ name: 'signin', query: { next: route.fullPath } }"
              class="btn btn-outline mt-4 w-full"
            >
              {{ t('auth.signInAction') }}
            </RouterLink>
          </template>
        </section>
      </div>

      <RateInvite v-if="page.overall !== null" class="mt-5" />

      <h2 class="h2 mt-8">{{ t('professor.subjects') }}</h2>
      <p v-if="!page.subjects.length" class="mt-3 text-sm text-muted">
        {{ t('professor.noSubjects') }}
      </p>
      <template v-if="years.length">
        <SegmentedChips
          v-model="year"
          :options="yearOptions"
          size="sm"
          class="mono mt-3"
          :aria-label="t('course.academicYear')"
        />
        <p v-if="year !== ALL" class="mt-2 text-xs text-muted" role="status">
          {{ t('professor.yearHint', { year }) }}
        </p>
      </template>
      <div class="card-grid mt-3">
        <article v-for="subject in subjects" :key="subject.subjectId" class="card">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="tag bg-board text-muted">{{ subject.components }}</span>
            <span class="mono inline-flex items-center gap-1 text-xs font-medium">
              <CalendarDays class="size-3.5 text-copper" aria-hidden="true" />
              <span class="sr-only">{{ t('professor.taught') }}:</span>
              {{ subject.firstYear }}–{{ subject.lastYear }}
            </span>
          </div>
          <RouterLink
            :to="`/courses/${subject.itemId}`"
            class="flex min-h-11 items-center font-semibold"
            >{{ subject.subjectName }}</RouterLink
          >
          <p v-if="!subject.radars.length" class="text-sm text-muted italic">
            {{ missing(0) }}
          </p>
          <section v-for="radar in subject.radars" :key="radar.component ?? ''" class="mt-2">
            <h3 class="flex flex-wrap items-baseline justify-between gap-x-3 text-sm font-semibold">
              {{ t(`group.component.${radar.component}`) }}
              <span
                class="text-xs font-normal text-muted"
                :class="{ italic: radar.n < page.threshold }"
              >
                {{
                  radar.n < page.threshold
                    ? missing(radar.n)
                    : t('course.ratingsCount', { n: radar.n })
                }}
              </span>
            </h3>
            <dl v-if="radar.n >= page.threshold" class="m-0 mt-1 text-sm">
              <div
                v-for="pillar in subjectPillars(radar)"
                :key="pillar.label"
                class="flex justify-between gap-4 border-t border-line py-1.5"
              >
                <dt>{{ pillar.label }}</dt>
                <dd class="mono m-0 font-medium">
                  {{ pillar.value === null ? '—' : pillar.value.toFixed(1) }}
                </dd>
              </div>
            </dl>
          </section>
          <CommentList
            v-if="subject.comments.length"
            :comments="subject.comments"
            :open="year !== ALL"
          />
        </article>
      </div>
    </template>
  </main>
</template>

<style scoped>
.score {
  border-color: var(--ink);
  box-shadow: 2px 3px 0 color-mix(in srgb, var(--ink) 28%, transparent);
}

.score-value {
  padding: 0.05em 0.2em 0;
  border-radius: 0.5em 0.2em;
  color: var(--on-mark);
  background: var(--mark);
  rotate: -2deg;
}

.bar {
  display: block;
  height: 0.625rem;
  overflow: hidden;
  border: 1.5px solid var(--line);
  border-radius: var(--wobble);
  background: var(--board);
}

.bar > span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--mask);
}
</style>
