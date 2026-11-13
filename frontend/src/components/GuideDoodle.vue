<script setup lang="ts">
import { computed } from 'vue'

interface Doodle {
  box: string
  wash?: string
  ink?: string[]
  red?: string[]
  blue?: string[]
  dotted?: string
}

const DOODLES: Record<string, Doodle> = {
  welcome: {
    box: '0 0 120 90',
    wash: 'M56 52 108 10 70 78z',
    ink: ['M12 40 108 10 70 78 56 52z', 'M56 52 108 10', 'M56 52l-3 20 11-12'],
    dotted: 'M2 62c6 18 30 26 42 14s-4-24-14-16 0 22 22 6',
  },
  timetable: {
    box: '0 0 120 100',
    wash: 'M17 26c29-2 58-2 86 0L17 86c-2-20-2-40 0-60z',
    ink: [
      'M14 24c30-3 62-3 92 0 2 21 2 44 0 66-30 3-62 3-92 0-2-22-2-45 0-66z',
      'M38 15v15M82 15v15',
      'M16 87 104 25',
    ],
    red: ['M37 40h12M43 40v17M37 57h12', 'M70 63h20M76 63v16M84 63v16M70 79h20'],
  },
  attendance: {
    box: '0 0 120 100',
    wash: 'M28 68c22-2 44-2 66 0v16c-22 2-44 2-66 0z',
    ink: [
      'M22 17c25-2 51-2 76 0 2 24 2 50 0 74-25 2-51 2-76 0-2-24-2-50 0-74z',
      'M46 10c9-1 19-1 28 0v11c-9 1-19 1-28 0z',
      'M56 38h30M56 57h25M56 76h30',
      'M32 70h11v12H32z',
    ],
    red: ['M31 37l5 6 10-12', 'M31 56l5 6 10-12'],
  },
  credits: {
    box: '0 0 120 100',
    wash: 'M12 52c22-1 43-1 64 0v16c-21 1-42 1-64 0z',
    ink: [
      'M10 50c33-2 67-2 100 0 2 7 2 13 0 20-33 2-67 2-100 0-2-7-2-13 0-20z',
      'M26 66v4M43 66v4M60 66v4M93 66v4',
    ],
    red: ['M76 42v36', 'M62 12 52 28h15M64 11v24', 'M83 12c-8 0-8 22 0 22s8-22 0-22z'],
  },
  exams: {
    box: '0 0 120 100',
    wash: 'M96 2c11 0 18 6 18 14s-7 14-18 14-18-6-18-14S85 2 96 2z',
    ink: [
      'M14 16c17-2 37-2 54 0 2 25 2 50 0 74-17 2-37 2-54 0-2-24-2-49 0-74z',
      'M24 34h34M24 47h34M24 60h22',
      'M98 44 70 84l-4 11 10-7 28-40z',
      'M94 50l6 4',
    ],
    red: ['M84 16h11M89.5 10.5v11', 'M100 13l5-4v15'],
  },
  scholarships: {
    box: '0 0 120 100',
    wash: 'M60 6c9 0 16 7 16 16s-7 16-16 16-16-7-16-16S51 6 60 6z',
    ink: [
      'M60 6c9 0 16 7 16 16s-7 16-16 16-16-7-16-16S51 6 60 6z',
      'M12 46c32-2 64-2 96 0 2 15 2 30 0 46-32 2-64 2-96 0-2-16-2-31 0-46z',
      'M13 48l47 25 47-25',
    ],
    red: ['M56 15v14h10', 'M92 12v10M87 17h10', 'M26 22v8M22 26h8'],
  },
  budget: {
    box: '0 0 120 100',
    wash: 'M8 62c8 12 20 12 28 0z',
    ink: [
      'M60 22v64',
      'M40 88c13-2 27-2 40 0',
      'M22 34 98 26',
      'M22 34 10 62M22 34l12 28',
      'M6 62h32c-6 12-26 12-32 0z',
      'M98 26 86 54M98 26l12 28',
      'M82 54h32c-6 12-26 12-32 0z',
    ],
    red: ['M60 12c3 0 5 2 5 5s-2 5-5 5-5-2-5-5 2-5 5-5z'],
  },
  calendar: {
    box: '0 0 120 100',
    wash: 'M83 56c5 0 9 4 9 9s-4 9-9 9-9-4-9-9 4-9 9-9z',
    ink: [
      'M14 24c30-3 62-3 92 0 2 21 2 44 0 66-30 3-62 3-92 0-2-22-2-45 0-66z',
      'M15 40c30-2 60-2 90 0',
      'M38 15v15M82 15v15',
      'M60 41v50',
    ],
    blue: ['M37 51v28M25 58l24 14M25 72l24-14', 'M33 53l4 4 4-4M33 77l4-4 4 4'],
    red: [
      'M83 56c5 0 9 4 9 9s-4 9-9 9-9-4-9-9 4-9 9-9z',
      'M83 50v-3M83 83v-3M68 65h-3M101 65h-3M72 54l-2-2M96 78l-2-2M94 54l2-2M72 76l-2 2',
    ],
  },
  contact: {
    box: '0 0 120 100',
    wash: 'M92 6c10 0 18 8 18 18s-8 18-18 18-18-8-18-18S82 6 92 6z',
    ink: [
      'M24 92V22c14-3 30-3 44 0v70',
      'M12 92c28-2 56-2 84 0',
      'M34 34h24v11H34z',
      'M60 60v.5',
      'M92 6c10 0 18 8 18 18s-8 18-18 18-18-8-18-18S82 6 92 6z',
    ],
    red: ['M92 24V12', 'M92 24l9-5'],
  },
  fun: {
    box: '0 0 120 100',
    wash: 'M60 13c7 0 13 6 13 13s-6 13-13 13-13-6-13-13 6-13 13-13z',
    ink: [
      'M32 22c4.5 0 8 3.5 8 8s-3.5 8-8 8-8-3.5-8-8 3.5-8 8-8z',
      'M32 38c1 10 1 19 0 29l-9 22M32 67l9 22',
      'M32 47 57 28M32 47 21 60',
      'M88 22c4.5 0 8 3.5 8 8s-3.5 8-8 8-8-3.5-8-8 3.5-8 8-8z',
      'M88 38c-1 10-1 19 0 29l-8 22M88 67l9 22',
      'M88 47 63 28M88 47l11 13',
      'M29 31c2 2 4 2 6 0M85 31c2 2 4 2 6 0',
      'M10 91c33-2 67-2 100 0',
    ],
    red: ['M60 15V6M51 18l-6-7M69 18l6-7', 'M48 21l-6-3M72 21l6-3'],
  },
  arrow: {
    box: '0 0 28 48',
    red: ['M15 3c-6 7 7 12 0 20-4 5-5 11-1 20', 'M5 33c3 5 6 9 9 11 2-4 5-8 10-11'],
  },
  lecture: {
    box: '0 0 48 48',
    ink: ['M8 9c11-2 21-2 32 0 2 7 2 14 0 21-6 1-12 1-18 1l-8 9v-9l-6-1c-2-7-2-14 0-21z'],
    red: ['M15 17h18M15 24h11'],
  },
  lab: {
    box: '0 0 48 48',
    ink: ['M6 9c12-1 24-1 36 0 1 8 1 16 0 23-12 1-24 1-36 0-1-7-1-15 0-23z', 'M24 33v7M15 41h18'],
    red: ['M16 16l-4 5 4 5M23 26h9'],
  },
  seminar: {
    box: '0 0 48 48',
    ink: [
      'M5 8c13-1 25-1 38 0 1 8 1 15 0 22-13 1-25 1-38 0-1-7-1-14 0-22z',
      'M14 31l-4 11M34 31l4 11',
    ],
    red: ['M12 16c3-3 5 3 8 0s5 3 8 0M12 23h13'],
  },
}

