<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AppNotice from '@/components/AppNotice.vue'
import { useAuthStore } from '@/stores/auth'
import {
  issueTokens,
  localMaster,
  localTokens,
  UnknownKeyError,
  type RateableOffering,
} from '@/crypto/tokens'

const { t, d } = useI18n()
const router = useRouter()
const userId = useAuthStore().user!.id
const offerings = ref<RateableOffering[] | null>(null)
const tokens = ref(new Map<number, boolean>())
const error = ref('')
const retryable = ref(false)
const busy = ref(false)

const terms = computed(() => {
  const groups = new Map<string, RateableOffering[]>()
  for (const offering of offerings.value ?? []) {
    const term = t('rate.term', { year: offering.academicYear, semester: offering.semesterNo })
    groups.set(term, [...(groups.get(term) ?? []), offering])
  }
  return groups
})

function status(offering: RateableOffering): string {
  if (!offering.open) {
    return offering.opensAt
      ? t('rate.opensOn', { date: d(offering.opensAt, 'short') })
      : t('rate.opensLater')
  }
  if (!offering.fingerprint) {
    return t('rate.opensLater')
  }
  if (!tokens.value.has(offering.offeringId)) {
    return t('rate.otherDevice')
  }
  return tokens.value.get(offering.offeringId) ? t('rate.submitted') : t('rate.ready')
}

async function load() {
  error.value = ''
  busy.value = true
  try {
    offerings.value = await issueTokens(userId)
    tokens.value = new Map(
      (await localTokens(userId)).map((token) => [token.offeringId, !!token.submitted]),
    )
  } catch (e) {
    retryable.value = !(e instanceof UnknownKeyError)
    error.value = retryable.value ? t('rate.issueFailed') : t('rate.unknownKey')
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  if (!(await localMaster(userId))) {
    await router.replace({ name: 'recovery' })
    return
  }
  await load()
})
</script>

<template>
  <main class="page">
    <span class="eyebrow">{{ t('rateForm.anonymous') }}</span>
    <h1 class="h1">{{ t('rate.title') }}</h1>
    <AppNotice v-if="error" kind="error">
      {{ error }}
      <button v-if="retryable" class="btn btn-ghost btn-sm ml-2" :disabled="busy" @click="load">
        {{ t('rate.retry') }}
      </button>
    </AppNotice>
    <p v-if="busy && !offerings" class="lede">{{ t('rate.loading') }}</p>
    <template v-if="offerings">
      <p v-if="!offerings.length" class="lede">
        {{ t('rate.noGroup') }}
        <RouterLink :to="{ name: 'group' }">{{ t('nav.groups') }}</RouterLink>
      </p>
      <p v-else class="lede">{{ t('rate.intro') }}</p>
      <section v-for="[term, items] in terms" :key="term" class="mt-8">
        <h2 class="eyebrow mb-3">{{ term }}</h2>
        <article
          v-for="offering in items"
          :key="offering.offeringId"
          class="card mt-2 flex flex-wrap items-center justify-between gap-3"
        >
          <div class="min-w-0 flex-1">
            <strong class="block font-semibold">{{ offering.title }}</strong>
            <span
              class="mt-1 block text-sm"
              :class="tokens.get(offering.offeringId) ? 'text-mask' : 'text-muted'"
            >
              {{ status(offering) }}
            </span>
          </div>
          <RouterLink
            v-if="tokens.has(offering.offeringId)"
            :to="`/rate/${offering.offeringId}`"
            class="btn"
            :class="tokens.get(offering.offeringId) ? 'btn-outline' : 'btn-primary'"
          >
            {{ tokens.get(offering.offeringId) ? t('rateForm.edit') : t('rate.action') }}
          </RouterLink>
        </article>
      </section>
      <p class="mt-8 text-sm text-muted">
        {{ t('rate.pastYears') }}
        <RouterLink :to="{ name: 'group' }">{{ t('rate.pastYearsLink') }}</RouterLink>
      </p>
      <p class="mt-2 text-sm">
        <RouterLink :to="{ name: 'recovery' }">{{ t('recovery.title') }}</RouterLink>
      </p>
    </template>
  </main>
</template>
