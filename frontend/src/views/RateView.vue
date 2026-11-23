<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowRight, Check, ChevronRight, Loader2 } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import NoteStar from '@/components/NoteStar.vue'
import RateDeck from '@/components/RateDeck.vue'
import { readStored, writeStored } from '@/storage'
import { useAuthStore } from '@/stores/auth'
import {
  issueTokens,
  keySetFingerprint,
  localMaster,
  localTokens,
  pendingOfferings,
  UnknownKeyError,
  vaultExists,
  type RateableOffering,
} from '@/crypto/tokens'
import { api } from '@/api'

const { t, d } = useI18n()
const router = useRouter()
const userId = useAuthStore().user!.id
const offerings = ref<RateableOffering[] | null>(null)
const tokens = ref(new Map<number, boolean>())
const error = ref('')
const retryable = ref(false)
const busy = ref(false)
const fingerprint = ref('')

// shortcut: hidden courses live in this browser only; move them into the token vault if students ask for it on every device
const hiddenKey = `rate-hidden:${userId}`
function readHidden(): number[] {
  try {
    return JSON.parse(readStored(hiddenKey) ?? '[]')
  } catch {
    return []
  }
}
const hidden = ref(new Set<number>(readHidden()))
function setHidden(id: number, hide: boolean) {
  const next = new Set(hidden.value)
  if (hide) next.add(id)
  else next.delete(id)
  hidden.value = next
  writeStored(hiddenKey, JSON.stringify([...next]))
}

const issued = computed(() =>
  (offerings.value ?? []).filter((offering) => tokens.value.has(offering.offeringId)),
)
const notTaken = computed(() =>
  issued.value.filter(
    (offering) => !tokens.value.get(offering.offeringId) && hidden.value.has(offering.offeringId),
  ),
)
const rateable = computed(() =>
  issued.value.filter(
    (offering) => tokens.value.get(offering.offeringId) || !hidden.value.has(offering.offeringId),
  ),
)
const pending = computed(() =>
  rateable.value.filter((offering) => !tokens.value.get(offering.offeringId)),
)
const pendingByYear = computed(() => {
  const years = new Map<string, RateableOffering[]>()
  for (const offering of pending.value) {
    years.set(offering.academicYear, [...(years.get(offering.academicYear) ?? []), offering])
  }
  return [...years].sort(([a], [b]) => b.localeCompare(a))
})
const tickets = computed(() =>
  pending.value.map((offering) => ({
    id: offering.offeringId,
    title: offering.title,
    term: term(offering),
  })),
)
const finished = computed(() =>
  rateable.value.filter((offering) => tokens.value.get(offering.offeringId)),
)
const unavailable = computed(() =>
  (offerings.value ?? []).filter((offering) => !tokens.value.has(offering.offeringId)),
)
const allDone = computed(() => rateable.value.length > 0 && !pending.value.length)

function term(offering: RateableOffering) {
  return t('rate.term', { year: offering.academicYear, semester: offering.semesterNo })
}

function status(offering: RateableOffering): string {
  if (!offering.open) {
    return offering.opensAt
      ? t('rate.opensOn', { date: d(offering.opensAt, 'short') })
      : t('rate.opensLater')
  }
  if (!offering.fingerprint) {
    return t('rate.opensLater')
  }
  return t('rate.otherDevice')
}

async function load() {
  error.value = ''
  retryable.value = false
  busy.value = true
  try {
    const master = await localMaster(userId)
    if (master) {
      offerings.value = await issueTokens(userId)
      tokens.value = new Map(
        (await localTokens(userId)).map((token) => [token.offeringId, !!token.submitted]),
      )
    } else if (await vaultExists()) {
      await router.replace({ name: 'recovery' })
    } else {
      offerings.value = await api<RateableOffering[]>('GET', '/api/tokens/offerings')
      tokens.value = new Map(
        pendingOfferings(offerings.value, []).map((offering) => [offering.offeringId, false]),
      )
    }
  } catch (e) {
    retryable.value = !(e instanceof UnknownKeyError)
    error.value = retryable.value ? t('rate.issueFailed') : t('rate.unknownKey')
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  load()
  keySetFingerprint()
    .then((value) => (fingerprint.value = value))
    .catch(() => {})
})
</script>

