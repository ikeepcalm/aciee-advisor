<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, RotateCcw } from 'lucide-vue-next'
import { beats } from '@/show/beats'
import { listen, send } from '@/show/relay'
import { ROOM_PATTERN, type RemoteCommand, type ShowLang, type StageState } from '@/show/state'

const PITCH_SECONDS = 5 * 60

const route = useRoute()
const room = String(route.query.room ?? '')
const valid = ROOM_PATTERN.test(room)
const linked = ref(false)
const stage = ref<StageState | null>(null)
const sending = ref(false)
const startedAt = ref<number | null>(null)
const now = ref(Date.now())
let closeLink = () => {}
let ticker = 0
let wakeLock: WakeLockSentinel | null = null

const current = computed(() =>
  stage.value && stage.value.beat >= 0 ? beats[stage.value.beat] : null,
)
const upcoming = computed(() => beats[(stage.value?.beat ?? -1) + 1] ?? null)
const left = computed(() =>
  startedAt.value === null
    ? PITCH_SECONDS
    : PITCH_SECONDS - Math.floor((now.value - startedAt.value) / 1000),
)
const clock = computed(() => {
  const s = Math.abs(left.value)
  return `${left.value < 0 ? '−' : ''}${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
})

async function command(cmd: RemoteCommand['cmd'], lang?: ShowLang) {
  navigator.vibrate?.(15)
  if (cmd === 'next' && startedAt.value === null) {
    startedAt.value = Date.now()
  }
  sending.value = true
  try {
    await send(room, { from: 'remote', id: crypto.randomUUID(), at: Date.now(), cmd, lang })
  } finally {
    sending.value = false
  }
}

function resetClock() {
  startedAt.value = null
}

async function keepAwake() {
  try {
    wakeLock = await navigator.wakeLock?.request('screen')
  } catch {
    // the phone may sleep; keep tapping
  }
}

function onVisible() {
  if (document.visibilityState === 'visible') keepAwake()
}

onMounted(() => {
  if (!valid) return
  closeLink = listen(
    room,
    (message) => {
      if (message.from === 'stage') stage.value = message
    },
    (up) => (linked.value = up),
  )
  ticker = window.setInterval(() => (now.value = Date.now()), 500)
  keepAwake()
  document.addEventListener('visibilitychange', onVisible)
})

onBeforeUnmount(() => {
  closeLink()
  clearInterval(ticker)
  wakeLock?.release().catch(() => undefined)
  document.removeEventListener('visibilitychange', onVisible)
})
</script>

<template>
  <main class="remote mx-auto flex min-h-svh max-w-md flex-col gap-3 p-4">
    <p v-if="!valid" class="card mt-8">
      Open this page as <span class="mono">/show/remote?room=…</span> with the same room as the
      laptop.
    </p>
    <template v-else>
      <header class="flex items-center justify-between gap-3">
        <span class="mono flex items-center gap-2 text-xs text-muted">
          <span class="size-2.5 rounded-full" :class="linked && stage ? 'bg-mask' : 'bg-copper'" />
          {{
            !linked
              ? 'connecting…'
              : !stage
                ? 'laptop not open'
                : !stage.ready
                  ? 'warming up…'
                  : 'ready'
          }}
        </span>
        <button
          class="mono text-3xl font-medium"
          :class="left < 30 ? 'text-copper' : ''"
          :aria-label="'Reset timer'"
          @click="resetClock"
        >
          {{ clock }}
        </button>
      </header>

      <section class="card">
        <p class="eyebrow mb-1">
          {{ current ? `${(stage?.beat ?? 0) + 1} / ${beats.length}` : 'Standby' }}
          <span v-if="stage?.playing" class="text-copper"> · playing</span>
        </p>
        <h1 class="display text-3xl leading-tight">
          {{ current?.title ?? 'Before the first beat' }}
        </h1>
        <p v-if="current" class="mt-2 text-base">{{ current.note }}</p>
      </section>

      <p v-if="upcoming" class="text-sm text-muted">
        Next: <strong class="text-ink">{{ upcoming.title }}</strong>
      </p>

      <button
        class="btn btn-primary next mt-auto w-full text-2xl"
        :disabled="!linked || !stage?.ready || sending || !upcoming"
        @click="command('next')"
      >
        {{ upcoming ? 'Next' : 'The end' }}
      </button>
      <div class="grid grid-cols-[1fr_1fr_auto] gap-3">
        <button class="btn btn-outline h-16" :disabled="!stage?.ready" @click="command('prev')">
          <ChevronLeft class="size-5" aria-hidden="true" /> Back
        </button>
        <button class="btn btn-outline h-16" :disabled="!stage?.ready" @click="command('replay')">
          <RotateCcw class="size-5" aria-hidden="true" /> Replay
        </button>
        <button
          class="btn btn-outline mono h-16 px-4"
          :disabled="!stage?.ready || sending"
          :aria-label="'Switch the show language'"
          @click="command('lang', stage?.lang === 'ro' ? 'en' : 'ro')"
        >
          {{ stage?.lang === 'ro' ? 'RO' : 'EN' }}
        </button>
      </div>
    </template>
  </main>
</template>

<style scoped>
.remote {
  padding-bottom: calc(1rem + env(safe-area-inset-bottom));
}
.next {
  min-height: 42svh;
  touch-action: manipulation;
}
</style>
