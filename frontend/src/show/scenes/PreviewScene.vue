<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BrandMark from '@/components/BrandMark.vue'

const { locale } = useI18n()

const copy = {
  en: {
    members: '28 members',
    question: 'anyone had Dobrescu for SDA?',
    reply: 'ok taking it 👍',
    ratings: '64 ratings',
    pillars: ['Teaching', 'Labs', 'Grading'],
    note: 'Every shared link',
    noteMark: 'is its own ad.',
    demo: 'Fictional demo data',
  },
  ro: {
    members: '28 de membri',
    question: 'a avut cineva cu Dobrescu la SDA?',
    reply: 'ok, mă înscriu la ea 👍',
    ratings: '64 de evaluări',
    pillars: ['Predare', 'Laboratoare', 'Notare'],
    note: 'Fiecare link trimis',
    noteMark: 'e propria reclamă.',
    demo: 'Date fictive, pentru demo',
  },
}

const scores = [8.9, 8.2, 8.5]
const text = computed(() => copy[locale.value === 'ro' ? 'ro' : 'en'])
</script>

<template>
  <div class="show-paper preview">
    <section class="chat fade" style="--at: 0.1s">
      <header class="chat-head">
        <span class="avatar group-avatar display">22</span>
        <span>
          <strong class="display">22C31A 🎓</strong>
          <span class="members">{{ text.members }}</span>
        </span>
      </header>

      <div class="thread">
        <div class="message rise" style="--at: 0.6s">
          <span class="avatar display">A</span>
          <div class="bubble">
            <span class="sender display">Andrei</span>
            <p>{{ text.question }}</p>
          </div>
        </div>

        <div class="message mine rise" style="--at: 2s">
          <div class="bubble">
            <span class="sender display">Ioana</span>
            <p class="link mono">acieeadvisor.com/professors/elena-dobrescu</p>
          </div>
        </div>

        <article class="link-card" style="--at: 2.8s">
          <p class="site">
            <BrandMark class="site-mark" />
            acieeadvisor.com
          </p>
          <h2 class="display">Elena Dobrescu · Conf. dr. ing.</h2>
          <div class="summary">
            <p class="overall">
              <strong class="mono">8.6</strong>
              <span class="mono">/ 10</span>
              <span class="ratings">{{ text.ratings }}</span>
            </p>
            <ul class="pillars">
              <li v-for="(pillar, index) in text.pillars" :key="pillar">
                <span>{{ pillar }}</span>
                <span class="track">
                  <span
                    class="fill grow"
                    :style="{ width: `${scores[index]! * 10}%`, '--at': `${3.6 + index * 0.25}s` }"
                  />
                </span>
                <span class="mono">{{ scores[index]!.toFixed(1) }}</span>
              </li>
            </ul>
          </div>
        </article>

        <div class="message rise" style="--at: 5.6s">
          <span class="avatar display">A</span>
          <div class="bubble">
            <p>{{ text.reply }}</p>
          </div>
        </div>
      </div>
    </section>

    <aside class="note">
      <svg class="arrow" viewBox="0 0 120 70" aria-hidden="true">
        <path class="draw" style="--at: 4.3s" pathLength="1" d="M114 12C84 2 44 10 12 50" />
        <path class="draw" style="--at: 4.9s" pathLength="1" d="M34 52 10 53 14 28" />
      </svg>
      <p class="display rise" style="--at: 4.3s">
        {{ text.note }}<br />
        <span class="hl swipe" style="--at: 5s">{{ text.noteMark }}</span>
      </p>
      <span class="demo mono fade" style="--at: 4.6s">{{ text.demo }}</span>
    </aside>
  </div>
</template>

<style scoped>
.preview {
  display: grid;
  grid-template-columns: 50em 27em;
  gap: 3.5em;
  align-items: center;
  justify-content: center;
}

.chat {
  border: 0.15em solid var(--ink);
  border-radius: 1.6em 1.2em 1.8em 1.3em / 1.3em 1.7em 1.2em 1.6em;
  background: var(--panel);
  box-shadow: 0.4em 0.5em 0 color-mix(in srgb, var(--ink) 22%, transparent);
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 1em;
  padding: 0.9em 1.6em;
  border-bottom: 0.12em dashed var(--line);
  background: color-mix(in srgb, var(--board) 70%, var(--panel));
}

