<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarClock, Database, Server, Share2, Smartphone } from 'lucide-vue-next'

const { locale } = useI18n()

const copy = {
  en: {
    title: 'Under the hood',
    hosting: 'one server · Docker behind nginx · deployed with Coolify',
    web: {
      title: 'Web app',
      lines: ['Vue 3 single-page app', 'Mobile-first', 'English & Romanian'],
    },
    server: {
      title: 'Server',
      lines: ['Spring Boot on Java 25', 'One service, split by domain'],
      domains: ['timetable', 'courses & professors', 'ratings', 'groups', 'moderation'],
    },
    timetable: {
      title: 'Timetable',
      lines: [
        'Faculty PDFs, read every hour',
        'Become each group’s week',
        'Moved classes recorded',
      ],
    },
    sharing: {
      title: 'Sharing',
      lines: ['Every link gets a preview card', 'WhatsApp · Telegram'],
    },
    database: 'Database',
    databaseNote: 'PostgreSQL · two halves that cannot read each other',
    who: {
      title: 'Who you are',
      lines: ['Accounts', 'Groups', 'Which tokens went to whom'],
      foot: 'schemas core + ident · own database role',
    },
    said: {
      title: 'What was said',
      lines: [
        'Ratings and comments',
        'Kept only under an anonymous token',
        'No column points back to a student',
      ],
      foot: 'schema anon · own database role',
    },
    bridge: 'The only bridge',
    token: 'Blind-signed token',
    tokenNote: 'signed without being seen',
    tagline: 'A real student in this course? Proven.',
    taglineMark: 'Which student? Unknowable.',
    built: 'How it was built',
    steps: ['I write the plan and choose the stack', 'AI writes the code', 'I review and test it'],
  },
  ro: {
    title: 'Sub capotă',
    hosting: 'un singur server · Docker în spatele nginx · deploy cu Coolify',
    web: {
      title: 'Aplicație web',
      lines: ['Vue 3, o singură pagină', 'Întâi pentru telefon', 'Română și engleză'],
    },
    server: {
      title: 'Server',
      lines: ['Spring Boot pe Java 25', 'Un serviciu, împărțit pe domenii'],
      domains: ['orar', 'cursuri și profesori', 'evaluări', 'grupe', 'moderare'],
    },
    timetable: {
      title: 'Orar',
      lines: [
        'PDF-urile facultății, citite din oră în oră',
        'Devin orarul fiecărei grupe',
        'Orele mutate sunt notate',
      ],
    },
    sharing: {
      title: 'Distribuire',
      lines: ['Orice link trimis apare ca un card', 'WhatsApp · Telegram'],
    },
    database: 'Baza de date',
    databaseNote: 'PostgreSQL · două jumătăți care nu se pot citi una pe alta',
    who: {
      title: 'Cine ești',
      lines: ['Conturi', 'Grupe', 'Ce jetoane a primit fiecare'],
      foot: 'schemele core + ident · rol propriu',
    },
    said: {
      title: 'Ce s-a spus',
      lines: [
        'Evaluări și comentarii',
        'Păstrate doar sub un jeton anonim',
        'Nicio coloană nu duce la student',
      ],
      foot: 'schema anon · rol propriu',
    },
    bridge: 'Singura punte',
    token: 'Jeton semnat orbește',
    tokenNote: 'semnat fără să fie văzut',
    tagline: 'Student real, înscris la curs? Dovedit.',
    taglineMark: 'Care student? Imposibil de aflat.',
    built: 'Cum a fost construit',
    steps: ['Eu fac planul și aleg tehnologiile', 'AI-ul scrie codul', 'Eu verific și testez'],
  },
}

const ARC = 'M14 46C50 0 130 0 166 46'
const TOKEN_HASH = 'M-6-3.5h12M-6 3.5h12M-3.5-6v12M3.5-6v12'

const text = computed(() => copy[locale.value === 'ro' ? 'ro' : 'en'])
</script>

