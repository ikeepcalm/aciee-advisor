<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeftRight, ChevronDown } from 'lucide-vue-next'
import { api, describeError, type Lesson, type Timetable } from '@/api'
import LessonCard from '@/components/LessonCard.vue'
import AppNotice from '@/components/AppNotice.vue'
import AppSheet from '@/components/AppSheet.vue'
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
const { t, locale } = useI18n()
const dayMonth = computed(
  () => new Intl.DateTimeFormat(locale.value, { day: 'numeric', month: 'short' }),
)
const monthShort = computed(() => new Intl.DateTimeFormat(locale.value, { month: 'short' }))
const route = useRoute()
const router = useRouter()
const timetable = ref<Timetable | null>(null)
const error = ref('')
const group = ref<string | null>(null)
const pickerOpen = ref(false)
const calibration = ref<Calibration | null>(readJson('timetable.calibration'))
// The week signal stays folded unless the parity is still unknown and needs an answer.
const weekOpen = ref(!calibration.value)
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
  weekOpen.value = false
}

// One tap on the parity chip flips the stored calibration, so every week shifts with it.
function tapParity() {
  if (calibration.value) {
    calibrate(parityOf(selectedMonday.value) === 'ODD' ? 'EVEN' : 'ODD')
  } else {
    weekOpen.value = true
  }
}

