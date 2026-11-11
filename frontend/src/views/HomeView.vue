<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ArrowRight,
  BookOpen,
  CalendarDays,
  ClipboardList,
  FileText,
  Gauge,
  Radar,
  ShieldCheck,
  Star,
  Users,
} from 'lucide-vue-next'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import RadarChart from '@/components/RadarChart.vue'
import { localTokens, pendingOfferings, type RateableOffering } from '@/crypto/tokens'

const { t } = useI18n()
const auth = useAuthStore()
const pending = ref(0)
const signedOut = computed(() => auth.loaded && !auth.user)

const features = [
  { key: 'catalog', icon: BookOpen, to: { name: 'catalog' }, link: 'home.browse' },
  { key: 'survival', icon: Gauge },
  { key: 'professors', icon: Radar },
  { key: 'timetable', icon: CalendarDays, to: { name: 'timetable' }, link: 'home.openTimetable' },
  { key: 'groups', icon: Users, to: { name: 'group' }, link: 'nav.groups', signedIn: true },
  {
    key: 'control',
    icon: FileText,
    to: { name: 'submissions' },
    link: 'nav.submissions',
    signedIn: true,
  },
]
const steps = ['account', 'token', 'submit', 'recovery']
const sampleScores = [8.4, 7.6, 9.1, 6.8, 7.9, 8.8, 6.2, 7.4, 9, 8.1, 6.9, 7.7, 8.6, 7.2]
const sampleAxes = sampleScores.map((_, index) => ({ code: String(index), label: '', group: null }))
const sampleStats = computed(() => [
  [t('rateForm.examDifficulty'), '6.8 / 10'],
  [t('rateForm.labsHelp'), '8.4 / 10'],
  [t('rateForm.weeklyHours'), '5'],
])

watch(
  () => auth.user,
  async (user) => {
    pending.value = 0
    if (!user) return
    pending.value = await Promise.all([
      api<RateableOffering[]>('GET', '/api/tokens/offerings'),
      localTokens(user.id),
    ]).then(
      ([offerings, tokens]) => pendingOfferings(offerings, tokens).length,
      () => 0,
    )
  },
  { immediate: true },
)
</script>

