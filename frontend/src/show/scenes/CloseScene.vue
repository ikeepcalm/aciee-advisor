<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { MapPin, ShieldCheck } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'

const { locale } = useI18n()

const copy = {
  en: {
    scan: 'Scan to open',
    next: 'Coming next',
    items: [
      {
        title: 'Room finder',
        body: '“Where is room X?” It finds the building and the room.',
      },
      {
        title: 'AI pre-moderation',
        body: 'Polite comments publish right away. Rude ones go to a human.',
      },
    ],
    ask: 'Know a first-year?',
    askMark: 'Share it.',
    askWhy: 'So next year’s students don’t start alone.',
  },
  ro: {
    scan: 'Scanează ca să-l deschizi',
    next: 'Ce urmează',
    items: [
      {
        title: 'Găsirea sălilor',
        body: '„Unde e sala X?” Găsește clădirea și sala.',
      },
      {
        title: 'Premoderare cu AI',
        body: 'Comentariile politicoase apar imediat. Cele jignitoare ajung la un om.',
      },
    ],
    ask: 'Cunoști un boboc?',
    askMark: 'Trimite-i linkul.',
    askWhy: 'Ca bobocii de la anul să nu înceapă singuri.',
  },
}

const QR_MODULES =
  'M0 0.5h7m7 0h1m1 0h1m1 0h7M0 1.5h1m5 0h1m2 0h3m1 0h1m1 0h2m1 0h1m5 0h1M0 2.5h1m1 0h3m1 0h1m1 0h2m4 0h2m2 0h1m1 0h3m1 0h1M0 3.5h1m1 0h3m1 0h1m1 0h1m1 0h1m1 0h3m3 0h1m1 0h3m1 0h1M0 4.5h1m1 0h3m1 0h1m1 0h2m3 0h1m2 0h1m1 0h1m1 0h3m1 0h1M0 5.5h1m5 0h1m1 0h1m1 0h6m2 0h1m5 0h1M0 6.5h7m1 0h1m1 0h1m1 0h1m1 0h1m1 0h1m1 0h7M8 7.5h3m1 0h1m1 0h1m1 0h1M0 8.5h1m1 0h5m2 0h2m1 0h5m1 0h5M0 9.5h1m3 0h1m2 0h2m2 0h2m1 0h1m1 0h1m2 0h1m3 0h1M0 10.5h1m3 0h3m2 0h1m1 0h1m1 0h1m1 0h1m2 0h2m1 0h1m1 0h2M0 11.5h1m1 0h1m4 0h1m4 0h1m2 0h3m6 0h1M0 12.5h1m1 0h1m3 0h1m4 0h1m1 0h1m1 0h1m1 0h2m1 0h1m1 0h3M0 13.5h2m6 0h1m1 0h1m1 0h2m2 0h1m2 0h1m1 0h1m1 0h1M0 14.5h1m4 0h4m4 0h4m2 0h3m1 0h2M0 15.5h1m1 0h1m1 0h1m2 0h3m2 0h1m2 0h1m1 0h4m3 0h1M0 16.5h1m4 0h2m2 0h1m2 0h9m1 0h1M8 17.5h2m5 0h2m3 0h2M0 18.5h7m4 0h3m2 0h1m1 0h1m1 0h1m1 0h3M0 19.5h1m5 0h1m1 0h4m2 0h1m1 0h1m3 0h2m2 0h1M0 20.5h1m1 0h3m1 0h1m1 0h2m1 0h10m1 0h1M0 21.5h1m1 0h3m1 0h1m1 0h2m2 0h2m3 0h2m1 0h5M0 22.5h1m1 0h3m1 0h1m1 0h1m4 0h2m6 0h2m1 0h1M0 23.5h1m5 0h1m3 0h1m3 0h8m2 0h1M0 24.5h7m1 0h2m1 0h4m2 0h8'

