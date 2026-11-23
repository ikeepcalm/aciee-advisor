<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  CircleUser,
  Coffee,
  Cookie,
  EyeOff,
  CodeXml,
  KeyRound,
  MessageSquareWarning,
} from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import BrandMark from '@/components/BrandMark.vue'
import { api } from '@/api'

defineProps<{ feedbackLink: RouteLocationRaw }>()

const { t } = useI18n()
const stats = ref<{ students: number; ratings: number } | null>(null)

onMounted(() => {
  if (!matchMedia('(min-width: 48rem)').matches) return
  api<{ students: number; ratings: number }>('GET', '/api/catalog/stats')
    .then((s) => (stats.value = s))
    .catch(() => {})
})

const facts = [
  { key: 'account', icon: CircleUser },
  { key: 'ratings', icon: EyeOff },
  { key: 'tokens', icon: KeyRound },
  { key: 'tracking', icon: Cookie },
]
</script>

<template>
  <footer class="band dark hidden border-t-2 border-dashed border-line text-ink md:block">
    <div
      class="mx-auto grid w-full max-w-7xl grid-cols-[1fr_1.4fr] gap-x-12 gap-y-8 px-6 pt-10 pb-12 text-sm text-muted lg:grid-cols-[1fr_1.5fr_auto] lg:gap-x-16 xl:px-10"
    >
      <section>
        <p class="display flex items-center gap-2 text-2xl text-ink">
          <BrandMark class="size-7 shrink-0" />
          ACIEE Advisor
        </p>
        <p class="mt-3">{{ t('feedback.footerNote') }}</p>
        <p class="mt-1">{{ t('footer.free') }}</p>
        <dl v-if="stats" class="mt-4 flex gap-8">
          <div
            v-for="key in (['students', 'ratings'] as const).filter((k) => stats![k] > 0)"
            :key="key"
          >
            <dt class="text-xs">{{ t(`footer.stats.${key}`) }}</dt>
            <dd class="display m-0 text-3xl leading-tight text-ink">{{ stats[key] }}</dd>
          </div>
        </dl>
        <a
          href="https://github.com/ikeepcalm/aciee-advisor"
          target="_blank"
          rel="noopener"
          class="mt-1 inline-flex min-h-11 items-center gap-2 font-semibold"
        >
          <CodeXml class="size-4 shrink-0" aria-hidden="true" />
          {{ t('footer.source') }}
        </a>
      </section>

      <section>
        <h2 class="eyebrow">{{ t('footer.dataTitle') }}</h2>
        <ul class="m-0 grid list-none gap-2.5 p-0">
          <li v-for="fact in facts" :key="fact.key" class="flex gap-2.5">
            <component
              :is="fact.icon"
              class="mt-0.5 size-4 shrink-0 text-copper"
              aria-hidden="true"
            />
            <span>
              <strong class="font-semibold text-ink">{{ t(`footer.${fact.key}.lead`) }}</strong>
              {{ t(`footer.${fact.key}.text`) }}
            </span>
          </li>
        </ul>
      </section>

      <section
        class="col-span-2 flex items-center gap-x-8 gap-y-3 lg:col-span-1 lg:w-56 lg:flex-col lg:items-start"
      >
        <div class="coffee-note border-on-mark bg-mark px-4 pt-2.5 pb-1 text-on-mark">
          <p class="display text-xl">{{ t('footer.useful') }}</p>
          <a
            href="https://buymeacoffee.com/ikeepcalm"
            target="_blank"
            rel="noopener"
            class="mt-1 inline-flex min-h-11 items-center gap-2 font-semibold text-on-mark"
          >
            <Coffee class="size-4 shrink-0" aria-hidden="true" />
            {{ t('footer.coffee') }}
          </a>
        </div>
        <div>
          <RouterLink
            :to="feedbackLink"
            class="inline-flex min-h-11 items-center gap-2 font-semibold"
          >
            <MessageSquareWarning class="size-4 shrink-0" aria-hidden="true" />
            {{ t('feedback.footerLink') }}
          </RouterLink>
          <p class="text-xs">{{ t('footer.reportNote') }}</p>
        </div>
      </section>
    </div>
  </footer>
</template>

<style scoped>
.band {
  background-color: var(--band);
  background-image:
    linear-gradient(var(--rule) 1px, transparent 1px),
    linear-gradient(90deg, var(--rule) 1px, transparent 1px);
  background-size: 1.5rem 1.5rem;
}

.coffee-note {
  border-width: 1.5px;
  border-radius: var(--wobble-alt);
  rotate: -1.5deg;
  box-shadow: 3px 4px 0 rgb(0 0 0 / 0.45);
}

.coffee-note a {
  text-decoration-color: currentColor;
}

.coffee-note a:focus-visible {
  outline-color: currentColor;
}
</style>
