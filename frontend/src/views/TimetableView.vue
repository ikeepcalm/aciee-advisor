<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Drawer from 'primevue/drawer'
import Message from 'primevue/message'
import '@fontsource/chakra-petch/600.css'
import '@fontsource/martian-mono/400.css'
import '@fontsource/martian-mono/500.css'
import { api, describeError, type Lesson, type Timetable } from '@/api'
import LessonCard from '@/components/LessonCard.vue'
import TimetablePicker from '@/components/TimetablePicker.vue'

type Parity = 'ODD' | 'EVEN'

interface Calibration {
  anchor: string
  parity: Parity
}

const ROMAN = ['', 'I', 'II', 'III', 'IV']
const DAYS = [1, 2, 3, 4, 5]
const WINDOW = 3
const ORDER = { ALL: 0, ODD: 1, EVEN: 2 }
const LEVEL = { ODD: 8, EVEN: 36 }
const dayMonth = new Intl.DateTimeFormat('ro-RO', { day: 'numeric', month: 'short' })
const monthShort = new Intl.DateTimeFormat('ro-RO', { month: 'short' })

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const timetable = ref<Timetable | null>(null)
const error = ref('')
const group = ref<string | null>(null)
const pickerOpen = ref(false)
const calibration = ref<Calibration | null>(readJson('timetable.calibration'))
const now = ref(new Date())
const selectedMonday = ref(addDays(mondayOf(now.value), isWeekend(now.value) ? 7 : 0))
const day = ref(isWeekend(now.value) ? 1 : now.value.getDay())
let timer: ReturnType<typeof setInterval> | undefined

function readJson<T>(key: string): T | null {
  try {
    return JSON.parse(localStorage.getItem(key) ?? 'null') as T | null
  } catch {
    return null
  }
}

function writeJson(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // storage blocked: the choice lasts until the tab closes
  }
}

function isWeekend(date: Date) {
  return date.getDay() === 0 || date.getDay() === 6
}

function mondayOf(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() - ((date.getDay() + 6) % 7))
}

function addDays(date: Date, days: number) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
}

function sameDay(a: Date, b: Date) {
  return a.toDateString() === b.toDateString()
}

function minutes(time: string) {
  const [h, m] = time.split(':').map(Number)
  return h! * 60 + m!
}

const thisMonday = computed(() => mondayOf(now.value))
const nowMinutes = computed(() => now.value.getHours() * 60 + now.value.getMinutes())

// shortcut: parity alternates strictly from one tap, so a holiday that pauses the count needs one more tap
function parityOf(monday: Date): Parity | null {
  const c = calibration.value
  if (!c) {
    return null
  }
  const [y, m, d] = c.anchor.split('-').map(Number)
  const weeks = Math.round((monday.getTime() - new Date(y!, m! - 1, d!).getTime()) / (7 * 864e5))
  return weeks % 2 === 0 ? c.parity : c.parity === 'ODD' ? 'EVEN' : 'ODD'
}

function calibrate(parity: Parity) {
  const m = selectedMonday.value
  calibration.value = { anchor: `${m.getFullYear()}-${m.getMonth() + 1}-${m.getDate()}`, parity }
  writeJson('timetable.calibration', calibration.value)
}

function flipParity() {
  calibrate(parityOf(selectedMonday.value) === 'ODD' ? 'EVEN' : 'ODD')
}

function range(monday: Date) {
  const friday = addDays(monday, 4)
  return monday.getMonth() === friday.getMonth()
    ? `${monday.getDate()}–${dayMonth.format(friday)}`
    : `${dayMonth.format(monday)} – ${dayMonth.format(friday)}`
}

const weeks = computed(() =>
  Array.from({ length: WINDOW * 2 + 1 }, (_, i) => {
    const monday = addDays(thisMonday.value, (i - WINDOW) * 7)
    return {
      monday,
      parity: parityOf(monday),
      current: i === WINDOW,
      selected: sameDay(monday, selectedMonday.value),
    }
  }),
)

const selectedIndex = computed(() => weeks.value.findIndex((w) => w.selected))