const props = defineProps<{ name: string; drawn: boolean }>()
const doodle = computed(() => DOODLES[props.name])
const strokes = computed(() =>
  (['ink', 'blue', 'red'] as const).flatMap((tone) =>
    (doodle.value?.[tone] ?? []).map((path) => ({ path, tone })),
  ),
)
</script>

<template>
  <svg
    v-if="doodle"
    class="doodle"
    :class="{ 'is-drawn': drawn }"
    :viewBox="doodle.box"
    fill="none"
    stroke="currentColor"
    stroke-width="2.4"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-if="doodle.wash" class="wash" :d="doodle.wash" />
    <path
      v-for="(stroke, index) in strokes"
      :key="index"
      class="stroke"
      :class="stroke.tone"
      :d="stroke.path"
      pathLength="1"
      :style="{ '--turn': index }"
    />
    <path v-if="doodle.dotted" class="dotted" :d="doodle.dotted" />
  </svg>
</template>

<style scoped>
.wash {
  fill: color-mix(in srgb, var(--mark) 62%, var(--board));
  stroke: none;
}

.red,
.dotted {
  stroke: var(--copper);
}

.blue {
  stroke: var(--mask);
}

.dotted {
  stroke-dasharray: 1.5 6;
}

@media (prefers-reduced-motion: no-preference) {
  .stroke {
    stroke-dasharray: 1;
    transition: stroke-dashoffset 0.45s ease-out;
    transition-delay: calc(0.15s + var(--turn) * 0.07s);
  }

  .wash,
  .dotted {
    transition: opacity 0.35s ease-out 0.5s;
  }

  .doodle:not(.is-drawn) .stroke {
    stroke-dashoffset: 1;
  }

  .doodle:not(.is-drawn) :is(.wash, .dotted) {
    opacity: 0;
  }
}
</style>
