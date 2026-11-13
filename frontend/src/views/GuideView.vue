<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowRight, ExternalLink, ShieldCheck } from 'lucide-vue-next'
import { reducedMotion } from '@/viewTransition'
import { guideFor } from '@/guide/content'
import GuideDoodle from '@/components/GuideDoodle.vue'

type Direction = 1 | -1

const GREETING_ID = 'welcome'
const ENDING_ID = 'end'
const EMAIL = /^\S+@\S+$/
const HEADER_HEIGHT = 56
const STRIP_CLEARANCE = 56
const WHEEL_IDLE = 200
const WHEEL_TRAVEL = 30
const WHEEL_RECENT = 5
const WHEEL_REPUSH = 3
const WHEEL_REPUSH_MIN = 10
const GLIDE_GUARD = 1000
const LINE_HEIGHT = 33
const KEY_DIRECTIONS: Record<string, Direction> = {
  PageDown: 1,
  ArrowDown: 1,
  PageUp: -1,
  ArrowUp: -1,
}
const FOCUS_OWNERS = 'input, textarea, select, [contenteditable], [role="dialog"]'

const { t, locale } = useI18n()
const route = useRoute()
const guide = computed(() => guideFor(locale.value))
const greeting = computed(() => highlight(guide.value.welcome, guide.value.welcomeMark))
const steps = computed(() =>
  guide.value.sections.map((section, index, sections) => {
    const following = sections[index + 1]
    return {
      ...section,
      headline: highlight(section.short, section.mark),
      points: withLeads(section.points),
      next: following
        ? { id: following.id, title: following.title }
        : { id: ENDING_ID, title: guide.value.finish },
    }
  }),
)
const story = ref<HTMLElement>()
const seen = reactive(new Set<string>())
const activeIndex = ref(-1)
const deckQuery = matchMedia('(min-width: 64rem) and (pointer: fine)')
const deck = ref(deckQuery.matches)
const baseTitle = document.title
let revealObserver: IntersectionObserver | undefined
let activeObserver: IntersectionObserver | undefined
let scenes: HTMLElement[] = []
let gliding = false
let glideGuard = 0
let wheelSizes: number[] = []
let wheelTravel = 0
let wheelPhase: 'pending' | 'rising' | 'fading' = 'pending'
let wheelPeak = 0
let wheelTrough = 0
let lastWheelAt = 0

function highlight(text: string, mark: string) {
  const at = mark ? text.indexOf(mark) : -1
  return at < 0
    ? { before: text, mark: '', after: '' }
    : { before: text.slice(0, at), mark, after: text.slice(at + mark.length) }
}

function withLeads(points: string[]) {
  const paired = points.every((point) => point.includes(': '))
  return points.map((point) => {
    const leadEnd = paired ? point.indexOf(': ') + 1 : 0
    return { lead: point.slice(0, leadEnd), rest: point.slice(leadEnd).trim() }
  })
}

function syncDeck() {
  deck.value = deckQuery.matches
  if (deck.value) {
    addEventListener('wheel', onWheel, { passive: false })
  } else {
    removeEventListener('wheel', onWheel)
  }
}

function onJump(event: MouseEvent) {
  const link = event.target instanceof Element ? event.target.closest('a[href^="#"]') : null
  const id = link?.getAttribute('href')?.slice(1)
  if (id) {
    event.preventDefault()
    goTo(id, true)
  }
}

function endGlide() {
  gliding = false
  clearTimeout(glideGuard)
}

function glideTo(scene: HTMLElement, smooth: boolean) {
  const clearance = HEADER_HEIGHT + (deck.value ? 0 : STRIP_CLEARANCE)
  const limit = document.documentElement.scrollHeight - innerHeight
  const top = Math.min(limit, Math.max(0, scene.getBoundingClientRect().top + scrollY - clearance))
  const calm = reducedMotion.matches
  if (smooth && !calm && Math.abs(top - scrollY) > 1) {
    gliding = true
    clearTimeout(glideGuard)
    glideGuard = window.setTimeout(endGlide, GLIDE_GUARD)
    scrollTo({ top, behavior: 'smooth' })
  } else {
    scrollTo({ top, behavior: 'auto' })
  }
}

