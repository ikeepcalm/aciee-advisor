<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()

const copy = {
  en: {
    title: 'How a rating stays anonymous',
    method: 'blind signatures · RFC 9474',
    browser: 'in your browser',
    server: 'on the server',
    steps: [
      {
        title: 'Seal a secret token',
        body: 'Your browser makes a random token and seals it in an envelope.',
      },
      {
        title: 'The server stamps it',
        body: 'It checks you’re enrolled in this course, then stamps the envelope without seeing inside.',
      },
      {
        title: 'Open the envelope',
        body: 'The stamp went through onto the token. The server has never seen this token.',
      },
      {
        title: 'Rate with no account',
        body: 'The rating travels with the token. The stamp checks out, and nothing links it to you.',
      },
    ],
    tagline: 'We can prove you’re a student.',
    taglineMark: 'We can’t tell which one.',
  },
  ro: {
    title: 'Cum rămâne anonimă o evaluare',
    method: 'semnături oarbe · RFC 9474',
    browser: 'în browserul tău',
    server: 'pe server',
    steps: [
      {
        title: 'Sigilezi un jeton secret',
        body: 'Browserul tău creează un jeton aleatoriu și îl sigilează într-un plic.',
      },
      {
        title: 'Serverul îl ștampilează',
        body: 'Verifică dacă ești înscris la curs, apoi ștampilează plicul fără să vadă ce e înăuntru.',
      },
      {
        title: 'Deschizi plicul',
        body: 'Ștampila a trecut pe jeton. Serverul nu a văzut niciodată acest jeton.',
      },
      {
        title: 'Evaluezi fără cont',
        body: 'Evaluarea pleacă împreună cu jetonul. Ștampila e validă și nimic nu o leagă de tine.',
      },
    ],
    tagline: 'Putem dovedi că ești student.',
    taglineMark: 'Nu putem ști care.',
  },
}

const ENVELOPE = 'M30 62c46-3 94-3 140 0 2 27 2 55 0 82-46 3-94 3-140 0-2-27-2-55 0-82z'
const FLAP = 'M31 64l69 32 69-32'
const OPEN_FLAP = 'M31 63 100 40l69 23'
const TOKEN_HASH = 'M-9-5h18M-9 5h18M-5-9v18M5-9v18'
const CHECK = 'M-10 1l7 8 14-17'
const STEP_SECONDS = 1.7

const text = computed(() => copy[locale.value === 'ro' ? 'ro' : 'en'])
</script>

<template>
  <div class="show-paper anonymity">
    <header class="rise" style="--at: 0s">
      <h1 class="display">{{ text.title }}</h1>
      <span class="method mono">{{ text.method }}</span>
    </header>

    <ol class="steps">
      <li
        v-for="(step, index) in text.steps"
        :key="index"
        :style="{ '--t': `${0.4 + index * STEP_SECONDS}s` }"
      >
        <svg class="doodle fade" viewBox="0 0 200 160" aria-hidden="true">
          <template v-if="index === 0">
            <g transform="translate(100 30)">
              <g class="token-drop">
                <circle r="22" class="coin" />
                <path :d="TOKEN_HASH" class="ink thin" />
              </g>
            </g>
            <path :d="ENVELOPE" class="ink paper" />
            <path :d="FLAP" class="ink" />
            <circle
              cx="100"
              cy="118"
              r="19"
              class="ghost fade"
              style="--at: calc(var(--t) + 1.3s)"
            />
          </template>

          <template v-else-if="index === 1">
            <path :d="ENVELOPE" class="ink paper" />
            <path :d="FLAP" class="ink" />
            <circle cx="100" cy="118" r="19" class="ghost" />
            <g transform="translate(100 118) rotate(-10)">
              <g class="pop" style="--at: calc(var(--t) + 1s)">
                <circle r="26" class="seal" />
                <path :d="CHECK" class="seal" />
              </g>
            </g>
            <g transform="translate(156 50)">
              <g class="stamp-press">
                <path d="M-9-34c0-9 18-9 18 0 0 8-5 12-4 22H-5c1-10-4-14-4-22z" class="ink paper" />
                <path d="M-24-12c16-2 32-2 48 0v12c-16 2-32 2-48 0z" class="ink wood" />
              </g>
            </g>
          </template>

          <template v-else-if="index === 2">
            <path :d="OPEN_FLAP" class="ink paper" />
            <g transform="translate(100 118)">
              <g class="token-lift">
                <circle r="22" class="coin" />
                <circle r="15" class="seal" />
                <path :d="CHECK" class="seal" transform="scale(0.75)" />
              </g>
            </g>
            <path :d="ENVELOPE" class="ink paper" />
            <path d="M31 64c22 14 44 22 69 22s47-8 69-22" class="ink soft" />
          </template>

          <template v-else>
            <path
              d="M22 30c28-2 56-2 84 0 2 34 2 68 0 102-28 2-56 2-84 0-2-34-2-68 0-102z"
              class="ink paper"
            />
            <path d="M36 62h56M36 84h56M36 106h56" class="ghost" />
            <path d="M36 62h46M36 84h38M36 106h50" class="bar" />
            <g transform="translate(104 34) scale(0.8)">
              <circle r="22" class="coin" />
              <circle r="15" class="seal" />
              <path :d="CHECK" class="seal" transform="scale(0.75)" />
            </g>
            <circle
              cx="166"
              cy="62"
              r="24"
              class="accept draw"
              pathLength="1"
              style="--at: calc(var(--t) + 0.8s)"
            />
            <path
              d="M154 63l9 10 16-21"
              class="accept draw"
              pathLength="1"
              style="--at: calc(var(--t) + 1.3s)"
            />
            <g class="fade" style="--at: calc(var(--t) + 1.7s)">
              <circle cx="158" cy="118" r="10" class="ghost" />
              <path d="M138 152c2-18 38-18 40 0" class="ghost" />
              <text x="180" y="128" class="unknown display">?</text>
            </g>
          </template>
        </svg>

        <div class="rise" style="--at: calc(var(--t) + 0.15s)">
          <span class="who display" :class="{ server: index % 2 }">
            <span class="pad">{{ index + 1 }}</span>
            {{ index % 2 ? text.server : text.browser }}
          </span>
          <h2 class="display">{{ step.title }}</h2>
          <p>{{ step.body }}</p>
        </div>

        <svg v-if="index < 3" class="next fade" viewBox="0 0 48 36" aria-hidden="true">
          <path d="M4 20c12-8 24 6 38-2M33 9l10 9-12 7" />
        </svg>
      </li>
    </ol>

    <p class="tagline display rise" style="--at: 7.6s">
      {{ text.tagline }}
      <span class="hl swipe" style="--at: 8.1s">{{ text.taglineMark }}</span>
    </p>
  </div>