const wavePath = computed(() =>
  weeks.value
    .map((w, i) => `${i === 0 ? 'M 0' : 'V'} ${w.parity ? LEVEL[w.parity] : 22} H ${(i + 1) * 100}`)
    .join(' '),
)

const selectedParity = computed(() => parityOf(selectedMonday.value))
const programs = computed(() => timetable.value?.programs ?? [])
const program = computed(() =>
  programs.value.find((p) => group.value && p.groups.includes(group.value)),
)

const lessons = computed(() =>
  (program.value?.lessons ?? []).filter(
    (l) =>
      l.groups.includes(group.value!) &&
      (l.weeks === 'ALL' || !selectedParity.value || l.weeks === selectedParity.value),
  ),
)

function dateOf(d: number) {
  return addDays(selectedMonday.value, d - 1)
}

function isToday(d: number) {
  return sameDay(dateOf(d), now.value)
}

function entry(l: Lesson) {
  const start = minutes(l.start)
  const end = minutes(l.end)
  const today = isToday(l.day)
  const left = start - nowMinutes.value
  const running = today && left <= 0 && nowMinutes.value < end
  return {
    lesson: l,
    progress: running ? Math.round((-left / (end - start)) * 100) : null,
    soon: today && left > 0 && left <= 90 ? left : null,
  }
}

type Entry = ReturnType<typeof entry>

const byDay = computed(
  () =>
    Object.fromEntries(
      DAYS.map((d) => [
        d,
        lessons.value
          .filter((l) => l.day === d)
          .sort((a, b) => a.start.localeCompare(b.start) || ORDER[a.weeks] - ORDER[b.weeks])
          .map(entry),
      ]),
    ) as Record<number, Entry[]>,
)

