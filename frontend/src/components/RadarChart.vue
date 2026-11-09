<script setup lang="ts">
import { computed } from 'vue'
import type { Option } from '@/api'

const props = defineProps<{ scores: (number | null)[]; axes: Option[]; pillars: Option[] }>()

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
const pillarAverages = computed(() =>
  props.pillars.map((pillar) => {
    const values = props.axes
      .map((axis, i) => (axis.group === pillar.code ? props.scores[i] : null))
      .filter((v): v is number => v !== null)
    return {
      label: pillar.label,
      value: values.length ? values.reduce((a, b) => a + b, 0) / values.length : null,
    }
  }),
)
</script>

<template>
  <figure class="radar">
    <svg
      :viewBox="`0 0 ${size} ${size}`"
      role="img"
      :aria-label="
        pillarAverages.map((p) => `${p.label}: ${p.value?.toFixed(1) ?? '—'}`).join('; ')
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
    </figcaption>
  </figure>
</template>

<style scoped>
.radar {
  margin: 0.5rem 0;
}

svg {
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
  font-size: 10px;
  fill: var(--muted);
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