<template>
  <div class="show-paper architecture">
    <header class="rise" style="--at: 0s">
      <h1 class="display">{{ text.title }}</h1>
      <span class="hosting mono fade" style="--at: 3s">{{ text.hosting }}</span>
    </header>

    <div class="parts">
      <section class="card rise" style="--at: 0.4s">
        <h2 class="display"><Smartphone class="icon" aria-hidden="true" />{{ text.web.title }}</h2>
        <ul>
          <li v-for="line in text.web.lines" :key="line">{{ line }}</li>
        </ul>
        <svg class="next fade" style="--at: 0.9s" viewBox="0 0 48 36" aria-hidden="true">
          <path d="M4 20c12-8 24 6 38-2M33 9l10 9-12 7" />
        </svg>
      </section>

      <section class="card alt rise" style="--at: 1.1s">
        <h2 class="display"><Server class="icon" aria-hidden="true" />{{ text.server.title }}</h2>
        <ul>
          <li v-for="line in text.server.lines" :key="line">{{ line }}</li>
        </ul>
        <p class="domains">
          <span
            v-for="(domain, index) in text.server.domains"
            :key="domain"
            class="pop"
            :style="{ '--at': `${1.5 + index * 0.12}s` }"
          >
            {{ domain }}
          </span>
        </p>
        <svg class="down fade" style="--at: 3.4s" viewBox="0 0 36 48" aria-hidden="true">
          <path d="M17 4c-7 12 7 24 1 38M8 32l10 11 9-13" />
        </svg>
      </section>

      <section class="card rise" style="--at: 2.2s">
        <svg class="next back fade" style="--at: 2.5s" viewBox="0 0 48 36" aria-hidden="true">
          <path d="M4 20c12-8 24 6 38-2M33 9l10 9-12 7" />
        </svg>
        <h2 class="display">
          <CalendarClock class="icon" aria-hidden="true" />{{ text.timetable.title }}
        </h2>
        <ul>
          <li v-for="line in text.timetable.lines" :key="line">{{ line }}</li>
        </ul>
      </section>

      <section class="card alt rise" style="--at: 2.8s">
        <h2 class="display"><Share2 class="icon" aria-hidden="true" />{{ text.sharing.title }}</h2>
        <ul>
          <li v-for="line in text.sharing.lines" :key="line">{{ line }}</li>
        </ul>
      </section>
    </div>

    <section class="database fade" style="--at: 3.5s">
      <h2 class="display">
        <Database class="icon" aria-hidden="true" />{{ text.database }}
        <span class="stack mono">{{ text.databaseNote }}</span>
      </h2>

      <div class="halves">
        <div class="half who rise" style="--at: 3.9s">
          <h3 class="display">{{ text.who.title }}</h3>
          <ul>
            <li v-for="line in text.who.lines" :key="line">{{ line }}</li>
          </ul>
          <span class="foot mono">{{ text.who.foot }}</span>
        </div>

        <div class="between">
          <span class="wall fade" style="--at: 4.4s" />
          <svg class="arc" viewBox="0 0 180 50" aria-hidden="true">
            <path class="draw" style="--at: 5.4s" pathLength="1" :d="ARC" />
            <path class="draw" style="--at: 6s" pathLength="1" d="M151 39l15 7 2-17" />
            <g class="token">
              <circle r="13" class="coin" />
              <path :d="TOKEN_HASH" class="hash" />
            </g>
          </svg>
          <p class="bridge pop" style="--at: 6s">
            <span class="display">{{ text.bridge }}</span>
            <strong class="display">{{ text.token }}</strong>
            {{ text.tokenNote }}
          </p>
        </div>

        <div class="half said rise" style="--at: 4.8s">
          <h3 class="display">{{ text.said.title }}</h3>
          <ul>
            <li v-for="line in text.said.lines" :key="line">{{ line }}</li>
          </ul>
          <span class="foot mono">{{ text.said.foot }}</span>
        </div>
      </div>

      <p class="tagline display rise" style="--at: 6.6s">
        {{ text.tagline }}
        <span class="hl swipe" style="--at: 7.1s">{{ text.taglineMark }}</span>
      </p>
    </section>

    <footer class="built">
      <span class="display rise" style="--at: 7.2s">{{ text.built }}</span>
      <ol>
        <li
          v-for="(step, index) in text.steps"
          :key="step"
          class="pop"
          :style="{ '--at': `${7.4 + index * 0.25}s` }"
        >
          <span class="step display" :class="{ ai: index === 1 }">
            <span class="pad">{{ index + 1 }}</span>
            {{ step }}
          </span>
          <svg v-if="index < 2" class="then" viewBox="0 0 48 36" aria-hidden="true">
            <path d="M4 20c12-8 24 6 38-2M33 9l10 9-12 7" />
          </svg>
        </li>
      </ol>
    </footer>
  </div>