const slots = computed(() => {
  const all = new Map<string, string>()
  for (const l of program.value?.lessons ?? []) {
    all.set(l.start, l.end)
  }
  const used = lessons.value.map((l) => l.start).sort()
  return [...all.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .filter(([start]) => used.length && start >= used[0]! && start <= used.at(-1)!)
    .map(([start, end]) => ({ start, end }))
})

const agenda = computed(() => {
  const items: ({ gap: false; entry: Entry } | { gap: true; from: string; to: string })[] = []
  let previousEnd = ''
  for (const e of byDay.value[day.value]!) {
    if (previousEnd && minutes(e.lesson.start) - minutes(previousEnd) >= 30) {
      items.push({ gap: true, from: previousEnd, to: e.lesson.start })
    }
    items.push({ gap: false, entry: e })
    previousEnd = previousEnd > e.lesson.end ? previousEnd : e.lesson.end
  }
  return items
})

function formatted(iso: string | null) {
  return iso
    ? new Date(iso).toLocaleString('ro-RO', { dateStyle: 'medium', timeStyle: 'short' })
    : '—'
}

function chooseGroup(g: string) {
  group.value = g
  pickerOpen.value = false
}

function selectWeek(monday: Date) {
  selectedMonday.value = monday
  day.value = sameDay(monday, thisMonday.value) && !isWeekend(now.value) ? now.value.getDay() : 1
}

onMounted(async () => {
  timer = setInterval(() => (now.value = new Date()), 30_000)
  try {
    timetable.value = await api<Timetable>('GET', '/api/timetable')
  } catch (e) {
    error.value = describeError(t, e)
    return
  }
  const known = (g: unknown): g is string =>
    typeof g === 'string' && programs.value.some((p) => p.groups.includes(g))
  group.value = [route.query.g, readJson<string>('timetable.group')].find(known) ?? null
})

onUnmounted(() => clearInterval(timer))

watch(group, (g) => {
  if (g) {
    writeJson('timetable.group', g)
    router.replace({ query: { ...route.query, g } })
  }
})
</script>

<template>
  <main class="board tt-theme">
    <Message v-if="error" severity="error">{{ error }}</Message>
    <Message v-else-if="timetable && !programs.length" severity="info">
      {{ t('timetable.loading') }}
    </Message>

    <template v-if="programs.length">
      <section v-if="!program" class="welcome">
        <p class="eyebrow">{{ t('timetable.title') }}</p>
        <h1 class="display">{{ t('timetable.pickTitle') }}</h1>
        <p class="lede">{{ t('timetable.pickLede') }}</p>
        <TimetablePicker :programs="programs" :group="group" @choose="chooseGroup" />
      </section>

      <template v-else>
        <header class="top">
          <button class="ticket" :aria-label="t('timetable.change')" @click="pickerOpen = true">
            <span class="code">{{ group }}</span>
            <span class="meta">
              <span>{{ program.name }}</span>
              <span class="sub">{{ t('timetable.year', { n: ROMAN[program.year] }) }}</span>
            </span>
            <span class="swap">{{ t('timetable.changeShort') }}</span>
          </button>

          <section class="clock" :aria-label="t('timetable.weekPicker')">
            <div class="wave">
              <svg viewBox="0 0 700 44" preserveAspectRatio="none" aria-hidden="true">
                <rect
                  v-if="selectedIndex >= 0"
                  :x="selectedIndex * 100"
                  y="0"
                  width="100"
                  height="44"
                  class="sel"
                />
                <path :d="wavePath" class="trace" :class="{ unknown: !calibration }" />
              </svg>
              <div class="weeks">
                <button
                  v-for="(w, i) in weeks"
                  :key="i"
                  class="week"
                  :class="{ selected: w.selected }"
                  :aria-pressed="w.selected"
                  :aria-label="
                    range(w.monday) + (w.parity ? ', ' + t(`timetable.parity.${w.parity}`) : '')
                  "
                  @click="selectWeek(w.monday)"
                >
                  <span v-if="w.current" class="probe">{{ t('timetable.today') }}</span>
                  <span class="date">{{ w.monday.getDate() }}</span>
                  <span class="month">{{ monthShort.format(w.monday).replace('.', '') }}</span>
                  <span class="bit">{{ w.parity ? t(`timetable.bit.${w.parity}`) : '?' }}</span>
                </button>
              </div>
            </div>
            <div class="readout">
              <span class="display">{{ range(selectedMonday) }}</span>
              <span v-if="selectedParity" class="tag" :class="selectedParity">
                {{ t(`timetable.parity.${selectedParity}`) }}
              </span>
            </div>
            <div v-if="!calibration" class="ask">
              <p>{{ t('timetable.askParity', { range: range(selectedMonday) }) }}</p>
              <div class="answers">
                <button @click="calibrate('ODD')">{{ t('timetable.parity.ODD') }}</button>
                <button @click="calibrate('EVEN')">{{ t('timetable.parity.EVEN') }}</button>
              </div>
              <p class="hint">{{ t('timetable.askHint') }}</p>
            </div>
          </section>
        </header>

        <nav class="days" :aria-label="t('timetable.dayPicker')">
          <button
            v-for="d in DAYS"
            :key="d"
            class="day"
            :class="{ on: day === d, today: isToday(d) }"
            :aria-pressed="day === d"
            :aria-label="`${t(`timetable.days.${d}`)} ${dayMonth.format(dateOf(d))}`"
            @click="day = d"
          >
            <span class="dname">{{ t(`timetable.daysShort.${d}`) }}</span>
            <span class="dnum">{{ dateOf(d).getDate() }}</span>
            <span class="pads" aria-hidden="true">
              <i v-for="n in byDay[d]!.length" :key="n"></i>
            </span>
          </button>
        </nav>

        <ol class="trace-list">
          <li v-if="!agenda.length" class="empty">{{ t('timetable.free') }}</li>
          <template v-for="(item, i) in agenda" :key="i">
            <li v-if="item.gap" class="gap">
              {{ t('timetable.freeGap', { from: item.from, to: item.to }) }}
            </li>
            <li v-else class="stop" :class="`k-${item.entry.lesson.kind ?? 'X'}`">
              <span class="when">
                <span>{{ item.entry.lesson.start }}</span>
                <span class="dim">{{ item.entry.lesson.end }}</span>
              </span>
              <span
                class="pad"
                :class="{ live: item.entry.progress !== null }"
                aria-hidden="true"
              ></span>
              <LessonCard
                :lesson="item.entry.lesson"
                :progress="item.entry.progress"
                :soon="item.entry.soon"
                :show-parity="!selectedParity"
                class="big"
              />
            </li>
          </template>
        </ol>

        <div class="grid" role="table" :aria-label="t('timetable.title')">
          <div role="row" class="row">
            <span role="columnheader"></span>
            <span
              v-for="d in DAYS"
              :key="d"
              role="columnheader"
              class="colhead"
              :class="{ today: isToday(d) }"
            >
              <span class="display">{{ t(`timetable.days.${d}`) }}</span>
              <span class="date">{{ dayMonth.format(dateOf(d)) }}</span>
            </span>
          </div>
          <div v-for="s in slots" :key="s.start" role="row" class="row">
            <span role="rowheader" class="when">
              <span>{{ s.start }}</span>
              <span class="dim">{{ s.end }}</span>
            </span>
            <span
              v-for="d in DAYS"
              :key="d"
              role="cell"
              class="cell"
              :class="{ today: isToday(d) }"
            >
              <LessonCard
                v-for="(e, i) in byDay[d]!.filter((x) => x.lesson.start === s.start)"
                :key="i"
                :lesson="e.lesson"
                :progress="e.progress"
                :show-parity="!selectedParity"
              />
            </span>
          </div>
        </div>

        <footer class="source">
          <a :href="program.pdfUrl" target="_blank" rel="noopener">
            {{ t('timetable.officialPdf') }}
          </a>
          · {{ t('timetable.updatedAt', { at: formatted(program.updatedAt) }) }} ·
          {{ t('timetable.checkedAt', { at: formatted(timetable!.checkedAt) }) }}
          <br />
          {{ t('timetable.parityNote') }}
          <button v-if="calibration" class="link" @click="flipParity">
            {{ t('timetable.flip') }}
          </button>
        </footer>

        <Drawer
          v-model:visible="pickerOpen"
          position="bottom"
          :header="t('timetable.pickTitle')"
          class="tt-theme"
          style="height: auto; max-height: 85vh"
        >
          <TimetablePicker :programs="programs" :group="group" @choose="chooseGroup" />
        </Drawer>
      </template>
    </template>
  </main>
</template>

<style>
.tt-theme {
  --tt-board: #e9f0ec;
  --tt-panel: #ffffff;
  --tt-ink: #10241d;
  --tt-muted: #5b6e66;
  --tt-line: #c9d8d0;
  --tt-mask: #1d5b45;
  --tt-copper: #b5652a;
  --tt-on-mask: #f2f7f4;
}

@media (prefers-color-scheme: dark) {
  .tt-theme {
    --tt-board: #0d211b;
    --tt-panel: #142d25;
    --tt-ink: #e4eee8;
    --tt-muted: #8fa79c;
    --tt-line: #24443a;
    --tt-mask: #2f8a68;
    --tt-copper: #e0904f;
    --tt-on-mask: #0d211b;
  }
}

.tt-theme .k-C {
  --kind: var(--tt-copper);
}

.tt-theme .k-S {
  --kind: #3d86b8;
}

.tt-theme .k-L {
  --kind: #8a6bd1;
}

.tt-theme .k-P {
  --kind: #c9a227;
}

.tt-theme .k-X {
  --kind: #7c8a84;
}
</style>

<style scoped>
main.board {
  max-width: none;
  margin: 0;
  padding: 1rem 1rem 2.5rem;
  color: var(--tt-ink);
  background-color: var(--tt-board);
  background-image: radial-gradient(var(--tt-line) 1px, transparent 1px);
  background-size: 18px 18px;
  min-height: calc(100vh - 3.5rem);
  box-sizing: border-box;
}

.board > * {
  max-width: 72rem;
  margin-left: auto;
  margin-right: auto;
}

.display,
.dname,
.ticket .code {
  font-family: 'Chakra Petch', system-ui, sans-serif;
  font-weight: 600;
}

.when,
.bit,
.date,
.dnum,
.gap,
.eyebrow,
.probe,
.swap {
  font-family: 'Martian Mono', ui-monospace, monospace;
  font-variant-numeric: tabular-nums;
}

.dim {
  color: var(--tt-muted);
}

button {
  font: inherit;
  color: inherit;
  cursor: pointer;
}

button:focus-visible {
  outline: 2px solid var(--tt-copper);
  outline-offset: 2px;
}

.eyebrow {
  margin: 0 0 0.5rem;
  font-size: 0.6875rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--tt-copper);
}

