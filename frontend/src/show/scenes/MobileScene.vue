<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Smartphone } from 'lucide-vue-next'
import timetableEn from './mobile/timetable-en.jpg'
import timetableRo from './mobile/timetable-ro.jpg'
import professorEn from './mobile/professor-en.jpg'
import professorRo from './mobile/professor-ro.jpg'
import rateEn from './mobile/rate-en.jpg'
import rateRo from './mobile/rate-ro.jpg'

const { locale } = useI18n()

const copy = {
  en: {
    kicker: 'Mobile-first',
    title: 'Built for the phone',
    titleMark: 'in your pocket',
    sub: 'No laptop needed: check your next class on the way there.',
    phones: [
      { label: 'Professor', shot: professorEn },
      { label: 'Timetable', shot: timetableEn },
      { label: 'Rate a course', shot: rateEn },
    ],
  },
  ro: {
    kicker: 'Întâi pentru telefon',
    title: 'Făcut pentru telefonul',
    titleMark: 'din buzunar',
    sub: 'Nu-ți trebuie laptop: îți vezi ora următoare în drum spre curs.',
    phones: [
      { label: 'Profesor', shot: professorRo },
      { label: 'Orar', shot: timetableRo },
      { label: 'Evaluează un curs', shot: rateRo },
    ],
  },
}

const PHONE_AT = [0.45, 0.1, 0.8]

const text = computed(() => copy[locale.value === 'ro' ? 'ro' : 'en'])
</script>

<template>
  <div class="show-paper mobile">
    <div class="pitch">
      <p class="kicker display fade" style="--at: 1.2s">
        <Smartphone class="icon" aria-hidden="true" />{{ text.kicker }}
      </p>
      <h1 class="display rise" style="--at: 1.3s">
        <span class="lead">{{ text.title }}</span>
        <span class="hl swipe" style="--at: 1.8s">{{ text.titleMark }}</span>
      </h1>
      <p class="sub rise" style="--at: 1.9s">{{ text.sub }}</p>
    </div>

    <div class="phones">
      <figure
        v-for="(phone, index) in text.phones"
        :key="index"
        class="rise"
        :class="{ hero: index === 1 }"
        :style="{ '--at': `${PHONE_AT[index]}s` }"
      >
        <div class="phone">
          <img :src="phone.shot" alt="" decoding="async" />
        </div>
        <figcaption class="display pop" :style="{ '--at': `${PHONE_AT[index]! + 0.6}s` }">
          {{ phone.label }}
        </figcaption>
      </figure>
    </div>
  </div>
</template>

<style scoped>
.mobile {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 2.5em;
  padding: 1.5em 3em 1.5em 3.5em;
}

.kicker {
  display: flex;
  align-items: center;
  gap: 0.3em;
  font-size: 2.4em;
  color: var(--copper);
}

.icon {
  flex: none;
  width: 1em;
  height: 1em;
}

h1 {
  margin-top: 0.15em;
  font-size: 4.5em;
  line-height: 1.1;
}

.lead {
  display: block;
  text-wrap: balance;
}

.hl {
  white-space: nowrap;
}

.sub {
  max-width: 14em;
  margin-top: 1em;
  font-size: 2em;
  line-height: 1.3;
  text-wrap: balance;
  color: color-mix(in srgb, var(--ink) 80%, var(--panel));
}

.phones {
  display: flex;
  align-items: center;
  gap: 1.5em;
}

figure {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 16.4em;
  margin: 1.5em 0 0;
}

figure.hero {
  z-index: 1;
  width: 17.6em;
  margin-top: 0;
}

.phone {
  width: 100%;
  padding: 0.45em;
  rotate: -3deg;
  border-radius: 2.2em;
  background: var(--ink);
  box-shadow: 0.4em 0.5em 0 color-mix(in srgb, var(--ink) 22%, transparent);
}

figure:last-child .phone {
  rotate: 3deg;
}

.hero .phone {
  rotate: none;
  box-shadow: 0.5em 0.65em 0 color-mix(in srgb, var(--ink) 26%, transparent);
}

img {
  display: block;
  width: 100%;
  aspect-ratio: 390 / 844;
  border-radius: 1.75em;
}

figcaption {
  margin-top: 0.75em;
  padding: 0.05em 0.7em 0.1em;
  border: 0.08em solid var(--ink);
  border-radius: var(--wobble);
  background: var(--panel);
  font-size: 1.7em;
  white-space: nowrap;
}

.hero figcaption {
  background: color-mix(in srgb, var(--mark) 24%, var(--panel));
}
</style>
