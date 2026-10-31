<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import AdminSections from '@/components/AdminSections.vue'
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
  <main class="admin">
    <h1>{{ t('admin.sections.history') }}</h1>
    <AdminSections />
    <form class="filters" @submit.prevent="load()">
      <InputText v-model.trim="table" :placeholder="t('admin.historyTable')" />
      <InputText v-model.trim="rowId" :placeholder="t('admin.historyRow')" inputmode="numeric" />
      <Button type="submit" :label="t('admin.search')" />
    </form>
    <Message v-if="error" severity="error">{{ error }}</Message>
    <details v-for="e in entries" :key="e.id">
      <summary>
        {{ d(new Date(e.createdAt), 'short') }} · {{ e.op }} {{ e.tableName }} #{{ e.rowId }} · {{ e.actor ?? t('admin.system') }}
      </summary>
      <pre v-if="e.old">{{ e.old }}</pre>
      <pre v-if="e.current">{{ e.current }}</pre>
    </details>
    <Button v-if="more" :label="t('admin.more')" text @click="load(true)" />
  </main>
</template>

<style scoped>
.filters {
  display: flex;
  gap: 0.5rem;
  margin: 1rem 0;
}
pre {
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 0.8rem;
}
</style>
