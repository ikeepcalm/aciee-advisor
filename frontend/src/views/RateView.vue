<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Message from 'primevue/message'
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
  <main>
    <h1>{{ t('rate.title') }}</h1>
    <Message v-if="error" severity="error">
      {{ error }}
      <Button v-if="retryable" :label="t('rate.retry')" link :loading="busy" @click="load" />
    </Message>
    <p v-if="busy && !offerings">{{ t('rate.loading') }}</p>
    <template v-if="offerings">
      <p v-if="!offerings.length">
        {{ t('rate.noGroup') }}
        <RouterLink :to="{ name: 'group' }">{{ t('nav.groups') }}</RouterLink>
      </p>
      <p v-else>{{ t('rate.intro') }}</p>
      <section v-for="[term, items] in terms" :key="term">
        <h2>{{ term }}</h2>
        <article v-for="offering in items" :key="offering.offeringId">
          <strong>{{ offering.title }}</strong>
          <br />{{ status(offering) }}
          <Button
            v-if="tokens.has(offering.offeringId)"
            as="router-link"
            :to="`/rate/${offering.offeringId}`"
            :label="tokens.get(offering.offeringId) ? t('rateForm.edit') : t('rate.action')"
            size="small"
            class="go"
          />
        </article>
      </section>
      <p>
        {{ t('rate.pastYears') }}
        <RouterLink :to="{ name: 'group' }">{{ t('rate.pastYearsLink') }}</RouterLink>
      </p>
      <RouterLink :to="{ name: 'recovery' }">{{ t('recovery.title') }}</RouterLink>
    </template>
  </main>
</template>

<style scoped>
h2 {
  font-size: 1rem;
  margin: 1.25rem 0 0.5rem;
}

.go {
  margin-top: 0.5rem;
  display: flex;
  width: fit-content;
}

article {
  padding: 0.75rem;
  margin-bottom: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 0.5rem;
}
</style>