.welcome {
  max-width: 34rem;
  margin: 0;
  padding-top: 1.5rem;
}

.welcome h1 {
  margin: 0 0 0.5rem;
  font-size: clamp(2rem, 7vw, 3rem);
  line-height: 1.05;
}

.lede {
  margin: 0 0 1.75rem;
  color: var(--tt-muted);
}

.top {
  display: grid;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.ticket {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 0.9rem;
  padding: 0.75rem 0.95rem;
  text-align: left;
  border: 0;
  border-radius: 0.8rem;
  background: var(--tt-mask);
  color: var(--tt-on-mask);
}

.ticket .code {
  padding-right: 0.9rem;
  border-right: 1px dashed currentColor;
  font-size: 1.75rem;
  line-height: 1;
}

.ticket .meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  font-size: 0.875rem;
  font-weight: 600;
}

.ticket .sub {
  opacity: 0.75;
  font-weight: 400;
}

.swap {
  font-size: 0.625rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  opacity: 0.85;
}

.clock {
  padding: 1.6rem 0.75rem 0.9rem;
  border: 1px solid var(--tt-line);
  border-radius: 0.9rem;
  background: var(--tt-panel);
}

.wave {
  position: relative;
}

.wave svg {
  display: block;
  width: 100%;
  height: 2.75rem;
}