function goTo(id: string, smooth: boolean) {
  const scene = document.getElementById(id)
  if (scene) {
    glideTo(scene, smooth)
  }
}

function currentStop() {
  return scenes.filter((scene) => scene.getBoundingClientRect().top <= HEADER_HEIGHT + 2).length - 1
}

function roomInside(direction: Direction) {
  const rect = scenes[Math.max(0, currentStop())]?.getBoundingClientRect()
  if (!rect) {
    return 0
  }
  return direction === 1 ? rect.bottom - innerHeight : HEADER_HEIGHT - rect.top
}

function scrollInside(direction: Direction, distance: number) {
  scrollBy({ top: direction * Math.min(distance, roomInside(direction)), behavior: 'auto' })
}

function moveToStop(direction: Direction) {
  const target = scenes[Math.max(0, currentStop()) + direction]
  if (target) {
    glideTo(target, true)
  }
}

function onWheel(event: WheelEvent) {
  if (
    !deck.value ||
    event.ctrlKey ||
    Math.abs(event.deltaX) > Math.abs(event.deltaY) ||
    (event.target instanceof Element && event.target.closest('[role="dialog"]') !== null)
  ) {
    return
  }
  event.preventDefault()
  const scale = event.deltaMode === 1 ? LINE_HEIGHT : event.deltaMode === 2 ? innerHeight : 1
  const delta = event.deltaY * scale
  const size = Math.abs(delta)
  if (event.timeStamp - lastWheelAt > WHEEL_IDLE) {
    wheelSizes = []
    wheelTravel = 0
    wheelPhase = 'pending'
  }
  lastWheelAt = event.timeStamp
  wheelSizes = [...wheelSizes.slice(1 - WHEEL_RECENT), size]
  const recent = average(wheelSizes)
  if (wheelPhase === 'rising') {
    wheelPeak = Math.max(wheelPeak, recent)
    if (recent < wheelPeak * 0.7) {
      wheelPhase = 'fading'
      wheelTrough = recent
    }
  } else if (wheelPhase === 'fading') {
    wheelTrough = Math.min(wheelTrough, recent)
    if (recent > wheelTrough * WHEEL_REPUSH && recent - wheelTrough > WHEEL_REPUSH_MIN) {
      wheelPhase = 'pending'
      wheelTravel = 0
    }
  }
  if (gliding || !delta || wheelPhase !== 'pending') {
    return
  }
  const direction: Direction = delta > 0 ? 1 : -1
  if (roomInside(direction) > 2) {
    scrollInside(direction, size)
    return
  }
  wheelTravel += delta
  if (Math.abs(wheelTravel) >= WHEEL_TRAVEL) {
    wheelPhase = 'rising'
    wheelPeak = recent
    moveToStop(direction)
  }
}