function range(monday: Date) {
  return dayMonth.value.formatRange(monday, addDays(monday, 4))
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

const span = computed(() => {
  const list = byDay.value[day.value]!
  let from = Math.min(...list.map((e) => minutes(e.lesson.start)))
  const to = Math.max(...list.map((e) => minutes(e.lesson.end)))
  if (isToday(day.value) && nowMinutes.value >= 7 * 60 && nowMinutes.value < to) {
    from = Math.min(from, nowMinutes.value)
  }
  return { from: Math.floor(from / 60) * 60, to: Math.ceil(to / 60) * 60 }
})

function y(time: number) {
  return ((time - span.value.from) / (span.value.to - span.value.from)) * 100
}

function clockLabel(time: number) {
  return `${String(Math.floor(time / 60)).padStart(2, '0')}:${String(time % 60).padStart(2, '0')}`
}

const hours = computed(() =>
  Array.from(
    { length: (span.value.to - span.value.from) / 60 + 1 },
    (_, i) => span.value.from + i * 60,
  ),
)

const blocks = computed(() => {
  const list = byDay.value[day.value]!
  return list.map((e) => {
    const same = list.filter((o) => o.lesson.start === e.lesson.start)
    const start = minutes(e.lesson.start)
    return {
      ...e,
      top: y(start),
      height: y(minutes(e.lesson.end)) - y(start),
      col: same.indexOf(e),
      cols: same.length,
    }
  })
})

const gaps = computed(() => {
  const result: { from: string; to: string }[] = []
  let previousEnd = ''
  for (const b of blocks.value) {
    if (previousEnd && minutes(b.lesson.start) - minutes(previousEnd) >= 30) {
      result.push({ from: previousEnd, to: b.lesson.start })
    }
    previousEnd = previousEnd > b.lesson.end ? previousEnd : b.lesson.end
  }
  return result
})

const nowTop = computed(() =>
  isToday(day.value) && nowMinutes.value >= span.value.from && nowMinutes.value <= span.value.to
    ? y(nowMinutes.value)
    : null,
)

function formatted(iso: string | null) {
  return iso
    ? new Date(iso).toLocaleString(locale.value, { dateStyle: 'medium', timeStyle: 'short' })
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
  // Codes are upper case since the parser change; stored and linked values may still be lower case.
  const known = (g: unknown): g is string =>
    typeof g === 'string' && programs.value.some((p) => p.groups.includes(g))
  group.value =
    [route.query.g, readJson<string>('timetable.group')]
      .map((g) => (typeof g === 'string' ? g.toUpperCase() : g))
      .find(known) ?? null
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
  <main class="page-wide tt-page">
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <AppNotice v-else-if="timetable && !programs.length">{{ t('timetable.loading') }}</AppNotice>

    <template v-if="programs.length">
      <section v-if="!program" class="welcome">
        <p class="eyebrow">{{ t('timetable.title') }}</p>
        <h1 class="display">{{ t('timetable.pickTitle') }}</h1>
        <p class="lede">{{ t('timetable.pickLede') }}</p>
        <TimetablePicker :programs="programs" :group="group" @choose="chooseGroup" />
      </section>

      <template v-else>
        <div class="controls">
          <div class="bar">
            <button class="grp" :aria-label="t('timetable.change')" @click="pickerOpen = true">
              <span class="code">{{ group }}</span>
              <span class="prog"
                >{{ program.name }} · {{ t('timetable.year', { n: ROMAN[program.year] }) }}</span
              >
              <ChevronDown class="icon" aria-hidden="true" />
            </button>
            <button
              class="wk"
              :aria-expanded="weekOpen"
              :aria-label="t('timetable.weekPicker')"
              @click="weekOpen = !weekOpen"
            >
              <span class="range">{{ range(selectedMonday) }}</span>
              <ChevronDown class="icon chev" :class="{ open: weekOpen }" aria-hidden="true" />
            </button>
            <button
              class="par"
              :class="selectedParity ?? 'ask'"
              :aria-label="calibration ? t('timetable.flip') : t('timetable.parityUnknown')"
              @click="tapParity"
            >
              <template v-if="selectedParity">
                {{ t(`timetable.parity.${selectedParity}`) }}
                <ArrowLeftRight class="icon" aria-hidden="true" />
              </template>
              <template v-else>
                ?<span class="prog">{{ t('timetable.parityUnknown') }}</span>
              </template>
            </button>
          </div>

          <section v-if="weekOpen" class="clock" :aria-label="t('timetable.weekPicker')">
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
            <div v-if="!calibration" class="ask">
              <p>{{ t('timetable.askParity', { range: range(selectedMonday) }) }}</p>
              <div class="answers">
                <button @click="calibrate('ODD')">{{ t('timetable.parity.ODD') }}</button>
                <button @click="calibrate('EVEN')">{{ t('timetable.parity.EVEN') }}</button>
              </div>
              <p class="hint">{{ t('timetable.askHint') }}</p>
            </div>
            <p v-else class="hint">{{ t('timetable.parityNote') }}</p>
          </section>
        </div>

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

        <p v-if="!blocks.length" class="empty">{{ t('timetable.free') }}</p>
        <div v-else class="axis" :style="{ minHeight: `${((span.to - span.from) / 60) * 2.4}rem` }">
          <span v-for="h in hours" :key="h" class="tick" :style="{ top: `${y(h)}%` }">
            <span>{{ clockLabel(h) }}</span>
          </span>
          <span
            v-for="g in gaps"
            :key="g.from"
            class="gap"
            :style="{
              top: `${y(minutes(g.from))}%`,
              height: `${y(minutes(g.to)) - y(minutes(g.from))}%`,
            }"
          >
            {{ t('timetable.freeGap', { from: g.from, to: g.to }) }}
          </span>
          <div
            v-for="(b, i) in blocks"
            :key="i"
            class="block"
            :class="`k-${b.lesson.kind ?? 'X'}`"
            :style="{
              top: `${b.top}%`,
              height: `${b.height}%`,
              '--col': b.col,
              '--cols': b.cols,
            }"
          >
            <span
              v-if="b.col === 0"
              class="pad"
              :class="{ live: b.progress !== null }"
              aria-hidden="true"
            ></span>
            <LessonCard
              :lesson="b.lesson"
              :progress="b.progress"
              :soon="b.soon"
              :show-parity="!selectedParity"
              :time="`${b.lesson.start}–${b.lesson.end}`"
              class="big"
            />
          </div>
          <div v-if="nowTop !== null" class="now-line" :style="{ top: `${nowTop}%` }">
            <span>{{ clockLabel(nowMinutes) }}</span>
          </div>
        </div>

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
        </footer>

        <AppSheet v-model="pickerOpen" :title="t('timetable.pickTitle')">
          <TimetablePicker :programs="programs" :group="group" @choose="chooseGroup" />
        </AppSheet>
      </template>
    </template>
  </main>
