<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarDays, ShieldCheck, Users } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'

const { locale } = useI18n()

const copy = {
  en: {
    takeaways: [
      'A timetable that’s actually yours',
      'Honest ratings, anonymous by design',
      'Built by students, for students',
    ],
    askWho: 'Group leaders:',
    ask: 'share it with your group before the exam session.',
  },
  ro: {
    takeaways: [
      'Orarul care e chiar al tău',
      'Evaluări sincere, anonime prin design',
      'Făcut de studenți, pentru studenți',
    ],
    askWho: 'Șefi de grupă:',
    ask: 'trimiteți-l grupei înainte de sesiune.',
  },
}

const icons = [CalendarDays, ShieldCheck, Users]
const text = computed(() => copy[locale.value === 'ro' ? 'ro' : 'en'])
</script>

<template>
  <div class="show-paper close">
    <p class="brand display fade" style="--at: 0.2s">
      <BrandMark class="mark" />
      ACIEE <span class="text-mask">Advisor</span>
    </p>

    <p class="address display rise" style="--at: 0.5s">
      acieeadvisor.com
      <svg class="scribble" viewBox="0 0 100 10" preserveAspectRatio="none" aria-hidden="true">
        <path
          class="draw"
          style="--at: 1.2s"
          pathLength="1"
          d="M2 6.5C14 3 24 8 38 5.2S62 3 74 5.6 90 7 98 4"
        />
      </svg>
    </p>

    <ul class="takeaways">
      <li
        v-for="(takeaway, index) in text.takeaways"
        :key="index"
        class="rise"
        :style="{ '--at': `${2 + index * 0.5}s` }"
      >
        <component :is="icons[index]" class="icon" aria-hidden="true" />
        <span class="display">{{ takeaway }}</span>
      </li>
    </ul>

    <p class="ask display pop" style="--at: 3.9s">
      <span class="hl swipe" style="--at: 4.5s">{{ text.askWho }}</span>
      {{ text.ask }}
    </p>
  </div>
</template>

<style scoped>
.close {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2em 4em;
  text-align: center;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.35em;
  font-size: 2.6em;
}

.mark {
  width: 1.5em;
  height: 1.5em;
}

.address {
  position: relative;
  margin-top: 0.05em;
  font-size: 9em;
  color: var(--mask);
}

.scribble {
  position: absolute;
  right: 0;
  bottom: -0.02em;
  left: 0;
  width: 100%;
  height: 0.14em;
  overflow: visible;
  fill: none;
  stroke: var(--copper);
  stroke-width: 2.4;
  stroke-linecap: round;
}

.takeaways {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.5em;
  width: 100%;
  max-width: 78em;
  margin: 3.5em 0 0;
  padding: 0;
  list-style: none;
}

.takeaways li {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6em;
  padding: 1.4em 1.2em 1.6em;
  border: 0.15em solid var(--line);
  border-radius: var(--wobble);
  background: var(--panel);
}

.takeaways li:nth-child(even) {
  border-radius: var(--wobble-alt);
}

.takeaways .icon {
  width: 3em;
  height: 3em;
  color: var(--copper);
}

.takeaways .display {
  font-size: 2.3em;
  text-wrap: balance;
}

.ask {
  position: relative;
  max-width: 30em;
  margin-top: 1.5em;
  padding: 0.55em 1.1em 0.6em;
  rotate: -1deg;
  border: 0.05em solid var(--ink);
  border-radius: var(--wobble-alt);
  background: color-mix(in srgb, var(--mark) 24%, var(--panel));
  box-shadow: 0.1em 0.14em 0 color-mix(in srgb, var(--ink) 30%, transparent);
  font-size: 2.7em;
  line-height: 1.25;
  text-wrap: balance;
}

.ask::before {
  content: '';
  position: absolute;
  top: -0.3em;
  left: 50%;
  width: 3em;
  height: 0.6em;
  translate: -50% 0;
  rotate: 2deg;
  background: color-mix(in srgb, var(--mark) 80%, transparent);
}
</style>
