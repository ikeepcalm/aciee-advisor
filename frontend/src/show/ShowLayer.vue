<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { setTheme } from '@/theme'
import { beats } from './beats'
import { beginRun, cancelRuns, director, resetStage, setPace, Skipped, stage } from './director'
import { clearCache, installFetchCache } from './fetchCache'
import { listen, send } from './relay'
import { registerShowcase, SHOW_USER } from './showcase'
import {
  showLang,
  showMirror,
  stopShow,
  type RemoteCommand,
  type ShowLang,
  type ShowMessage,
  type StageState,
} from './state'
import { i18n, setLocale } from '@/i18n'
import ShowSpot from './ShowSpot.vue'

const props = defineProps<{ room: string }>()

const beat = ref(-1)
const playing = ref(false)
const ready = ref(false)
const linked = ref(false)
const handled = new Set<string>()
let current: Promise<void> = Promise.resolve()
let linkedAt = 0
let closeLink = () => {}

installFetchCache()
registerShowcase()
const auth = useAuthStore()
const signInShowcase = () => {
  auth.user = SHOW_USER
  auth.loaded = true
}
signInShowcase()
auth.load().finally(signInShowcase)
setTheme('light')

function report() {
  if (showMirror.value) return
  send(props.room, {
    from: 'stage',
    beat: beat.value,
    playing: playing.value,
    ready: ready.value,
    beats: beats.length,
    lang: i18n.global.locale.value === 'ro' ? 'ro' : 'en',
  }).catch(() => undefined)
}

let pressed = 0

async function advance(index: number) {
  const mine = ++pressed
  cancelRuns()
  await current
  if (mine !== pressed || index < 0 || index >= beats.length) {
    return
  }
  resetStage()
  signInShowcase()
  const run = beginRun()
  beat.value = index
  playing.value = true
  report()
  current = beats[index]!.run(director(run))
    .catch((e) => {
      if (!(e instanceof Skipped)) console.error(e)
    })
    .finally(() => {
      if (mine === pressed) {
        playing.value = false
        report()
      }
    })
}

function next() {
  if (ready.value) advance(Math.min(beat.value + 1, beats.length - 1))
}
function prev() {
  if (ready.value) advance(Math.max(beat.value - 1, 0))
}
function replay() {
  if (ready.value && beat.value >= 0) advance(beat.value)
}

let lastStage: StageState | null = null
let following: Promise<void> = Promise.resolve()

// The phone preview replays the beats itself and follows what the laptop reports.
async function follow(message: StageState) {
  const previous = lastStage
  lastStage = message
  if (!ready.value) return
  const langChanged = message.lang !== i18n.global.locale.value
  if (langChanged) await applyLang(message.lang)
  if (
    message.beat >= 0 &&
    (!previous ||
      langChanged ||
      message.beat !== previous.beat ||
      (message.playing && !previous.playing))
  ) {
    advance(message.beat)
  }
}

function onMessage(message: ShowMessage) {
  if (!showMirror.value) onCommand(message)
  else if (message.from === 'stage') following = following.then(() => follow(message))
}

function onCommand(message: ShowMessage) {
  if (message.from !== 'remote' || handled.has(message.id)) {
    return
  }
  handled.add(message.id)
  // The relay replays its last message on connect; a command that old is not a fresh press.
  if (performance.now() - linkedAt < 1500) {
    return
  }
  const command = message as RemoteCommand
  if (command.cmd === 'next') next()
  else if (command.cmd === 'prev') prev()
  else if (command.cmd === 'replay') replay()
  else if (command.cmd === 'goto' && command.beat !== undefined) advance(command.beat)
  else if (command.cmd === 'lang' && command.lang) switchLang(command.lang)
}

async function switchLang(lang: ShowLang) {
  await applyLang(lang)
  if (beat.value >= 0) replay()
  else report()
}

async function applyLang(lang: ShowLang) {
  try {
    sessionStorage.setItem('show.lang', lang)
  } catch {
    // the language lasts until the tab reloads
  }
  await setLocale(lang)
  for (let tries = 0; i18n.global.locale.value !== lang && tries < 40; tries++) {
    await new Promise((resolve) => setTimeout(resolve, 25))
  }
  clearCache()
}

let escapedAt = 0
function onKey(event: KeyboardEvent) {
  if (!event.isTrusted) {
    return
  }
  const forward = ['ArrowRight', 'PageDown', ' ', 'Enter'].includes(event.key)
  const back = ['ArrowLeft', 'PageUp'].includes(event.key)
  if (forward || back || event.key === 'r' || event.key === 'Escape') {
    event.preventDefault()
    event.stopImmediatePropagation()
  }
  if (forward) next()
  else if (back) prev()
  else if (event.key === 'r') replay()
  else if (event.key === 'Escape') {
    if (performance.now() - escapedAt < 1000) {
      stopShow()
      location.assign('/')
    }
    escapedAt = performance.now()
  }
}