</template>

<style scoped>
.dname,
.grp .code,
.wk .range {
  font-family: var(--font-display);
  font-weight: 600;
}

.when,
.bit,
.date,
.dnum,
.gap,
.eyebrow,
.probe,
.par,
.prog {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.dim {
  color: var(--muted);
}

.eyebrow {
  margin: 0 0 0.5rem;
  font-size: 0.6875rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--copper);
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
  color: var(--muted);
}

/* Sticky controls: group · week · parity, then the day strip; lessons start right under them. */
.controls {
  position: sticky;
  top: 3.5rem;
  z-index: 20;
  margin: 0 -1rem 0.75rem;
  padding: 0.5rem 1rem 0.6rem;
  background: var(--board);
  box-shadow: 0 1px 0 var(--line);
}

.bar {
  display: flex;
  gap: 0.4rem;
}

.grp,
.wk,
.par {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 0.3rem;
  min-height: 2.75rem;
  padding: 0 0.7rem;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--panel);
}

.grp {
  border-color: var(--mask);
  background: var(--mask);
  color: var(--on-mask);
}

.grp .code {
  font-size: 1rem;
  letter-spacing: 0.02em;
}

.prog {
  display: none;
  font-size: 0.625rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  opacity: 0.8;
}

.wk {
  flex: 1 1 auto;
  min-width: 0;
  justify-content: space-between;
  font-size: 0.9375rem;
}

.wk .range {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.icon {
  flex: none;
  width: 0.9rem;
  height: 0.9rem;
}

.chev {
  transition: rotate 150ms ease-out;
}

.chev.open {
  rotate: 180deg;
}

.par {
  padding: 0 0.6rem;
  border-color: var(--copper);
  color: var(--copper);
  font-size: 0.625rem;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.par.EVEN {
  border-color: var(--mask);
  color: var(--mask);
}

.par.ask {
  border-style: dashed;
  border-color: var(--muted);
  color: var(--muted);
}

.clock {
  margin-top: 0.6rem;
  padding: 1.6rem 0.75rem 0.9rem;
  border: 1px solid var(--line);
  border-radius: 0.9rem;
  background: var(--panel);
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
  stroke: var(--copper);
  stroke-width: 2.5;
  vector-effect: non-scaling-stroke;
}

.trace.unknown {
  stroke: var(--muted);
  stroke-dasharray: 6 5;
}

.sel {
  fill: color-mix(in srgb, var(--mask) 18%, transparent);
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
  color: var(--muted);
  font-size: 0.5625rem;
}

.week .bit {
  color: var(--muted);
  font-size: 0.5625rem;
  letter-spacing: 0.06em;
}

.week.selected {
  border-top-color: var(--mask);
  font-weight: 600;
}

.week.selected .bit {
  color: var(--ink);
}

.probe {
  position: absolute;
  top: -3.95rem;
  left: 50%;
  transform: translateX(-50%);
  padding: 0.05rem 0.35rem;
  border-radius: 0.25rem;
  background: var(--copper);
  color: var(--panel);
  font-size: 0.5625rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.tag {
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.6875rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  background: var(--copper);
  color: var(--panel);
}

.tag.EVEN {
  background: var(--mask);
  color: var(--on-mask);
}

.ask {
  margin-top: 0.8rem;
  padding-top: 0.8rem;
  border-top: 1px dashed var(--line);
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
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--panel);
  font-weight: 600;
}

.answers button:hover {
  border-color: var(--mask);
  background: var(--mask);
  color: var(--on-mask);
}

.hint {
  margin: 0.6rem 0 0;
  font-size: 0.8125rem;
  color: var(--muted);
}

.days {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.4rem;
  margin-top: 0.6rem;
  padding-bottom: 0.25rem;
}

.day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  padding: 0.5rem 0;
  border: 1px solid var(--line);
  border-radius: 0.7rem;
  background: var(--panel);
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
  background: var(--copper);
}

.day.today {
  border-color: var(--copper);
  box-shadow: inset 0 -3px 0 var(--copper);
}

.day.on {
  border-color: var(--mask);
  background: var(--mask);
  color: var(--on-mask);
}

.day.on .pads i {
  background: var(--on-mask);
}

.tt-page {
  display: flex;
  flex-direction: column;
}

.axis {
  position: relative;
  flex: 1 1 auto;
  margin: 1.25rem 0 0.75rem;
}

.axis::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 3.4rem;
  width: 2px;
  background: var(--copper);
  opacity: 0.4;
}