</template>

<style scoped>
.anonymity {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1.6em;
  padding: 2em 4.5em;
}

header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 2em;
}

h1 {
  font-size: 3.6em;
}

.method {
  font-size: 1.3em;
  color: var(--muted);
}

.steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 3.4em;
  margin: 0;
  padding: 0;
  list-style: none;
}

.steps li {
  position: relative;
}

.doodle {
  display: block;
  width: 17em;
  height: 13.6em;
  margin-bottom: 0.4em;
  overflow: visible;
  --at: var(--t);
}

.ink {
  fill: none;
  stroke: var(--ink);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.ink.thin {
  stroke-width: 2.2;
}

.ink.soft {
  stroke: var(--line);
}

.paper {
  fill: var(--panel);
}

.wood {
  fill: color-mix(in srgb, var(--copper) 30%, var(--panel));
}

.coin {
  fill: var(--mark);
  stroke: var(--ink);
  stroke-width: 3;
}

.ghost {
  fill: none;
  stroke: var(--line);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-dasharray: 2 7;
}

.seal {
  fill: none;
  stroke: var(--copper);
  stroke-width: 3.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.bar {
  fill: none;
  stroke: var(--mask);
  stroke-width: 6;
  stroke-linecap: round;
}

.accept {
  fill: none;
  stroke: var(--kind-s);
  stroke-width: 5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.unknown {
  fill: var(--copper);
  font-size: 34px;
}

.token-drop {
  animation: token-drop 900ms cubic-bezier(0.5, 0, 0.3, 1) calc(var(--t) + 0.6s) both;
}

.token-lift {
  animation: token-lift 1000ms cubic-bezier(0.3, 0.7, 0.2, 1) calc(var(--t) + 0.5s) both;
}

.stamp-press {
  animation: stamp-press 1300ms ease-in-out calc(var(--t) + 0.4s) both;
}

@keyframes token-drop {
  to {
    translate: 0 88px;
  }
}

@keyframes token-lift {
  to {
    translate: 0 -90px;
  }
}

@keyframes stamp-press {
  40%,
  55% {
    translate: -56px 62px;
  }
}

.who {
  display: flex;
  align-items: center;
  gap: 0.45em;
  font-size: 1.6em;
  color: var(--mask);
}

.who.server {
  color: var(--copper);
}

.pad {
  display: grid;
  flex: none;
  place-items: center;
  width: 1.5em;
  height: 1.5em;
  border: 0.1em solid currentColor;
  border-radius: 47% 53% 44% 56% / 55% 46% 54% 45%;
}

h2 {
  min-height: 2.2em;
  margin-top: 0.15em;
  font-size: 2.4em;
}

.steps p {
  margin-top: 0.3em;
  font-size: 1.4em;
  line-height: 1.4;
  color: color-mix(in srgb, var(--ink) 80%, var(--panel));
}

.next {
  position: absolute;
  top: 6em;
  right: -3.5em;
  width: 3.6em;
  fill: none;
  stroke: var(--copper);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  --at: calc(var(--t) + 1.5s);
}

.tagline {
  margin-top: 0.2em;
  font-size: 3.6em;
  text-align: center;
}
</style>
