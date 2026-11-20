<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronRight } from 'lucide-vue-next'
import type { Option } from '@/api'
import { pillarAverages as averagePillars } from '@/professors'

const props = defineProps<{
  scores: (number | null)[]
  axes: Option[]
  pillars: Option[]
  open?: boolean
}>()

const { t } = useI18n()

const size = 300
const center = size / 2
const radius = 105

function point(index: number, value: number) {
  const angle = (Math.PI * 2 * index) / props.axes.length - Math.PI / 2
  const r = (radius * value) / 10
  return { x: center + r * Math.cos(angle), y: center + r * Math.sin(angle) }
}

const rings = [2, 4, 6, 8, 10].map((v) =>
  props.axes
    .map((_, i) => point(i, v))
    .map((p) => `${p.x},${p.y}`)
    .join(' '),
)
const spokes = computed(() => props.axes.map((_, i) => point(i, 10)))
const labels = computed(() =>
  props.axes.map((axis, i) => ({ ...point(i, 11.6), n: i + 1, label: axis.label })),
)
const marks = computed(() =>
  props.scores
    .map((score, i) =>
      score === null ? null : { ...point(i, score), score, label: props.axes[i]?.label ?? '' },
    )
    .filter((m) => m !== null),
)
const shape = computed(() => marks.value.map((m) => `${m.x},${m.y}`).join(' '))
const criteria = computed(() =>
  props.axes
    .map((axis, i) => ({ n: i + 1, label: axis.label, score: props.scores[i] ?? null }))
    .filter((criterion) => criterion.label),
)
const pillarAverages = computed(() => averagePillars(props.axes, props.pillars, props.scores))
const extremes = computed(() => {
  const scored = criteria.value.filter((criterion) => criterion.score !== null)
  const ranked = [...scored].sort((a, b) => b.score! - a.score!)
  const highest = ranked[0]
  const lowest = ranked.at(-1)
  return scored.length > 2 && highest && lowest && highest.score !== lowest.score
    ? [
        { ...highest, title: t('course.ratedHighest') },
        { ...lowest, title: t('course.ratedLowest') },
      ]
    : []
})
</script>

<template>
  <figure class="radar">
    <div class="layout">
      <svg
        :viewBox="`0 0 ${size} ${size}`"
        role="img"
        :aria-label="
          pillarAverages.map((p) => `${p.label}: ${p.value?.toFixed(1) ?? '—'}`).join('; ') ||
          t('course.criteria')
        "
      >
        <polygon v-for="(ring, i) in rings" :key="i" :points="ring" class="grid" />
        <line
          v-for="(s, i) in spokes"
          :key="i"
          :x1="center"
          :y1="center"
          :x2="s.x"
          :y2="s.y"
          class="grid"
        />
        <text
          v-for="l in labels"
          :key="l.n"
          :x="l.x"
          :y="l.y"
          class="axis"
          :class="{ flagged: extremes.some((extreme) => extreme.n === l.n) }"
          text-anchor="middle"
          dominant-baseline="middle"
        >
          {{ l.n }}
          <title>{{ l.label }}</title>
        </text>
        <polygon v-if="marks.length > 2" :points="shape" class="area" />
        <circle v-for="m in marks" :key="`${m.x}-${m.y}`" :cx="m.x" :cy="m.y" r="4" class="mark">
          <title>{{ m.label }}: {{ m.score.toFixed(1) }}</title>
        </circle>
      </svg>
      <figcaption>
        <div v-for="p in pillarAverages" :key="p.label" class="pillar">
          <span>{{ p.label }}</span>
          <strong>{{ p.value === null ? '—' : p.value.toFixed(1) }}</strong>
        </div>
        <dl v-if="extremes.length" class="extremes">
          <div v-for="extreme in extremes" :key="extreme.title">
            <dt>{{ extreme.title }}</dt>
            <dd class="criterion">
              <span class="number">{{ extreme.n }}</span>
              <span class="min-w-0 flex-1">{{ extreme.label }}</span>
              <strong>{{ extreme.score!.toFixed(1) }}</strong>
            </dd>
          </div>
        </dl>
        <details v-if="criteria.length" :open="open" class="group criteria">
          <summary
            class="flex min-h-11 cursor-pointer list-none items-center gap-1 text-sm font-medium text-mask [&::-webkit-details-marker]:hidden"
          >
            <ChevronRight
              class="size-4 transition-transform group-open:rotate-90"
              aria-hidden="true"
            />
            {{ t('course.criteria') }}
          </summary>
          <ol class="m-0 list-none p-0 pb-2">
            <li v-for="criterion in criteria" :key="criterion.n" class="criterion">
              <span class="number">{{ criterion.n }}</span>
              <span class="min-w-0 flex-1">{{ criterion.label }}</span>
              <strong>{{ criterion.score === null ? '—' : criterion.score.toFixed(1) }}</strong>
            </li>
          </ol>
        </details>
      </figcaption>
    </div>
  </figure>
</template>

<style scoped>
.radar {
  margin: 0.5rem 0 0;
  container-type: inline-size;
}

.layout {
  display: grid;
  gap: 0.5rem;
}

@container (min-width: 36rem) {
  .layout {
    grid-template-columns: 18rem minmax(0, 1fr);
    align-items: center;
    gap: 2rem;
  }
}

.extremes {
  display: grid;
  gap: 0.5rem;
  margin: 0;
  padding: 0.75rem 0;
  border-top: 1px solid var(--line);
}

.extremes dt {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
}

.extremes dd {
  margin: 0;
  padding: 0.125rem 0 0;
  font-size: 0.875rem;
}

.extremes .number {
  color: var(--copper);
}

@container (min-width: 36rem) {
  .extremes {
    display: none;
  }
}

.criteria {
  border-top: 1px solid var(--line);
}

.criterion {
  display: flex;
  gap: 0.75rem;
  padding: 0.3rem 0;
  font-size: 0.8125rem;
}

.number {
  width: 1.25rem;
  flex-shrink: 0;
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  line-height: 1.25rem;
  color: var(--muted);
}

.criterion strong {
  font-family: var(--font-mono);
  font-weight: 500;
}

.layout > svg {
  width: 100%;
  max-width: 18rem;
  display: block;
  margin: 0 auto;
}

.grid {
  fill: none;
  stroke: var(--line);
  stroke-width: 1;
}

.axis {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  fill: var(--muted);
}

.flagged {
  fill: var(--copper);
}

@container (min-width: 36rem) {
  .axis {
    font-size: 10px;
    font-weight: 400;
    fill: var(--muted);
  }
}

.area {
  fill: var(--mask);
  fill-opacity: 0.18;
  stroke: var(--mask);
  stroke-width: 2;
  stroke-linejoin: round;
}

.mark {
  fill: var(--copper);
  stroke: var(--panel);
  stroke-width: 2;
}

.pillar {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.35rem 0;
  border-top: 1px solid var(--line);
  font-size: 0.875rem;
}

.pillar strong {
  font-family: var(--font-mono);
  font-weight: 500;
}
</style>
