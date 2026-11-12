<script setup lang="ts">
import { computed, nextTick, onUnmounted, watch, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  ArrowRight,
  CalendarDays,
  ChevronDown,
  ClipboardCheck,
  Coins,
  ExternalLink,
  GraduationCap,
  MapPin,
  NotebookPen,
  Scale,
  ShieldCheck,
} from 'lucide-vue-next'
import { guideFor } from '@/guide/content'

const { t, locale } = useI18n()
const route = useRoute()
const guide = computed(() => guideFor(locale.value))
const icons: Record<string, typeof MapPin> = {
  credits: GraduationCap,
  attendance: ClipboardCheck,
  exams: NotebookPen,
  calendar: CalendarDays,
  scholarships: Coins,
  budget: Scale,
  contact: MapPin,
}
const baseTitle = document.title

watchEffect(() => (document.title = `${guide.value.title} · ${t('app.title')}`))
onUnmounted(() => (document.title = baseTitle))

function reveal(id: string) {
  const section = document.getElementById(id)
  if (section instanceof HTMLDetailsElement) {
    section.open = true
    section.scrollIntoView()
  }
}

watch(
  () => route.hash,
  async (hash) => {
    await nextTick()
    if (hash) {
      reveal(hash.slice(1))
    }
  },
  { immediate: true },
)
</script>

<template>
  <main class="page-wide">
    <header>
      <h1 class="h1">{{ guide.title }}</h1>
      <p class="lede text-sm md:text-base">{{ guide.lede }}</p>
    </header>

    <nav
      class="-mx-4 mt-5 flex gap-1.5 overflow-x-auto px-4 pb-1 md:mx-0 md:flex-wrap md:px-0"
      :aria-label="t('guide.index')"
    >
      <RouterLink
        v-for="section in guide.sections"
        :key="section.id"
        :to="{ hash: `#${section.id}` }"
        replace
        @click="reveal(section.id)"
        class="chip shrink-0 gap-1.5 px-3 text-xs whitespace-nowrap text-ink no-underline"
      >
        <component :is="icons[section.id]" class="size-4 text-copper" aria-hidden="true" />
        {{ section.title }}
      </RouterLink>
    </nav>

    <div class="mt-4 grid items-start gap-3 md:grid-cols-2 xl:grid-cols-3">
      <details
        v-for="section in guide.sections"
        :id="section.id"
        :key="section.id"
        class="group card p-0"
      >
        <summary
          class="flex min-h-11 cursor-pointer list-none items-start gap-3 p-4 [&::-webkit-details-marker]:hidden"
        >
          <component
            :is="icons[section.id]"
            class="mt-0.5 size-6 shrink-0 text-copper"
            aria-hidden="true"
          />
          <span class="min-w-0 flex-1">
            <h2 class="h2">{{ section.title }}</h2>
            <span class="mt-1.5 block font-medium text-pretty">{{ section.short }}</span>
          </span>
          <ChevronDown
            class="chevron mt-1 size-5 shrink-0 text-muted group-open:rotate-180"
            aria-hidden="true"
          />
        </summary>
        <div class="border-t border-dashed border-line px-4 pt-3 pb-4">
          <ul class="m-0 grid list-none gap-2 p-0 text-sm">
            <li v-for="point in section.points" :key="point" class="point">{{ point }}</li>
          </ul>
          <RouterLink
            v-if="section.action"
            :to="section.action.to"
            class="btn btn-outline mt-4 w-full text-center"
          >
            {{ section.action.label }}
            <ArrowRight class="size-4 shrink-0" aria-hidden="true" />
          </RouterLink>
          <p class="mt-3 flex flex-wrap items-center gap-x-3 text-xs text-muted">
            <span class="mono uppercase">{{ guide.sources }}</span>
            <a
              v-for="source in section.sources"
              :key="source.label"
              :href="source.url"
              target="_blank"
              rel="noopener"
              class="inline-flex min-h-11 items-center gap-1 text-muted"
            >
              {{ source.label }}
              <ExternalLink class="size-3 shrink-0" aria-hidden="true" />
              <span class="sr-only">({{ t('guide.newTab') }})</span>
            </a>
          </p>
        </div>
      </details>
      <p class="flex max-w-prose gap-2 self-center px-1 py-2 text-xs text-muted xl:col-span-2">
        <ShieldCheck class="size-4 shrink-0" aria-hidden="true" />
        {{ guide.checked }}
      </p>
    </div>
  </main>
</template>

<style scoped>
.point {
  position: relative;
  padding-left: 1.5rem;
}

.point::before {
  content: '';
  position: absolute;
  top: 0.5rem;
  left: 0;
  width: 0.9rem;
  height: 0.4rem;
  background: var(--copper);
  mask: var(--scribble) center / 100% 100% no-repeat;
}

@media (prefers-reduced-motion: no-preference) {
  .chevron {
    transition: rotate 150ms ease-out;
  }
}
</style>
