<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Loader2 } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import { api, describeError } from '@/api'

interface Preview {
  group: { code: string; specialization: string; studyYear: number; academicYear: string }
  subgroups: { id: number; label: string }[]
}

const { t } = useI18n()
const token = useRoute().params.token as string
const preview = ref<Preview | null>(null)
const subgroupId = ref<number | null>(null)
const error = ref('')
const sent = ref(false)
const busy = ref(false)

onMounted(async () => {
  try {
    preview.value = await api<Preview>('GET', `/api/groups/invites/${encodeURIComponent(token)}`)
  } catch (e) {
    error.value = describeError(t, e)
  }
})

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await api('POST', '/api/groups/join', { token, subgroupId: subgroupId.value })
    sent.value = true
  } catch (e) {
    error.value = describeError(t, e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="page max-w-md">
    <span class="eyebrow">{{ t('nav.groups') }}</span>
    <h1 class="h1">{{ t('group.joinTitle') }}</h1>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <form v-if="preview && !sent" class="card mt-6 grid gap-5" @submit.prevent="submit">
      <div>
        <p class="display text-2xl">{{ preview.group.code }}</p>
        <p class="text-sm text-muted">
          {{ preview.group.specialization }} ·
          {{ t('catalog.year', { n: preview.group.studyYear }) }} ·
          <span class="mono text-xs">{{ preview.group.academicYear }}</span>
        </p>
      </div>
      <fieldset v-if="preview.subgroups.length" class="m-0 min-w-0 border-0 p-0">
        <legend class="mb-1.5 text-sm font-medium">{{ t('group.subgroup') }}</legend>
        <div class="flex flex-wrap gap-x-5 gap-y-1">
          <label
            v-for="s in preview.subgroups"
            :key="s.id"
            class="inline-flex min-h-11 items-center gap-2 text-sm"
          >
            <input v-model="subgroupId" type="radio" :value="s.id" class="check" required />
            {{ s.label }}
          </label>
        </div>
      </fieldset>
      <button type="submit" class="btn btn-primary w-full" :disabled="busy">
        <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
        {{ t('group.sendRequest') }}
      </button>
      <p class="text-center text-xs text-muted">{{ t('group.joinHint') }}</p>
    </form>
    <AppNotice v-if="sent" kind="success">{{ t('group.joinSent') }}</AppNotice>
  </main>
</template>