.chat-head strong {
  display: block;
  font-size: 2.2em;
}

.members {
  font-size: 1.25em;
  color: var(--muted);
}

.avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 2em;
  height: 2em;
  border: 0.08em solid var(--ink);
  border-radius: 47% 53% 44% 56% / 55% 46% 54% 45%;
  background: color-mix(in srgb, var(--kind-l) 22%, var(--panel));
  font-size: 1.6em;
}

.group-avatar {
  background: var(--mark);
  font-size: 1.9em;
}

.thread {
  display: flex;
  flex-direction: column;
  gap: 0.9em;
  padding: 1.4em 1.6em 1.6em;
}

.message {
  display: flex;
  align-items: flex-end;
  gap: 0.8em;
}

.message.mine {
  justify-content: flex-end;
}

.bubble {
  padding: 0.6em 1.2em 0.7em;
  border: 0.12em solid var(--line);
  border-radius: 1.3em 1.5em 1.4em 0.3em;
  background: var(--board);
}

.mine .bubble {
  border-color: color-mix(in srgb, var(--mask) 50%, var(--line));
  border-radius: 1.5em 1.3em 0.3em 1.4em;
  background: color-mix(in srgb, var(--mask) 10%, var(--panel));
}

.bubble p {
  font-size: 1.65em;
  line-height: 1.3;
}

.bubble .link {
  font-size: 1.3em;
  color: var(--mask);
  text-decoration: underline;
  text-decoration-color: color-mix(in srgb, var(--copper) 55%, transparent);
  text-underline-offset: 0.25em;
}

.sender {
  display: block;
  font-size: 1.3em;
  color: var(--copper);
}

.link-card {
  align-self: flex-end;
  width: 40em;
  padding: 1.1em 1.5em 1.3em;
  border: 0.15em solid var(--ink);
  border-left: 0.5em solid var(--copper);
  border-radius: 0.5em 1.4em 1.2em 0.5em / 0.5em 1.2em 1.5em 0.5em;
  background: var(--panel);
  animation: unfold 800ms cubic-bezier(0.2, 0.8, 0.2, 1) var(--at) both;
}

@keyframes unfold {
  from {
    opacity: 0;
    clip-path: inset(0 0 100% 0);
    translate: 0 -0.8em;
  }
  to {
    clip-path: inset(0 0 0 0);
  }
}

.site {
  display: flex;
  align-items: center;
  gap: 0.5em;
  font-size: 1.25em;
  color: var(--muted);
}

.site-mark {
  width: 1.5em;
  height: 1.5em;
}

.link-card h2 {
  margin-top: 0.15em;
  font-size: 2.5em;
}

.summary {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2.2em;
  align-items: center;
  margin-top: 0.7em;
}

.overall strong {
  font-size: 4em;
  font-weight: 600;
  line-height: 1;
  color: var(--mask);
}

.overall strong + span {
  margin-left: 0.4em;
  font-size: 1.4em;
  color: var(--muted);
}

.ratings {
  display: block;
  margin-top: 0.3em;
  font-size: 1.4em;
}

.pillars {
  display: grid;
  gap: 0.45em;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pillars li {
  display: grid;
  grid-template-columns: 7em 1fr 2.4em;
  gap: 0.6em;
  align-items: center;
  font-size: 1.4em;
}

.track {
  height: 0.7em;
  border: 0.08em solid var(--line);
  border-radius: 0.5em 0.3em 0.5em 0.3em;
  background: var(--board);
  overflow: hidden;
}

.fill {
  display: block;
  height: 100%;
  background: var(--mask);
}

.note {
  position: relative;
  padding-top: 6em;
}

.note p {
  font-size: 3em;
  line-height: 1.3;
}

.arrow {
  position: absolute;
  top: 0;
  left: -3em;
  width: 11em;
  fill: none;
  stroke: var(--copper);
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.demo {
  display: block;
  margin-top: 1.6em;
  font-size: 1.2em;
  color: var(--muted);
}
</style>