<template>
  <main>
    <section
      class="page-wide grid items-center gap-10 pt-8 md:grid-cols-[1.1fr_0.9fr] md:gap-12 md:pt-14 lg:pt-20"
    >
      <div>
        <span class="eyebrow">{{ t('home.eyebrow') }}</span>
        <h1 class="display text-[clamp(2.125rem,8.5vw,3.75rem)] leading-[1.22]">
          {{ t('home.title') }}
          <span class="hl">{{ t('home.titleMark') }}</span>
        </h1>
        <p class="lede text-base md:text-lg">{{ t('home.lede') }}</p>

        <div class="mt-6 min-h-36 sm:min-h-24">
          <template v-if="auth.loaded">
            <section v-if="auth.user && pending" class="pending card mb-4">
              <div class="flex items-start gap-3">
                <ClipboardList class="size-6 shrink-0 text-copper" aria-hidden="true" />
                <div>
                  <h2 class="h2">{{ t('home.pending', { n: pending }) }}</h2>
                  <p class="mt-1 text-sm">{{ t('home.pendingBody') }}</p>
                </div>
              </div>
              <RouterLink :to="{ name: 'rate' }" class="btn btn-primary mt-4 w-full sm:w-auto">
                {{ t('home.fillIn') }}
                <ArrowRight class="size-4" aria-hidden="true" />
              </RouterLink>
            </section>
            <div class="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
              <RouterLink
                v-if="signedOut"
                :to="{ name: 'signup' }"
                class="btn btn-primary col-span-2"
              >
                {{ t('auth.signUp') }}
              </RouterLink>
              <RouterLink
                :to="{ name: 'catalog' }"
                class="btn"
                :class="auth.user && !pending ? 'btn-primary' : 'btn-outline'"
              >
                <BookOpen class="size-4" aria-hidden="true" />
                {{ t('home.browse') }}
              </RouterLink>
              <RouterLink :to="{ name: 'timetable' }" class="btn btn-outline">
                <CalendarDays class="size-4" aria-hidden="true" />
                {{ t('nav.timetable') }}
              </RouterLink>
            </div>
            <p v-if="signedOut" class="mt-1 text-sm text-muted">
              {{ t('auth.haveAccount') }}
              <RouterLink :to="{ name: 'signin' }" class="inline-flex min-h-11 items-center">
                {{ t('auth.signInAction') }}
              </RouterLink>
            </p>
          </template>
        </div>
      </div>

      <div class="mx-auto w-full max-w-sm md:max-w-md" aria-hidden="true">
        <p class="eyebrow">{{ t('home.sampleCaption') }}</p>
        <div class="stack">
          <figure class="sheet card m-0">
            <div class="flex items-center justify-between gap-2">
              <span class="tag bg-board text-muted">{{ t('home.sample') }}</span>
              <span class="mono text-xs text-muted">n = 23</span>
            </div>
            <strong class="display mt-3 block text-xl">{{ t('home.sampleCourse') }}</strong>
            <span class="mt-1.5 flex flex-wrap gap-x-3 gap-y-1">
              <span class="kind-tag k-C">{{ t('catalog.lecture') }} 2h</span>
              <span class="kind-tag k-L">{{ t('catalog.lab') }} 2h</span>
            </span>
            <RadarChart :scores="sampleScores" :axes="sampleAxes" :pillars="[]" />
            <dl
              class="m-0 grid grid-cols-3 gap-px overflow-hidden rounded-lg border border-line bg-line"
            >
              <div v-for="[label, value] in sampleStats" :key="label" class="bg-panel p-2.5">
                <dt class="text-xs text-muted">{{ label }}</dt>
                <dd class="mono m-0 mt-1 text-sm font-medium">{{ value }}</dd>
              </div>
            </dl>
          </figure>
        </div>
      </div>
    </section>

    <section class="page-wide">
      <h2 class="display text-2xl md:text-3xl">{{ t('home.featuresTitle') }}</h2>
      <ul class="m-0 mt-5 grid list-none gap-3 p-0 sm:grid-cols-2 lg:grid-cols-3">
        <li v-for="feature in features" :key="feature.key" class="card flex flex-col">
          <component :is="feature.icon" class="size-6 text-copper" aria-hidden="true" />
          <h3 class="h2 mt-3">{{ t(`home.features.${feature.key}.title`) }}</h3>
          <p class="mt-1.5 text-sm text-muted">{{ t(`home.features.${feature.key}.body`) }}</p>
          <RouterLink
            v-if="feature.to && (!feature.signedIn || auth.user)"
            :to="feature.to"
            class="mt-auto inline-flex min-h-11 items-center gap-1 self-start pt-2 text-sm font-semibold"
          >
            {{ t(feature.link) }}
            <ArrowRight class="size-4" aria-hidden="true" />
          </RouterLink>
        </li>
      </ul>
    </section>

    <section class="page-wide grid gap-8 md:grid-cols-2 md:gap-12">
      <div>
        <span class="eyebrow">{{ t('rateForm.anonymous') }}</span>
        <h2 class="display text-2xl md:text-3xl">{{ t('home.anonTitle') }}</h2>
        <p class="lede">{{ t('home.anonLede') }}</p>
        <p class="mt-5 flex max-w-prose gap-2 text-sm text-muted">
          <ShieldCheck class="size-5 shrink-0 text-copper" aria-hidden="true" />
          {{ t('home.anonNote') }}
        </p>
      </div>
      <ol class="m-0 list-none p-0">
        <li v-for="(step, index) in steps" :key="step" class="step">
          <span class="pad mono">{{ index + 1 }}</span>
          <h3 class="h2 text-lg">{{ t(`home.steps.${step}.title`) }}</h3>
          <p class="mt-1 text-sm text-muted">{{ t(`home.steps.${step}.body`) }}</p>
        </li>
      </ol>
    </section>

    <section class="page-wide">
      <div
        class="card flex flex-col items-start gap-4 p-6 md:flex-row md:items-center md:justify-between md:p-8"
      >
        <div>
          <h2 class="display text-2xl">{{ t('home.closingTitle') }}</h2>
          <p v-if="signedOut" class="mt-1.5 text-sm text-muted">{{ t('home.closingBody') }}</p>
        </div>
        <div class="flex w-full shrink-0 flex-col gap-2 sm:w-auto sm:flex-row">
          <template v-if="signedOut">
            <RouterLink :to="{ name: 'signup' }" class="btn btn-primary">
              {{ t('auth.signUp') }}
            </RouterLink>
            <RouterLink :to="{ name: 'signin' }" class="btn btn-outline">
              {{ t('auth.signInAction') }}
            </RouterLink>
          </template>
          <RouterLink v-else-if="auth.user" :to="{ name: 'rate' }" class="btn btn-primary">
            <Star class="size-4" aria-hidden="true" />
            {{ t('nav.rate') }}
          </RouterLink>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.pending {
  rotate: -0.5deg;
  border-color: var(--ink);
  background: color-mix(in srgb, var(--mark) 16%, var(--panel));
}

.stack {
  position: relative;
}

.stack::before {
  content: '';
  position: absolute;
  inset: 0;
  translate: 0.25rem 0.4rem;
  rotate: -2.5deg;
  border: 1.5px solid var(--line);
  border-radius: var(--wobble-alt);
  background: var(--panel);
}

.sheet {
  position: relative;
  rotate: 1.2deg;
  pointer-events: none;
}

.sheet::before {
  content: '';
  position: absolute;
  top: -0.7rem;
  left: 50%;
  width: 5.5rem;
  height: 1.5rem;
  translate: -50% 0;
  rotate: -3deg;
  background: color-mix(in srgb, var(--mark) 75%, transparent);
}

.sheet :deep(.area) {
  transform-origin: 150px 150px;
  animation: radar-in 900ms 150ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.sheet :deep(.mark) {
  animation: marks-in 400ms 800ms ease-out both;
}

@keyframes radar-in {
  from {
    opacity: 0;
    scale: 0.3;
  }
}

@keyframes marks-in {
  from {
    opacity: 0;
  }
}

.step {
  position: relative;
  padding: 0 0 1.5rem 3rem;
}

.step:last-child {
  padding-bottom: 0;
}

.step:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 2.25rem;
  bottom: 0.25rem;
  left: calc(1rem - 1px);
  border-left: 2px dashed color-mix(in srgb, var(--ink) 35%, transparent);
}

.pad {
  position: absolute;
  top: -0.2rem;
  left: 0;
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 2px solid var(--copper);
  border-radius: 47% 53% 44% 56% / 55% 46% 54% 45%;
  color: var(--copper);
  font-family: var(--font-display);
  font-size: 1.125rem;
  font-weight: 700;
}

@media (prefers-reduced-motion: reduce) {
  .sheet :deep(.area),
  .sheet :deep(.mark) {
    animation: none;
  }
}
</style>
