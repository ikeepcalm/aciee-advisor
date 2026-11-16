<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminShell from '@/components/AdminShell.vue'
import AppNotice from '@/components/AppNotice.vue'
import { api, describeError } from '@/api'

interface Entry {
  id: number
  createdAt: string
  actor: string | null
  tableName: string
  rowId: number
  op: string
  old: string | null
  current: string | null
}

const { t, d } = useI18n()
const table = ref('')
const rowId = ref('')
const entries = ref<Entry[]>([])
const more = ref(false)
const error = ref('')
const loaded = ref(false)

async function load(append = false) {
  error.value = ''
  const query = new URLSearchParams()
  if (table.value) query.set('table', table.value)
  if (rowId.value) query.set('rowId', rowId.value)
  if (append && entries.value.length) query.set('before', String(entries.value.at(-1)!.id))
  try {
    const page = await api<Entry[]>('GET', `/api/admin/audit?${query}`)
    entries.value = append ? [...entries.value, ...page] : page
    more.value = page.length === 50
    loaded.value = true
  } catch (e) {
    error.value = describeError(t, e)
  }
}

onMounted(() => load())
</script>

<template>
  <AdminShell :title="t('admin.sections.history')">
    <form class="mt-4 flex flex-wrap gap-2" @submit.prevent="load()">
      <input
        v-model.trim="table"
        class="input basis-full sm:basis-0 sm:flex-1"
        :placeholder="t('admin.historyTable')"
        :aria-label="t('admin.historyTable')"
      />
      <input
        v-model.trim="rowId"
        class="input min-w-0 flex-1 sm:w-28 sm:flex-none"
        :placeholder="t('admin.historyRow')"
        :aria-label="t('admin.historyRow')"
        inputmode="numeric"
      />
      <button type="submit" class="btn btn-primary">{{ t('admin.search') }}</button>
    </form>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <p v-if="loaded && !entries.length" class="mt-6 text-sm text-muted">
      {{ t('common.noResults') }}
    </p>
    <details
      v-for="e in entries"
      :key="e.id"
      class="border-t border-line text-sm first-of-type:mt-4 open:pb-3"
    >
      <summary class="flex min-h-11 cursor-pointer flex-wrap items-center gap-x-2 py-2 text-xs">
        <span class="tag bg-board text-muted">{{ e.op }}</span>
        <strong class="mono [overflow-wrap:anywhere]">{{ e.tableName }} #{{ e.rowId }}</strong>
        <span class="ml-auto text-muted [overflow-wrap:anywhere]">
          {{ d(new Date(e.createdAt), 'short') }} · {{ e.actor ?? t('admin.system') }}
        </span>
      </summary>
      <pre
        v-if="e.old"
        class="mono mt-1 rounded-md bg-board p-2 text-xs break-all whitespace-pre-wrap text-muted"
        >{{ e.old }}</pre>
      <pre
        v-if="e.current"
        class="mono mt-2 rounded-md bg-board p-2 text-xs break-all whitespace-pre-wrap"
        >{{ e.current }}</pre>
    </details>
    <button v-if="more" class="btn btn-outline mt-4 w-full" @click="load(true)">
      {{ t('admin.more') }}
    </button>
  </AdminShell>
</template>
