<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, ArrowRight, EyeOff } from 'lucide-vue-next'

interface Ticket {
  id: number
  title: string
  term: string
}

const props = defineProps<{ tickets: Ticket[]; userId: number }>()
const emit = defineEmits<{ hide: [id: number] }>()
const { t } = useI18n()
const router = useRouter()

// the pile is shuffled once per tab, so leaving to rate and coming back keeps the same order
const storageKey = `rate-deck:${props.userId}`
function shuffled(ids: number[]) {
  const copy = [...ids]
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[copy[i], copy[j]] = [copy[j]!, copy[i]!]
  }
  return copy
}
function readOrder(): number[] {
  try {
    return JSON.parse(sessionStorage.getItem(storageKey) ?? '[]')
  } catch {
    return []
  }
}
const order = ref<number[]>(readOrder())
watch(
  () => props.tickets.map((ticket) => ticket.id),
  (ids) => {
    const kept = order.value.filter((id) => ids.includes(id))
    order.value = [...kept, ...shuffled(ids.filter((id) => !kept.includes(id)))]
  },
  { immediate: true },
)
watch(order, (ids) => {
  try {
    sessionStorage.setItem(storageKey, JSON.stringify(ids))
  } catch {
    // storage blocked: the pile reshuffles on the next visit
  }
})

const top = computed(() => props.tickets.find((ticket) => ticket.id === order.value[0]))
const revealed = ref(false)
const drag = ref(0)
let startX: number | null = null

function draw() {
  revealed.value = true
}
function rate() {
  if (top.value) router.push(`/rate/${top.value.id}`)
}
function another() {
  drag.value = 0
  revealed.value = false
  order.value = [...order.value.slice(1), order.value[0]!]
}
function hide() {
  revealed.value = false
  if (top.value) emit('hide', top.value.id)
}

function onDown(event: PointerEvent) {
  if (!revealed.value || (event.target as HTMLElement).closest('a, button')) return
  startX = event.clientX
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}
function onMove(event: PointerEvent) {
  if (startX !== null) drag.value = event.clientX - startX
}
function onUp() {
  if (startX === null) return
  startX = null
  if (drag.value > 110) rate()
  else if (drag.value < -110) another()
  drag.value = 0
}
function onKey(event: KeyboardEvent) {
  if (!revealed.value) return
  if (event.key === 'ArrowRight') rate()
  else if (event.key === 'ArrowLeft') another()
}
</script>

<template>
  <div class="deck" :class="{ 'deck-thin': tickets.length < 3, 'deck-single': tickets.length < 2 }">
    <div
      v-if="top"
      class="ticket"
      :class="{ 'is-revealed': revealed, 'is-dragging': drag !== 0 }"
      :style="drag ? { transform: `translateX(${drag}px) rotate(${drag / 18}deg)` } : undefined"
      tabindex="-1"
      @pointerdown="onDown"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointercancel="onUp"
      @keydown="onKey"
    >
      <div class="ticket-inner">
        <button
          class="ticket-face ticket-back"
          :aria-label="t('rate.deck.draw')"
          :tabindex="revealed ? -1 : 0"
          :aria-hidden="revealed"
          @click="draw"
        >
          <span class="mono text-xs tracking-widest text-muted uppercase">{{
            t('rate.deck.pile', { n: tickets.length })
          }}</span>
          <span class="display text-5xl leading-none">{{ t('rate.deck.draw') }}</span>
          <span class="ticket-stamp mono text-xs">ACIEE</span>
        </button>
        <div class="ticket-face ticket-front" :aria-hidden="!revealed">
          <span class="eyebrow mb-0">{{ t('rate.deck.yours') }}</span>
          <strong class="display text-3xl leading-tight [overflow-wrap:anywhere]">{{
            top.title
          }}</strong>
          <span class="mono text-xs text-muted">{{ top.term }}</span>
          <div class="mt-auto grid gap-2">
            <RouterLink
              :to="`/rate/${top.id}`"
              class="btn btn-primary w-full"
              :tabindex="revealed ? 0 : -1"
            >
              {{ t('rate.action') }} <ArrowRight class="size-4" aria-hidden="true" />
            </RouterLink>
            <div class="grid grid-cols-2 gap-2">
              <button
                class="btn btn-outline"
                :disabled="tickets.length < 2"
                :tabindex="revealed ? 0 : -1"
                @click="another"
              >
                <ArrowLeft class="size-4" aria-hidden="true" /> {{ t('rate.deck.another') }}
              </button>
              <button class="btn btn-ghost" :tabindex="revealed ? 0 : -1" @click="hide">
                <EyeOff class="size-4" aria-hidden="true" /> {{ t('rate.deck.notTaken') }}
              </button>
            </div>
            <p class="m-0 text-center text-xs text-muted [@media(hover:hover)]:hidden">
              {{ t('rate.deck.swipe') }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.deck {
  position: relative;
  min-height: 22rem;
  perspective: 1400px;
}
.deck::before,
.deck::after {
  content: '';
  position: absolute;
  inset: 0;
  border: 1.5px solid var(--line);
  border-radius: var(--wobble-alt);
  background: var(--panel);
}
.deck::before {
  transform: rotate(-3.5deg) translate(-6px, 8px);
}
.deck::after {
  transform: rotate(2deg) translate(5px, 4px);
}
.deck-thin::before,
.deck-single::after {
  display: none;
}
.ticket {
  position: relative;
  z-index: 1;
  min-height: 22rem;
  touch-action: pan-y;
  transition: transform 0.25s ease;
}
.ticket.is-dragging {
  transition: none;
}
.ticket-inner {
  position: relative;
  min-height: 22rem;
  transform-style: preserve-3d;
  transition: transform 0.55s cubic-bezier(0.3, 1.4, 0.5, 1);
}
.is-revealed .ticket-inner {
  transform: rotateY(180deg);
}
.ticket-face {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.5rem;
  border: 1.5px solid var(--ink);
  border-radius: var(--wobble);
  background: var(--panel);
  color: var(--ink);
  box-shadow: 3px 4px 0 color-mix(in srgb, var(--ink) 28%, transparent);
  backface-visibility: hidden;
}
.ticket-back {
  cursor: pointer;
  align-items: center;
  justify-content: center;
  text-align: center;
  background:
    repeating-linear-gradient(
      -45deg,
      transparent 0 14px,
      color-mix(in srgb, var(--mask) 9%, transparent) 14px 16px
    ),
    var(--panel);
}
.ticket-back:hover,
.ticket-back:focus-visible {
  border-color: var(--mask);
}
.ticket-stamp {
  position: absolute;
  right: 1.25rem;
  bottom: 1rem;
  padding: 0.15rem 0.5rem;
  border: 1.5px solid var(--copper);
  border-radius: var(--wobble-alt);
  color: var(--copper);
  transform: rotate(-8deg);
}
.ticket-front {
  transform: rotateY(180deg);
}
</style>