.trace {
  fill: none;
  stroke: var(--tt-copper);
  stroke-width: 2.5;
  vector-effect: non-scaling-stroke;
}

.trace.unknown {
  stroke: var(--tt-muted);
  stroke-dasharray: 6 5;
}

.sel {
  fill: color-mix(in srgb, var(--tt-mask) 18%, transparent);
}

.weeks {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.week {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.15rem;
  padding: 0.4rem 0 0.2rem;
  border: 0;
  border-top: 2px solid transparent;
  background: none;
  font-size: 0.625rem;
}

.week .date {
  font-size: 0.8125rem;
}

.week .month {
  margin-top: -0.2rem;
  color: var(--tt-muted);
  font-size: 0.5625rem;
}

.week .bit {
  color: var(--tt-muted);
  font-size: 0.5625rem;
  letter-spacing: 0.06em;
}

.week.selected {
  border-top-color: var(--tt-mask);
  font-weight: 600;
}

.week.selected .bit {
  color: var(--tt-ink);
}

.probe {
  position: absolute;
  top: -3.95rem;
  left: 50%;
  transform: translateX(-50%);
  padding: 0.05rem 0.35rem;
  border-radius: 0.25rem;
  background: var(--tt-copper);
  color: var(--tt-panel);
  font-size: 0.5625rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.readout {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.6rem;
}

.readout .display {
  font-size: 1.375rem;
}

.tag {
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.6875rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  background: var(--tt-copper);
  color: var(--tt-panel);
}

.tag.EVEN {
  background: var(--tt-mask);
  color: var(--tt-on-mask);
}

.ask {
  margin-top: 0.8rem;
  padding-top: 0.8rem;
  border-top: 1px dashed var(--tt-line);
}

.ask > p:first-child {
  margin: 0 0 0.6rem;
  font-weight: 600;
}

.answers {
  display: flex;
  gap: 0.5rem;
}

.answers button {
  padding: 0.6rem 1.1rem;
  border: 1px solid var(--tt-line);
  border-radius: 999px;
  background: var(--tt-panel);
  font-weight: 600;
}

.answers button:hover {
  border-color: var(--tt-mask);
  background: var(--tt-mask);
  color: var(--tt-on-mask);
}

.hint {
  margin: 0.6rem 0 0;
  font-size: 0.8125rem;
  color: var(--tt-muted);
}

.days {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.4rem;
  margin-bottom: 1.1rem;
}

.day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  padding: 0.5rem 0;
  border: 1px solid var(--tt-line);
  border-radius: 0.7rem;
  background: var(--tt-panel);
}

.dname {
  font-size: 0.8125rem;
  text-transform: uppercase;
}

.dnum {
  font-size: 1.125rem;
}

.pads {
  display: flex;
  gap: 2px;
  height: 5px;
}

.pads i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--tt-copper);
}

