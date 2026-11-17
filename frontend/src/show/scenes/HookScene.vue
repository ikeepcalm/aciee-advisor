<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BrandMark from '@/components/BrandMark.vue'

const { locale } = useI18n()

const copy = {
  en: {
    seen: 'seen by 31 · no reply',
    bubbles: [
      { chat: '22C31A', text: 'Which elective should I pick?' },
      { chat: 'Year II', text: 'How hard is the Data Structures exam?' },
      { text: 'Easy, everyone passes.' },
      { text: 'Half our group failed it.' },
      { chat: 'Dorm chat', text: 'Is the lab worth going to?' },
      { chat: '22C32B', text: 'Does she take attendance?' },
      { text: 'Depends who you ask…' },
    ],
    line: 'What each course really asks of you,',
    lineMark: 'told by students who passed it.',
  },
  ro: {
    seen: 'văzut de 31 · niciun răspuns',
    bubbles: [
      { chat: '22C31A', text: 'Ce opțional să aleg?' },
      { chat: 'Anul II', text: 'Cât de greu e examenul la Structuri de date?' },
      { text: 'Ușor, trece toată lumea.' },
      { text: 'La noi a picat jumătate de grupă.' },
      { chat: 'Grupul de cămin', text: 'Merită să merg la laborator?' },
      { chat: '22C32B', text: 'Face prezența?' },
      { text: 'Depinde pe cine întrebi…' },
    ],
    line: 'Ce îți cere de fapt fiecare curs,',
    lineMark: 'spus de studenții care l-au trecut.',
  },
}

const places = [
  { x: 6, y: 9, tilt: -3, at: 0.3, seen: true },
  { x: 55, y: 6, tilt: 2, at: 0.8 },
  { x: 66, y: 29, tilt: -2, at: 1.5 },
  { x: 27, y: 40, tilt: 3, at: 2 },
  { x: 7, y: 58, tilt: 2, at: 2.6, seen: true },
  { x: 60, y: 62, tilt: -3, at: 3.1 },
  { x: 33, y: 80, tilt: 1.5, at: 3.6 },
]

const text = computed(() => copy[locale.value === 'ro' ? 'ro' : 'en'])
const bubbles = computed(() =>
  text.value.bubbles.map((bubble, index) => ({ ...bubble, ...places[index]! })),
)
</script>

<template>
  <div class="show-paper">
    <div
      v-for="bubble in bubbles"
      :key="bubble.at"
      class="bubble"
      :class="{ reply: !bubble.chat }"
      :style="{
        '--x': bubble.x,
        '--y': bubble.y,
        '--at': `${bubble.at}s`,
        rotate: `${bubble.tilt}deg`,
      }"
    >
      <span v-if="bubble.chat" class="chat display">{{ bubble.chat }}</span>
      <p>{{ bubble.text }}</p>
      <span v-if="bubble.seen" class="seen mono">{{ text.seen }}</span>
    </div>

    <div class="brand">
      <BrandMark class="mark pop" style="--at: 5.2s" />
      <p class="name display rise" style="--at: 5.5s">
        ACIEE <span class="text-mask">Advisor</span>
      </p>
      <p class="line display rise" style="--at: 6s">
        {{ text.line }}<br />
        <span class="hl swipe" style="--at: 6.7s">{{ text.lineMark }}</span>
      </p>
    </div>
  </div>
</template>

<style scoped>
.bubble {
  position: absolute;
  top: calc(var(--y) * 1%);
  left: calc(var(--x) * 1%);
  width: max-content;
  max-width: 38em;
  padding: 0.9em 1.5em 1em;
  border: 0.15em solid var(--line);
  border-radius: var(--wobble);
  background: var(--panel);
  box-shadow: 0.2em 0.3em 0 color-mix(in srgb, var(--ink) 16%, transparent);
  animation:
    show-pop 600ms cubic-bezier(0.2, 0.9, 0.3, 1.25) var(--at) both,
    gather 800ms cubic-bezier(0.6, 0, 0.8, 0.4) 4.5s forwards;
}

.bubble.reply {
  border-color: color-mix(in srgb, var(--mask) 55%, var(--line));
  border-radius: var(--wobble-alt);
  background: color-mix(in srgb, var(--mask) 9%, var(--panel));
}

.bubble p {
  font-size: 2.1em;
  font-weight: 500;
  line-height: 1.25;
  text-wrap: balance;
}

.chat {
  display: block;
  font-size: 1.5em;
  color: var(--copper);
}

.seen {
  display: block;
  margin-top: 0.4em;
  font-size: 1.2em;
  color: var(--muted);
}

@keyframes gather {
  to {
    opacity: 0;
    scale: 0.15;
    translate: calc((42 - var(--x)) * 1vw) calc((40 - var(--y)) * 1vh);
  }
}

.brand {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 2em;
  text-align: center;
}

.mark {
  width: 10em;
  height: 10em;
}

.name {
  margin-top: 0.1em;
  font-size: 6.5em;
}

.line {
  margin-top: 0.5em;
  font-size: 3.2em;
  line-height: 1.3;
}
</style>
