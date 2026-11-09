<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminSections from '@/components/AdminSections.vue'
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
  } catch (e) {
    error.value = describeError(t, e)
  }
}

onMounted(() => load())
</script>

<template>
  <main class="page">
    <span class="eyebrow">{{ t('nav.admin') }}</span>
    <h1 class="h1">{{ t('admin.sections.history') }}</h1>
    <AdminSections />
    <form class="mt-4 flex flex-wrap gap-2" @submit.prevent="load()">
      <input
        v-model.trim="table"
        class="input flex-1"
        :placeholder="t('admin.historyTable')"
        :aria-label="t('admin.historyTable')"
      />
      <input
        v-model.trim="rowId"
        class="input w-28"
        :placeholder="t('admin.historyRow')"
        :aria-label="t('admin.historyRow')"
        inputmode="numeric"
      />
      <button type="submit" class="btn btn-primary">{{ t('admin.search') }}</button>
    </form>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <details v-for="e in entries" :key="e.id" class="border-t border-line py-2 text-sm">
      <summary class="mono cursor-pointer text-xs">
        {{ d(new Date(e.createdAt), 'short') }} · <strong>{{ e.op }}</strong> {{ e.tableName }} #{{
          e.rowId
        }}
        · {{ e.actor ?? t('admin.system') }}
      </summary>
      <pre
        v-if="e.old"
        class="mono mt-2 rounded-md bg-board p-2 text-xs break-all whitespace-pre-wrap text-muted"
        >{{ e.old }}</pre>
      <pre
        v-if="e.current"
        class="mono mt-2 rounded-md bg-board p-2 text-xs break-all whitespace-pre-wrap"
        >{{ e.current }}</pre>
    </details>
    <button v-if="more" class="btn btn-ghost mt-4" @click="load(true)">
      {{ t('admin.more') }}
    </button>
  </main>
</template>