</template>

<style scoped>
.architecture {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1em;
  padding: 1.2em 2.5em;
}

header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 2em;
}

h1 {
  font-size: 3.2em;
}

.hosting {
  font-size: 1.3em;
  color: var(--muted);
}

.parts {
  display: grid;
  grid-template-columns: 17.5em 24em 22em 1fr;
  gap: 2em;
  margin-bottom: 1.3em;
}

.card {
  position: relative;
  padding: 0.6em 1.1em 0.8em;
  border: 0.15em solid var(--line);
  border-radius: var(--wobble);
  background: var(--panel);
  box-shadow: 0.2em 0.3em 0 color-mix(in srgb, var(--ink) 16%, transparent);
}

.card.alt {
  border-radius: var(--wobble-alt);
}

h2 {
  display: flex;
  align-items: center;
  gap: 0.35em;
  font-size: 2em;
}

.icon {
  flex: none;
  width: 1em;
  height: 1em;
  color: var(--copper);
}

.stack {
  margin-left: auto;
  font-size: 0.6em;
  color: var(--muted);
}

ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

li {
  font-size: 1.35em;
  line-height: 1.3;
}

.card li,
.half li {
  position: relative;
  margin-top: 0.25em;
  padding-left: 1em;
}

.card li::before,
.half li::before {
  content: '';
  position: absolute;
  top: 0.45em;
  left: 0.1em;
  width: 0.42em;
  height: 0.42em;
  border-radius: 47% 53% 44% 56% / 55% 46% 54% 45%;
  background: var(--copper);
}

.domains {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35em;
  margin-top: 0.45em;
  font-size: 1.25em;
}

.domains span {
  padding: 0.1em 0.6em 0.15em;
  border: 0.1em solid color-mix(in srgb, var(--copper) 50%, var(--line));
  border-radius: 0.9em 0.7em 0.9em 0.6em;
  background: color-mix(in srgb, var(--copper) 9%, var(--panel));
}