function average(values: number[]) {
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

function onKeydown(event: KeyboardEvent) {
  const target = event.target
  if (
    !deck.value ||
    event.ctrlKey ||
    event.metaKey ||
    event.altKey ||
    (target instanceof Element &&
      (target.closest(FOCUS_OWNERS) || (event.key === ' ' && target.closest('button'))))
  ) {
    return
  }
  const direction = event.key === ' ' ? (event.shiftKey ? -1 : 1) : (KEY_DIRECTIONS[event.key] ?? 0)
  if (!direction) {
    return
  }
  event.preventDefault()
  if (gliding) {
    return
  }
  if (roomInside(direction) > 2) {
    scrollInside(direction, innerHeight * 0.8)
  } else {
    moveToStop(direction)
  }
}

watchEffect(() => (document.title = `${guide.value.title} · ${t('app.title')}`))

watch(
  () => route.hash,
  async (hash, previous) => {
    await nextTick()
    if (hash) {
      goTo(hash.slice(1), previous !== undefined)
    }
  },
  { immediate: true },
)

watch(activeIndex, (index) => {
  const step = steps.value[index]
  const hash = step ? `#${step.id}` : ''
  if (hash !== location.hash) {
    history.replaceState(history.state, '', `${location.pathname}${location.search}${hash}`)
  }
})

onMounted(() => {
  scenes = [...(story.value?.querySelectorAll<HTMLElement>('.scene') ?? [])]
  const stepScenes = scenes.filter((scene) => scene.classList.contains('step'))
  revealObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          seen.add(entry.target.id)
          revealObserver?.unobserve(entry.target)
        }
      }
    },
    { rootMargin: '0px 0px -12% 0px' },
  )
  activeObserver = new IntersectionObserver(
    () => {
      const middle = innerHeight / 2
      activeIndex.value =
        stepScenes.filter((scene) => scene.getBoundingClientRect().top <= middle).length - 1
    },
    { rootMargin: '-50% 0px -50% 0px' },
  )
  scenes.forEach((scene) => revealObserver?.observe(scene))
  stepScenes.forEach((scene) => activeObserver?.observe(scene))
  deckQuery.addEventListener('change', syncDeck)
  syncDeck()
  addEventListener('keydown', onKeydown)
  addEventListener('scrollend', endGlide)
})

onUnmounted(() => {
  revealObserver?.disconnect()
  activeObserver?.disconnect()
  deckQuery.removeEventListener('change', syncDeck)
  removeEventListener('wheel', onWheel)
  removeEventListener('keydown', onKeydown)
  removeEventListener('scrollend', endGlide)
  clearTimeout(glideGuard)
  document.title = baseTitle
})
</script>