async function warmUp() {
  await setLocale(showLang())
  setPace(0)
  for (let index = 0; index < beats.length; index++) {
    resetStage()
    signInShowcase()
    const run = beginRun()
    await beats[index]!.run(director(run)).catch((e) => {
      if (!(e instanceof Skipped)) console.error('warm-up', index + 1, e)
    })
  }
  setPace(1)
  resetStage()
  stage.scene = null
  stage.cursor.visible = false
  await router.push('/')
  scrollTo({ top: 0, behavior: 'instant' })
  ready.value = true
  report()
  if (lastStage) {
    const caughtUp = lastStage
    lastStage = null
    following = following.then(() => follow(caughtUp))
  }
}

onMounted(() => {
  document.documentElement.classList.add('show-on')
  if (!showMirror.value) addEventListener('keydown', onKey, { capture: true })
  closeLink = listen(props.room, onMessage, (up) => {
    if (up && !linked.value) {
      linkedAt = performance.now()
      report()
    }
    linked.value = up
  })
  warmUp()
})

onBeforeUnmount(() => {
  document.documentElement.classList.remove('show-on')
  removeEventListener('keydown', onKey, { capture: true })
  closeLink()
})
</script>

<template>
  <div class="show-layer" aria-hidden="true">
    <ShowSpot :target="stage.spot" />

    <Transition name="show-fade">
      <div v-if="stage.scene" class="show-scene">
        <component :is="stage.scene" :key="stage.sceneTake" />
      </div>
    </Transition>

    <Transition name="show-rise">
      <div
        v-if="stage.caption"
        :key="stage.caption.title"
        class="show-caption"
        :class="stage.caption.at === 'top' ? 'top-20' : 'bottom-8'"
      >
        <strong class="show-caption-title display">{{ stage.caption.title }}</strong>
        <span v-if="stage.caption.body" class="show-caption-body">{{ stage.caption.body }}</span>
      </div>
    </Transition>

    <div
      class="show-cursor"
      :class="{ 'is-down': stage.cursor.down, 'is-hidden': !stage.cursor.visible }"
      :style="{
        transform: `translate(${stage.cursor.x}px, ${stage.cursor.y}px)`,
        transitionDuration: `${stage.cursor.ms}ms, 200ms`,
      }"
    >
      <span class="show-cursor-halo" />
      <span class="show-cursor-ripple" />
      <svg viewBox="0 0 24 24" width="44" height="44">
        <path
          d="M4 2.5 19.5 13l-7 1.4-3.6 6.6z"
          class="fill-ink stroke-panel"
          stroke-width="1.8"
          stroke-linejoin="round"
        />
      </svg>
    </div>

    <Transition name="show-lift">
      <div v-if="!ready || beat < 0" class="show-curtain">
        <p class="display text-5xl">ACIEE <span class="text-mask">Advisor</span></p>
      </div>
    </Transition>

    <div class="show-status">
      <span :class="linked ? 'bg-mask' : 'bg-copper'" />
    </div>
  </div>
</template>

<style>
html.show-on,
html.show-on * {
  cursor: none !important;
  scrollbar-width: none;
}
html.show-on ::-webkit-scrollbar {
  display: none;
}