.next,
.down,
.then {
  flex: none;
  fill: none;
  stroke: var(--copper);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.next {
  position: absolute;
  top: 2.6em;
  right: -2.2em;
  z-index: 1;
  width: 2.2em;
}

.next.back {
  right: auto;
  left: -2.2em;
  scale: -1 1;
}

.down {
  position: absolute;
  bottom: -2.7em;
  left: 50%;
  z-index: 1;
  width: 2em;
  translate: -50% 0;
}

.database {
  padding: 0.6em 1.4em 0.7em;
  border: 0.15em solid var(--ink);
  border-radius: 1.6em 1.2em 1.8em 1.3em / 1.3em 1.7em 1.2em 1.6em;
  background: var(--panel);
  box-shadow: 0.4em 0.5em 0 color-mix(in srgb, var(--ink) 22%, transparent);
}

.halves {
  display: grid;
  grid-template-columns: 1fr 18em 1fr;
  gap: 1.2em;
  margin-top: 0.4em;
}

.half {
  padding: 0.5em 1.3em 0.7em;
  border: 0.15em solid color-mix(in srgb, var(--tone) 55%, var(--line));
  border-radius: var(--wobble);
  background: color-mix(in srgb, var(--tone) 10%, var(--panel));
  --tone: var(--copper);
}

.half.said {
  border-radius: var(--wobble-alt);
  --tone: var(--mask);
}

h3 {
  font-size: 2.6em;
  color: var(--tone);
}

.half li {
  font-size: 1.5em;
}

.half li::before {
  background: var(--tone);
}

.foot {
  display: block;
  margin-top: 0.6em;
  font-size: 1.05em;
  white-space: nowrap;
  color: var(--muted);
}

.between {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wall {
  position: absolute;
  top: 2.6em;
  bottom: 0;
  left: 50%;
  width: 0.9em;
  translate: -50% 0;
  border: solid var(--ink);
  border-width: 0 0.15em;
  background: repeating-linear-gradient(135deg, var(--ink) 0 0.12em, var(--panel) 0.12em 0.5em);
}

.arc {
  position: relative;
  display: block;
  width: 18em;
  height: 5em;
  overflow: visible;
  fill: none;
  stroke: var(--copper);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.token {
  offset-path: path('M14 46C50 0 130 0 166 46');
  offset-rotate: 0deg;
  offset-distance: 50%;
  animation: cross 1300ms cubic-bezier(0.3, 0.7, 0.2, 1) 5s both;
}

@keyframes cross {
  from {
    opacity: 0;
    offset-distance: 0%;
  }
  25% {
    opacity: 1;
  }
}

.coin {
  fill: var(--mark);
  stroke: var(--ink);
}

.hash {
  stroke: var(--ink);
  stroke-width: 2;
}

.bridge {
  position: relative;
  margin-top: 0.3em;
  padding: 0.3em 0.9em 0.45em;
  rotate: -1.5deg;
  border: 0.1em solid var(--ink);
  border-radius: var(--wobble-alt);
  background: color-mix(in srgb, var(--mark) 26%, var(--panel));
  box-shadow: 0.2em 0.28em 0 color-mix(in srgb, var(--ink) 30%, transparent);
  font-size: 1.3em;
  line-height: 1.25;
  text-align: center;
}

.bridge span {
  display: block;
  font-size: 1.05em;
  color: var(--copper);
}

.bridge strong {
  display: block;
  font-size: 1.4em;
  font-weight: inherit;
}

.tagline {
  margin-top: 0.3em;
  font-size: 2.2em;
  text-align: center;
}

.built {
  display: flex;
  align-items: center;
  gap: 1.4em;
  margin-top: 0.3em;
  padding-top: 0.8em;
  border-top: 0.12em dashed var(--line);
}

.built > span {
  flex: none;
  font-size: 1.7em;
  color: var(--copper);
}

ol {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  margin: 0;
  padding: 0;
  list-style: none;
}

ol li {
  display: flex;
  align-items: center;
  gap: 0.5em;
  font-size: 1em;
}

.step {
  display: flex;
  align-items: center;
  gap: 0.45em;
  padding: 0.25em 0.8em 0.3em 0.5em;
  border: 0.08em solid var(--ink);
  border-radius: var(--wobble);
  background: color-mix(in srgb, var(--mark) 24%, var(--panel));
  font-size: 1.5em;
  white-space: nowrap;
}

.step.ai {
  border-color: var(--line);
  border-radius: var(--wobble-alt);
  background: var(--panel);
}

.pad {
  display: grid;
  flex: none;
  place-items: center;
  width: 1.4em;
  height: 1.4em;
  border: 0.1em solid currentColor;
  border-radius: 47% 53% 44% 56% / 55% 46% 54% 45%;
  font-size: 0.85em;
}

.then {
  width: 2.4em;
}
</style>