<template>
  <main
    ref="story"
    @click="onJump"
    class="mx-auto w-full max-w-2xl px-4 md:px-6"
    :class="{ 'is-deck': deck }"
  >
    <nav
      class="rail sticky top-14 z-20 -mx-4 border-b border-dashed border-line bg-board px-1 md:-mx-6 md:px-3"
      :aria-label="t('guide.index')"
    >
      <ol class="m-0 flex list-none p-0">
        <li
          v-for="(step, index) in steps"
          :key="step.id"
          class="stop relative flex-1"
          :class="{ 'is-past': index < activeIndex, 'is-current': index === activeIndex }"
        >
          <a
            :href="`#${step.id}`"
            class="flex min-h-11 items-center justify-center no-underline"
            :title="step.title"
            :aria-current="index === activeIndex ? 'step' : undefined"
          >
            <span class="dot" aria-hidden="true"></span>
            <span class="sr-only">{{ step.title }}</span>
          </a>
        </li>
      </ol>
    </nav>

    <header
      :id="GREETING_ID"
      class="scene greeting flex min-h-[calc(100svh-9.75rem-env(safe-area-inset-bottom))] flex-col md:min-h-[calc(100svh-6.25rem)]"
      :class="{ 'is-seen': seen.has(GREETING_ID) }"
    >
      <div class="greeting-inner my-auto grid items-center gap-x-16 py-6">
        <div>
          <span class="eyebrow reveal">{{ guide.title }}</span>
          <h1
            class="reveal display max-w-[14ch] text-[clamp(3rem,15vw,6.5rem)] leading-[1.08]"
            style="--order: 1"
          >
            {{ greeting.before }}<span class="hl">{{ greeting.mark }}</span
            >{{ greeting.after }}
          </h1>
          <p class="reveal mt-5 max-w-[34ch] text-lg text-pretty md:text-xl" style="--order: 2">
            {{ guide.lede }}
          </p>
        </div>
        <div class="greeting-aside order-first">
          <GuideDoodle
            name="welcome"
            :drawn="seen.has(GREETING_ID)"
            class="greeting-doodle ml-auto w-32"
          />
          <ol class="contents-card reveal card m-0 hidden rotate-2 list-none px-5 py-3">
            <li v-for="(step, index) in steps" :key="step.id">
              <a
                :href="`#${step.id}`"
                class="display flex min-h-11 items-center gap-3 text-xl leading-tight text-ink no-underline hover:text-mask"
              >
                <span class="mono text-xs text-copper">{{ index + 1 }}</span>
                {{ step.title }}
              </a>
            </li>
          </ol>
        </div>
      </div>
      <a
        v-if="steps[0]"
        :href="`#${steps[0].id}`"
        class="reveal display mb-4 flex min-h-14 items-center gap-2 self-start pr-4 text-2xl text-copper no-underline"
        style="--order: 3"
      >
        <GuideDoodle name="arrow" :drawn="seen.has(GREETING_ID)" class="h-12 w-7" />
        {{ guide.cue }}
      </a>
    </header>

    <section
      v-for="(step, index) in steps"
      :id="step.id"
      :key="step.id"
      class="scene step scroll-mt-10 border-t border-dashed border-line pt-8 pb-10"
      :class="{ 'is-seen': seen.has(step.id), 'is-flipped': index % 2 }"
    >
      <div class="step-grid flex flex-col">
        <GuideDoodle
          :name="step.id"
          :drawn="seen.has(step.id)"
          class="step-doodle mb-2 w-32"
          :class="index % 2 ? 'self-start' : 'self-end'"
        />
        <div class="step-lead">
          <h2 class="eyebrow reveal">{{ step.title }}</h2>
          <p
            class="rule reveal display text-[clamp(2rem,9vw,3rem)] leading-[1.18] text-balance"
            style="--order: 1"
          >
            {{ step.headline.before }}<span class="hl">{{ step.headline.mark }}</span
            >{{ step.headline.after }}
          </p>
        </div>
        <div class="step-body text-[1.0625rem] leading-relaxed md:text-lg">
          <div class="step-points">
            <ul class="reveal m-0 grid list-none gap-[0.8em] p-0" style="--order: 2">
              <li v-for="point in step.points" :key="point.rest" class="point text-pretty">
                <strong v-if="point.lead">{{ point.lead }}&nbsp;</strong>
                <a v-if="EMAIL.test(point.rest)" :href="`mailto:${point.rest}`" class="break-all">{{
                  point.rest
                }}</a>
                <template v-else>{{ point.rest }}</template>
              </li>
            </ul>
            <ul
              v-if="step.kinds"
              class="reveal m-0 grid list-none gap-[1em] p-0"
              style="--order: 2"
            >
              <li v-for="kind in step.kinds" :key="kind.id" class="flex gap-4">
                <GuideDoodle
                  :name="kind.id"
                  :drawn="seen.has(step.id)"
                  class="w-12 shrink-0 self-start"
                />
                <p class="text-pretty">
                  <strong class="display block text-[1.4em] leading-tight">{{ kind.name }}</strong>
                  {{ kind.text }}
                </p>
              </li>
            </ul>
          </div>
          <div class="step-rest flex flex-col">
            <aside v-if="step.tips" class="tips reveal" style="--order: 3">
              <strong class="display block text-[1.2em] leading-tight text-copper">{{
                guide.tipsLabel
              }}</strong>
              <ul class="m-0 mt-[0.5em] grid list-none gap-[0.6em] p-0">
                <li v-for="tip in step.tips" :key="tip" class="point text-pretty">{{ tip }}</li>
              </ul>
            </aside>
            <RouterLink
              v-if="step.action"
              :to="step.action.to"
              class="reveal btn btn-primary self-start text-left"
              style="--order: 3"
            >
              {{ step.action.label }}
              <ArrowRight class="size-4 shrink-0" aria-hidden="true" />
            </RouterLink>
            <p
              v-if="step.sources.length"
              class="reveal flex flex-wrap items-center gap-x-4 border-t border-dashed border-line pt-2 text-sm text-muted"
              style="--order: 3"
            >
              <span class="mono text-xs uppercase">{{ guide.sources }}</span>
              <a
                v-for="source in step.sources"
                :key="source.label"
                :href="source.url"
                target="_blank"
                rel="noopener"
                class="inline-flex min-h-11 items-center gap-1.5"
              >
                {{ source.label }}
                <ExternalLink class="size-3.5 shrink-0" aria-hidden="true" />
                <span class="sr-only">({{ t('guide.newTab') }})</span>
              </a>
            </p>
            <p
              v-else
              class="reveal mono border-t border-dashed border-line pt-4 text-xs text-muted uppercase"
              style="--order: 3"
            >
              {{ guide.tipsLabel }}
            </p>
            <a
              :href="`#${step.next.id}`"
              class="next card flex min-h-16 items-center gap-4 px-4 py-3 text-ink no-underline"
            >
              <span class="min-w-0 flex-1">
                <span
                  v-if="step.next.id !== ENDING_ID"
                  class="mono block text-xs text-muted uppercase"
                  >{{ guide.next }}</span
                >
                <span class="display block text-2xl leading-tight">{{ step.next.title }}</span>
              </span>
              <GuideDoodle name="arrow" drawn class="h-10 w-6 shrink-0" />
            </a>
          </div>
        </div>
      </div>
    </section>

    <footer
      :id="ENDING_ID"
      class="scene ending scroll-mt-10 border-t border-dashed border-line pt-12 pb-16"
      :class="{ 'is-seen': seen.has(ENDING_ID) }"
    >
      <p class="display text-[clamp(2rem,9vw,3.5rem)] leading-tight text-balance">
        {{ guide.farewell }}
      </p>
      <p class="mt-4 flex max-w-prose gap-2 text-sm text-muted">
        <ShieldCheck class="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        {{ guide.checked }}
      </p>
    </footer>
  </main>