.show-paper {
  position: relative;
  height: 100%;
  overflow: hidden;
  /* 1em = 16px on a 1440x810 stage, so scenes scale as one picture on any projector */
  font-size: min(1.1111vw, 1.9753vh);
  line-height: 1.35;
  color: var(--ink);
  background-color: var(--board);
  background-image:
    linear-gradient(var(--rule) 1px, transparent 1px),
    linear-gradient(90deg, var(--rule) 1px, transparent 1px);
  background-size: 2em 2em;
}
.show-paper .display {
  line-height: 1.1;
}
.show-paper .pop {
  animation: show-pop 600ms cubic-bezier(0.2, 0.9, 0.3, 1.25) var(--at, 0s) both;
}
.show-paper svg .pop {
  transform-box: fill-box;
  transform-origin: center;
}
.show-paper .rise {
  animation: show-rise 700ms cubic-bezier(0.2, 0.8, 0.2, 1) var(--at, 0s) both;
}
.show-paper .fade {
  animation: show-fade-in 600ms ease var(--at, 0s) both;
}
.show-paper .draw {
  stroke-dasharray: 1;
  animation: show-draw 800ms ease-in-out var(--at, 0s) both;
}
.show-paper .swipe {
  animation: show-swipe 900ms cubic-bezier(0.3, 0.7, 0.2, 1) var(--at, 0s) both;
}
.show-paper .grow {
  transform-origin: left center;
  animation: show-grow 900ms cubic-bezier(0.2, 0.8, 0.2, 1) var(--at, 0s) both;
}
@keyframes show-pop {
  from {
    opacity: 0;
    scale: 0.7;
  }
}
@keyframes show-rise {
  from {
    opacity: 0;
    translate: 0 1.2em;
  }
}
@keyframes show-fade-in {
  from {
    opacity: 0;
  }
}
@keyframes show-draw {
  from {
    opacity: 0;
    stroke-dashoffset: 1;
  }
  8% {
    opacity: 1;
  }
  to {
    stroke-dashoffset: 0;
  }
}
@keyframes show-swipe {
  from {
    background-size: 0% 68%;
  }
}
@keyframes show-grow {
  from {
    scale: 0 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .show-paper * {
    animation-delay: 0s !important;
  }
}
</style>

<style scoped>
.show-layer {
  position: fixed;
  inset: 0;
  z-index: 100;
  pointer-events: none;
}
.show-scene {
  position: fixed;
  inset: 0;
}
.show-caption {
  position: fixed;
  left: 50%;
  translate: -50% 0;
  rotate: -0.6deg;
  width: max-content;
  max-width: min(56rem, calc(100vw - 6rem));
  padding: 1.1rem 2.25rem 1.25rem;
  border: 2px solid var(--color-ink);
  border-radius: var(--wobble);
  background: color-mix(in srgb, var(--mark) 26%, var(--panel));
  box-shadow: 5px 7px 0 color-mix(in srgb, var(--ink) 30%, transparent);
  text-align: center;
  text-wrap: balance;
}
.show-caption::before {
  content: '';
  position: absolute;
  top: -0.85rem;
  left: 50%;
  width: 7rem;
  height: 1.6rem;
  translate: -50% 0;
  rotate: 2deg;
  background: color-mix(in srgb, var(--mark) 80%, transparent);
}
.show-caption-title {
  display: block;
  font-size: clamp(2.25rem, 2.8vw, 2.75rem);
  line-height: 1.1;
}
.show-caption-body {
  display: block;
  margin-top: 0.35rem;
  font-size: clamp(1.375rem, 1.55vw, 1.5rem);
  line-height: 1.35;
  color: color-mix(in srgb, var(--ink) 82%, var(--panel));
}
.show-cursor {
  position: fixed;
  top: 0;
  left: 0;
  margin: -5px 0 0 -7px;
  transition-property: transform, opacity;
  transition-timing-function: cubic-bezier(0.65, 0, 0.35, 1), ease;
}
.show-cursor svg {
  position: relative;
  display: block;
  transform-origin: 7px 5px;
  filter: drop-shadow(2px 3px 2px rgb(0 0 0 / 0.3));
  transition: scale 120ms ease;
}
.show-cursor.is-down svg {
  scale: 0.84;
}
.show-cursor.is-hidden {
  opacity: 0;
}
.show-cursor-halo,
.show-cursor-ripple {
  position: absolute;
  top: 5px;
  left: 7px;
  width: 4.5rem;
  height: 4.5rem;
  margin: -2.25rem 0 0 -2.25rem;
  border-radius: 999px;
}
.show-cursor-halo {
  background: color-mix(in srgb, var(--mark) 55%, transparent);
  transition: scale 160ms ease;
}
.show-cursor.is-down .show-cursor-halo {
  scale: 0.7;
}
.show-cursor-ripple {
  border: 3px solid var(--copper);
  opacity: 0;
}
.show-cursor.is-down .show-cursor-ripple {
  animation: show-click-ripple 600ms ease-out;
}
@keyframes show-click-ripple {
  from {
    opacity: 0.9;
    scale: 0.35;
  }
  to {
    opacity: 0;
    scale: 1.7;
  }
}
.show-curtain {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  background: #fff;
}
.show-lift-leave-active {
  transition: opacity 400ms ease 250ms;
}
.show-lift-leave-to {
  opacity: 0;
}
.show-status {
  position: fixed;
  right: 0.5rem;
  bottom: 0.5rem;
}
.show-status span {
  display: block;
  width: 0.375rem;
  height: 0.375rem;
  border-radius: 999px;
  opacity: 0.6;
}
.show-fade-enter-active,
.show-fade-leave-active {
  transition: opacity 500ms ease;
}
.show-fade-enter-from,
.show-fade-leave-to {
  opacity: 0;
}
.show-rise-enter-active,
.show-rise-leave-active {
  transition:
    opacity 350ms ease,
    translate 350ms ease;
}
.show-rise-enter-from,
.show-rise-leave-to {
  opacity: 0;
  translate: -50% 12px;
}
</style>