<template>
  <main class="page lg:max-w-6xl">
    <span class="eyebrow">{{ t('rateForm.anonymous') }}</span>
    <h1 class="h1">{{ t('rate.title') }}</h1>
    <AppNotice v-if="error" kind="error">
      <p>{{ error }}</p>
      <button v-if="retryable" class="btn btn-outline mt-2" :disabled="busy" @click="load">
        <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
        {{ t('common.retry') }}
      </button>
    </AppNotice>
    <p v-if="busy && !offerings" class="lede flex items-center gap-2">
      <Loader2 class="size-4 animate-spin" aria-hidden="true" /> {{ t('rate.loading') }}
    </p>
    <section v-if="offerings && !offerings.length" class="card mt-6 grid max-w-md gap-4">
      <p>{{ t('rate.noGroup') }}</p>
      <RouterLink :to="{ name: 'group' }" class="btn btn-primary">{{ t('nav.groups') }}</RouterLink>
    </section>
    <div v-else-if="offerings" class="mt-5 grid gap-8 lg:grid-cols-2 lg:items-start lg:gap-12">
      <section v-if="rateable.length" class="grid gap-4 lg:sticky lg:top-20">
        <div>
          <p class="flex items-baseline justify-between gap-3 text-sm font-medium">
            <span>{{ t('rate.progress', { done: finished.length, total: rateable.length }) }}</span>
            <span v-if="allDone" class="text-mask">{{ t('rate.allDone') }}</span>
          </p>
          <div
            class="mt-2 h-3 overflow-hidden rounded-full border-[1.5px] border-ink bg-panel"
            role="progressbar"
            aria-valuemin="0"
            :aria-valuemax="rateable.length"
            :aria-valuenow="finished.length"
            :aria-label="t('rate.progress', { done: finished.length, total: rateable.length })"
          >
            <div
              class="h-full rounded-full bg-mark transition-[width] duration-500"
              :style="{ width: `${(finished.length / rateable.length) * 100}%` }"
            ></div>
          </div>
        </div>
        <RateDeck
          v-if="pending.length"
          class="mt-2"
          :tickets="tickets"
          :user-id="userId"
          @hide="setHidden($event, true)"
        />
        <NoteStar v-else-if="allDone" class="mx-auto mt-2 size-20" />
      </section>

      <div class="grid gap-8" :class="{ 'lg:col-span-2': !rateable.length }">
        <section v-for="[year, courses] in pendingByYear" :key="year">
          <h2 class="eyebrow mb-3">
            {{ t('rate.toRate') }} · <span class="mono not-italic">{{ year }}</span>
          </h2>
          <ul class="m-0 grid list-none gap-2 p-0">
            <li v-for="offering in courses" :key="offering.offeringId">
              <RouterLink :to="`/rate/${offering.offeringId}`" class="row-link min-h-14 py-3">
                <span class="min-w-0 flex-1">
                  <strong class="block font-semibold [overflow-wrap:anywhere]">{{
                    offering.title
                  }}</strong>
                  <span class="mono text-xs text-muted">{{ term(offering) }}</span>
                </span>
                <ArrowRight class="size-4 shrink-0 text-mask" aria-hidden="true" />
              </RouterLink>
            </li>
          </ul>
        </section>

        <section v-if="finished.length">
          <h2 class="eyebrow mb-3">{{ t('rate.done') }}</h2>
          <ul class="m-0 grid list-none gap-2 p-0">
            <li v-for="offering in finished" :key="offering.offeringId">
              <RouterLink :to="`/rate/${offering.offeringId}`" class="row-link min-h-14 py-3">
                <Check class="size-5 shrink-0 text-mask" aria-hidden="true" />
                <span class="min-w-0 flex-1">
                  <strong class="block font-semibold [overflow-wrap:anywhere]">{{
                    offering.title
                  }}</strong>
                  <span class="mono text-xs text-muted">{{ term(offering) }}</span>
                </span>
                <span class="shrink-0 text-sm font-semibold text-mask">{{
                  t('rateForm.edit')
                }}</span>
              </RouterLink>
            </li>
          </ul>
        </section>

        <details v-if="notTaken.length" class="group">
          <summary
            class="flex min-h-11 cursor-pointer list-none items-center gap-1 text-sm font-medium text-mask [&::-webkit-details-marker]:hidden"
          >
            <ChevronRight
              class="size-4 transition-transform group-open:rotate-90"
              aria-hidden="true"
            />
            {{ t('rate.hidden', { n: notTaken.length }) }}
          </summary>
          <ul class="m-0 mt-1 list-none p-0 text-sm">
            <li
              v-for="offering in notTaken"
              :key="offering.offeringId"
              class="flex flex-wrap items-center justify-between gap-x-3 border-t border-line py-1"
            >
              <span class="font-medium [overflow-wrap:anywhere]">{{ offering.title }}</span>
              <button class="btn btn-ghost min-h-11" @click="setHidden(offering.offeringId, false)">
                {{ t('rate.unhide') }}
              </button>
            </li>
          </ul>
        </details>

        <details v-if="unavailable.length" :open="!rateable.length" class="group">
          <summary
            class="flex min-h-11 cursor-pointer list-none items-center gap-1 text-sm font-medium text-mask [&::-webkit-details-marker]:hidden"
          >
            <ChevronRight
              class="size-4 transition-transform group-open:rotate-90"
              aria-hidden="true"
            />
            {{ t('rate.locked', { n: unavailable.length }) }}
          </summary>
          <ul class="m-0 mt-1 list-none p-0 text-sm">
            <li
              v-for="offering in unavailable"
              :key="offering.offeringId"
              class="flex flex-wrap items-baseline justify-between gap-x-3 border-t border-line py-2"
            >
              <span class="font-medium [overflow-wrap:anywhere]">{{ offering.title }}</span>
              <span class="text-muted">{{ status(offering) }}</span>
            </li>
          </ul>
        </details>

        <div class="text-sm text-muted">
          <p>{{ t('rate.pastYears') }}</p>
          <p class="flex flex-wrap gap-x-6 font-medium">
            <RouterLink :to="{ name: 'group' }" class="inline-flex min-h-11 items-center">{{
              t('rate.pastYearsLink')
            }}</RouterLink>
            <RouterLink :to="{ name: 'recovery' }" class="inline-flex min-h-11 items-center">{{
              t('recovery.title')
            }}</RouterLink>
          </p>
          <p v-if="fingerprint" class="mt-2 text-xs">
            {{ t('rate.keys') }} <span class="mono font-medium text-ink">{{ fingerprint }}</span
            >.
            {{ t('rate.keysHint') }}
          </p>
        </div>
      </div>
    </div>
  </main>
</template>