.day.today {
  border-color: var(--tt-copper);
  box-shadow: inset 0 -3px 0 var(--tt-copper);
}

.day.on {
  border-color: var(--tt-mask);
  background: var(--tt-mask);
  color: var(--tt-on-mask);
}

.day.on .pads i {
  background: var(--tt-on-mask);
}

.trace-list {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.trace-list::before {
  content: '';
  position: absolute;
  top: 0.9rem;
  bottom: 0.9rem;
  left: 3.62rem;
  width: 2px;
  background: var(--tt-copper);
  opacity: 0.5;
}

.stop {
  display: grid;
  grid-template-columns: 3rem 1.25rem minmax(0, 1fr);
  align-items: start;
  gap: 0 0.35rem;
}

.when {
  display: flex;
  flex-direction: column;
  padding-top: 0.55rem;
  font-size: 0.75rem;
  font-weight: 500;
}

.pad {
  position: relative;
  z-index: 1;
  width: 0.9rem;
  height: 0.9rem;
  margin: 0.65rem 0 0 0.18rem;
  border: 3px solid var(--kind);
  border-radius: 50%;
  background: var(--tt-board);
  box-sizing: border-box;
}

.pad.live {
  background: var(--kind);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--kind) 35%, transparent);
  animation: pulse 1.8s ease-in-out infinite;
}

@keyframes pulse {
  50% {
    box-shadow: 0 0 0 8px color-mix(in srgb, var(--kind) 0%, transparent);
  }
}

.stop .big {
  font-size: 0.875rem;
}

.stop .big :deep(strong) {
  font-size: 0.9375rem;
}

.gap {
  padding: 0.1rem 0 0.1rem 4.6rem;
  font-size: 0.625rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--tt-muted);
}

.empty {
  padding: 2.5rem 1rem;
  text-align: center;
  color: var(--tt-muted);
  border: 1px dashed var(--tt-line);
  border-radius: 0.9rem;
  background: var(--tt-panel);
}

.grid {
  display: none;
}

.source {
  margin-top: 1.75rem;
  font-size: 0.8125rem;
  line-height: 1.6;
  color: var(--tt-muted);
}

.source a,
.link {
  color: var(--tt-mask);
}

.link {
  padding: 0;
  border: 0;
  background: none;
  text-decoration: underline;
}

@media (min-width: 52rem) {
  main.board {
    padding: 1.5rem 1.5rem 3rem;
  }

  .top {
    grid-template-columns: minmax(16rem, 22rem) minmax(0, 1fr);
  }

  .ticket {
    grid-template-columns: 1fr auto;
    grid-template-rows: auto 1fr;
    align-content: space-between;
    padding: 1.1rem 1.2rem;
  }

  .ticket .code {
    grid-column: 1 / -1;
    padding: 0 0 0.7rem;
    border-right: 0;
    border-bottom: 1px dashed currentColor;
    font-size: 2.75rem;
  }

  .ticket .swap {
    align-self: end;
  }

  .days,
  .trace-list {
    display: none;
  }

  .grid {
    display: grid;
    gap: 0.4rem;
  }

  .row {
    display: grid;
    grid-template-columns: 3.5rem repeat(5, minmax(0, 1fr));
    gap: 0.4rem;
  }

  .colhead {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    padding: 0.45rem 0.6rem;
    border-bottom: 2px solid var(--tt-line);
  }

  .colhead.today {
    border-bottom-color: var(--tt-copper);
  }

  .colhead .display {
    font-size: 1.0625rem;
  }

  .colhead .date {
    font-size: 0.6875rem;
    color: var(--tt-muted);
  }

  .cell {
    display: flex;
    flex-direction: column;
    gap: 0.3rem;
    min-height: 4.25rem;
    padding: 0.2rem;
    border-radius: 0.6rem;
  }

  .cell.today {
    background: color-mix(in srgb, var(--tt-copper) 8%, transparent);
  }

  .cell :deep(.live) {
    border-color: var(--kind);
    box-shadow: 0 0 0 1px var(--kind);
  }
}

@media (prefers-reduced-motion: reduce) {
  .pad.live {
    animation: none;
  }
}
</style>