</template>

<style scoped>
.point {
  position: relative;
  padding-left: 1.75rem;
}

.point::before {
  content: '';
  position: absolute;
  top: 0.7em;
  left: 0;
  width: 1rem;
  height: 0.4rem;
  background: var(--copper);
  mask: var(--scribble) center / 100% 100% no-repeat;
}

.step-body {
  margin-top: 1.75em;
}

.step-rest,
.step-points > * + *,
.step-rest > * + * {
  margin-top: 1.75em;
}

.tips {
  position: relative;
  align-self: flex-start;
  max-width: 34rem;
  padding: 1.2em 1.1em 1.1em;
  border-radius: 2px 10px 3px 8px;
  background: color-mix(in srgb, var(--mark) 30%, var(--panel));
  box-shadow: 2px 3px 0 color-mix(in srgb, var(--ink) 18%, transparent);
  rotate: -0.7deg;
}

.tips::before {
  content: '';
  position: absolute;
  top: -0.6rem;
  left: 50%;
  width: 5rem;
  height: 1.25rem;
  background: color-mix(in srgb, var(--line) 40%, transparent);
  translate: -50% 0;
  rotate: -2.5deg;
}

.next {
  border-color: var(--ink);
  box-shadow: 2px 3px 0 color-mix(in srgb, var(--ink) 28%, transparent);
}

.dot {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  width: 0.75rem;
  height: 0.75rem;
  border: 1.5px solid var(--line);
  border-radius: 47% 53% 44% 56% / 55% 46% 54% 45%;
  background: var(--board);
}

.stop:not(:last-child)::before {
  content: '';
  position: absolute;
  top: calc(50% - 0.75px);
  left: 50%;
  width: 100%;
  height: 1.5px;
  background: var(--line);
}

.stop.is-past::before,
.stop.is-past .dot {
  border-color: var(--mask);
  background: var(--mask);
}