const icons = [MapPin, ShieldCheck]
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

    <div class="row">
      <section class="next">
        <h2 class="display rise" style="--at: 2.6s">{{ text.next }}</h2>
        <ul>
          <li
            v-for="(item, index) in text.items"
            :key="index"
            class="rise"
            :style="{ '--at': `${3 + index * 0.5}s` }"
          >
            <component :is="icons[index]" class="icon" aria-hidden="true" />
            <div>
              <h3 class="display">{{ item.title }}</h3>
              <p>{{ item.body }}</p>
            </div>
          </li>
        </ul>
      </section>

      <figure class="scan pop" style="--at: 1.8s">
        <div class="qr">
          <svg viewBox="0 0 25 25" shape-rendering="crispEdges" aria-hidden="true">
            <path fill="#ffffff" d="M0 0h25v25H0z" />
            <path stroke="#000000" :d="QR_MODULES" />
          </svg>
        </div>
        <figcaption class="display">{{ text.scan }}</figcaption>
      </figure>

      <div class="ask pop" style="--at: 4.2s">
        <p class="display">
          {{ text.ask }}
          <span class="hl swipe" style="--at: 4.8s">{{ text.askMark }}</span>
        </p>
        <p class="why display fade" style="--at: 5.4s">{{ text.askWhy }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.close {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 1.5em 3.5em;
  text-align: center;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.35em;
  font-size: 2.4em;
}

.mark {
  width: 1.5em;
  height: 1.5em;
}

.address {
  position: relative;
  font-size: 8.5em;
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

.row {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 3em;
  width: 100%;
  margin-top: 2.6em;
}

.next {
  text-align: left;
}

.next h2 {
  font-size: 2.2em;
  color: var(--copper);
}

.next ul {
  display: grid;
  gap: 1em;
  margin: 0.8em 0 0;
  padding: 0;
  list-style: none;
}

.next li {
  display: flex;
  align-items: flex-start;
  gap: 0.9em;
  padding: 0.8em 1.2em 1em;
  border: 0.15em solid var(--line);
  border-radius: var(--wobble);
  background: var(--panel);
  box-shadow: 0.2em 0.3em 0 color-mix(in srgb, var(--ink) 16%, transparent);
}

.next li:nth-child(even) {
  border-radius: var(--wobble-alt);
}

.next .icon {
  flex: none;
  width: 2.4em;
  height: 2.4em;
  margin-top: 0.2em;
  color: var(--copper);
}

.next h3 {
  font-size: 2.2em;
}

.next li p {
  margin-top: 0.15em;
  font-size: 1.45em;
  line-height: 1.35;
  color: color-mix(in srgb, var(--ink) 80%, var(--panel));
}

.scan {
  margin: 0;
}

.qr {
  padding: 1.92em;
  border: 0.15em solid var(--ink);
  border-radius: 0.6em;
  background: #ffffff;
  box-shadow: 0.25em 0.35em 0 color-mix(in srgb, var(--ink) 22%, transparent);
}

.qr svg {
  display: block;
  width: 16em;
  height: 16em;
}

.scan figcaption {
  margin-top: 0.45em;
  font-size: 1.9em;
}

.ask {
  position: relative;
  justify-self: center;
  max-width: 11.5em;
  padding: 0.6em 0.8em 0.7em;
  rotate: 2deg;
  border: 0.05em solid var(--ink);
  border-radius: var(--wobble-alt);
  background: color-mix(in srgb, var(--mark) 24%, var(--panel));
  box-shadow: 0.1em 0.14em 0 color-mix(in srgb, var(--ink) 30%, transparent);
  font-size: 2.6em;
}

.ask::before {
  content: '';
  position: absolute;
  top: -0.3em;
  left: 50%;
  width: 3em;
  height: 0.6em;
  translate: -50% 0;
  rotate: -2deg;
  background: color-mix(in srgb, var(--mark) 80%, transparent);
}

.ask p:first-child {
  font-size: 1.5em;
  text-wrap: balance;
}

.ask .why {
  margin-top: 0.5em;
  font-size: 0.75em;
  line-height: 1.25;
  text-wrap: balance;
  color: color-mix(in srgb, var(--ink) 80%, var(--panel));
}
</style>