.tick {
  position: absolute;
  left: 0;
  right: 0;
  border-top: 1px dashed var(--line);
}

.tick > span {
  position: absolute;
  top: -0.55rem;
  left: 0;
  padding-right: 0.35rem;
  background: var(--board);
  font-family: 'Martian Mono', ui-monospace, monospace;
  font-size: 0.6875rem;
  color: var(--muted);
}

.block {
  --rail: 4.3rem;
  position: absolute;
  z-index: 3;
  left: calc(var(--rail) + (100% - var(--rail)) * var(--col) / var(--cols));
  width: calc((100% - var(--rail)) / var(--cols) - 0.25rem);
  padding: 2px 0;
  box-sizing: border-box;
}

.block .lesson {
  height: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.pad {
  position: absolute;
  top: 0.7rem;
  left: -1.33rem;
  z-index: 1;
  width: 0.9rem;
  height: 0.9rem;
  border: 3px solid var(--kind);
  border-radius: 50%;
  background: var(--board);
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

.block .big {
  font-size: 0.875rem;
}

.block .big :deep(strong) {
  font-size: 0.9375rem;
}

.gap {
  position: absolute;
  left: 4.3rem;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Martian Mono', ui-monospace, monospace;
  font-size: 0.625rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--muted);
}

.now-line {
  position: absolute;
  left: 3rem;
  right: 0;
  z-index: 2;
  height: 2px;
  background: var(--copper);
  pointer-events: none;
}

.now-line::before {
  content: '';
  position: absolute;
  top: -4px;
  left: calc(0.4rem - 4px);
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--copper);
}

.now-line > span {
  position: absolute;
  top: -0.6rem;
  left: -3rem;
  padding: 0.1rem 0.3rem;
  border-radius: 0.3rem;
  background: var(--copper);
  color: var(--panel);
  font-family: 'Martian Mono', ui-monospace, monospace;
  font-size: 0.625rem;
  font-weight: 500;
}

.empty {
  padding: 2.5rem 1rem;
  text-align: center;
  color: var(--muted);
  border: 1px dashed var(--line);
  border-radius: 0.9rem;
  background: var(--panel);
}

.grid {
  display: none;
}

.source {
  margin-top: 1.75rem;
  font-size: 0.8125rem;
  line-height: 1.6;
  color: var(--muted);
}

.source a {
  color: var(--mask);
}

@media (min-width: 52rem) {
  .tt-page {
    display: block;
  }

  .controls {
    position: static;
    padding-bottom: 0;
  }

  .prog {
    display: inline;
  }

  .wk {
    max-width: 22rem;
  }

  .par .prog {
    text-transform: none;
    opacity: 1;
  }

  .days,
  .axis,
  .empty {
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
    border-bottom: 2px solid var(--line);
  }

  .colhead.today {
    border-bottom-color: var(--copper);
  }

  .colhead .display {
    font-size: 1.0625rem;
  }

  .colhead .date {
    font-size: 0.6875rem;
    color: var(--muted);
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
    background: color-mix(in srgb, var(--copper) 8%, transparent);
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