.stop.is-current .dot {
  border-color: var(--ink);
  background: var(--mark);
  scale: 1.6;
}

.is-deck {
  max-width: 84rem;
  padding-inline: 6.5rem 3rem;
}

.is-deck .rail {
  position: fixed;
  top: calc(50% + 1.75rem);
  left: max(1.25rem, calc(50vw - 41rem));
  margin: 0;
  padding: 0;
  border: 0;
  background: none;
  translate: 0 -50%;
}

.is-deck .rail ol {
  flex-direction: column;
}

.is-deck .rail a {
  width: 2.75rem;
}

.is-deck .stop:not(:last-child)::before {
  top: 50%;
  left: calc(50% - 0.75px);
  width: 1.5px;
  height: 100%;
}

.is-deck .scene {
  min-height: calc(100svh - 3.5rem);
  padding-block: 1rem;
  border: 0;
  scroll-margin-top: -1rem;
}

.is-deck .greeting-inner {
  grid-template-columns: minmax(0, 1fr) 22rem;
}

.is-deck .greeting-aside {
  order: 0;
}

.is-deck .greeting-doodle {
  width: min(13rem, 22svh);
  margin: 0 auto 2svh;
}

.is-deck .contents-card a {
  min-height: clamp(2rem, 5svh, 2.75rem);
  font-size: clamp(1.0625rem, 2.4svh, 1.25rem);
}

.is-deck .contents-card {
  display: grid;
}

.is-deck .ending {
  display: grid;
  align-content: center;
}

.is-deck .step {
  display: flex;
  align-items: center;
}

.is-deck .step-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: auto 1fr;
  grid-template-areas:
    'doodle body'
    'lead body';
  align-items: start;
  column-gap: clamp(2.5rem, 5vw, 5rem);
  width: 100%;
}

.is-deck .is-flipped .step-grid {
  grid-template-rows: auto auto 1fr;
  grid-template-areas:
    'lead doodle'
    'points doodle'
    'points rest';
}

.is-deck .step-doodle {
  grid-area: doodle;
  align-self: start;
  width: min(22rem, 40svh);
  margin-bottom: 2svh;
}

.is-deck .step-lead {
  grid-area: lead;
}

.is-deck .step-points {
  grid-area: points;
}

.is-deck .step-rest {
  grid-area: rest;
}

.is-deck .is-flipped .step-body {
  display: contents;
}

.is-deck .is-flipped .step-points {
  margin-top: 1.35em;
}

.is-deck .rule {
  font-size: clamp(2rem, 5.2svh, 3.25rem);
}

.is-deck .step-body {
  grid-area: body;
  margin-top: 0;
  font-size: clamp(0.9375rem, 1.9svh, 1.125rem);
  line-height: 1.5;
}

.is-deck .step-rest,
.is-deck .step-points > * + *,
.is-deck .step-rest > * + * {
  margin-top: 1.35em;
}

.is-deck .tips {
  padding: 1em 1em 0.9em;
}

.is-deck .next {
  display: none;
}

@media (prefers-reduced-motion: no-preference) {
  .dot,
  .stop::before {
    transition:
      scale 0.25s ease-out,
      background-color 0.25s ease-out,
      border-color 0.25s ease-out;
  }

  .reveal {
    transition:
      opacity 0.4s ease-out,
      translate 0.4s cubic-bezier(0.2, 0.7, 0.2, 1);
    transition-delay: calc(var(--order, 0) * 70ms);
  }

  .hl {
    transition:
      background-size 0.5s cubic-bezier(0.6, 0, 0.3, 1) 0.3s,
      color 0.25s ease-out 0.4s;
  }

  .scene:not(.is-seen) .reveal {
    opacity: 0;
    translate: 0 0.75rem;
  }

  .scene:not(.is-seen) .hl {
    color: inherit;
    background-size: 0% 68%;
  }
}
</style>
