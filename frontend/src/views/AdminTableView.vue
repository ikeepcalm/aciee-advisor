<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import AdminSections from '@/components/AdminSections.vue'
import { api, describeError } from '@/api'

interface Column {
  name: string
  type: string
  ref: string | null
  nullable: boolean
  editable: boolean
}
interface Table {
  key: string
  columns: Column[]
}
interface Option {
  value: string
  label: string
}
type Row = Record<string, string | null>

const placeholders: Record<string, string> = {
  daterange: '[2026-10-01,2027-10-01)',
  int4range: '[2025,)',
  timestamptz: '2027-02-15 00:00+02',
}

const { t } = useI18n()
const route = useRoute()
const tables = ref<Table[]>([])
const rows = ref<Row[]>([])
const search = ref('')
const more = ref(false)
const error = ref('')
const notice = ref('')
const editing = ref<Row | null>(null)
const editingId = ref<string | null>(null)
const formError = ref('')
const options = ref<Record<string, Option[]>>({})

const key = computed(() => route.params.table as string)
const table = computed(() => tables.value.find((candidate) => candidate.key === key.value))
const editable = computed(() => table.value?.columns.filter((column) => column.editable) ?? [])

function shown(row: Row, column: Column) {
  return column.ref ? row[`${column.name}_label`] : row[column.name]
}

async function load(append = false) {
  error.value = ''
  const query = new URLSearchParams()
  if (search.value) query.set('search', search.value)
  if (append && rows.value.length) query.set('after', rows.value.at(-1)!.id!)
  try {
    if (!tables.value.length) tables.value = await api<Table[]>('GET', '/api/admin/tables')
    const page = await api<Row[]>('GET', `/api/admin/tables/${key.value}?${query}`)
    rows.value = append ? [...rows.value, ...page] : page
    more.value = page.length === 50
  } catch (e) {
    error.value = describeError(t, e)
  }
}

async function loadOptions(column: Column, filter = '') {
  const query = filter ? `?search=${encodeURIComponent(filter)}` : ''
  const found = await api<Option[]>('GET', `/api/admin/tables/${column.ref}/options${query}`)
  const selected = options.value[column.name]?.find((option) => option.value === editing.value?.[column.name])
  options.value[column.name] = selected && !found.some((option) => option.value === selected.value) ? [selected, ...found] : found
}

function open(row: Row | null) {
  formError.value = ''
  editingId.value = row?.id ?? null
  editing.value = Object.fromEntries(editable.value.map((column) => [column.name, row?.[column.name] ?? '']))
  for (const column of editable.value.filter((candidate) => candidate.ref)) {
    const selected = row?.[column.name]
    options.value[column.name] = selected ? [{ value: selected, label: row![`${column.name}_label`] ?? selected }] : []
    loadOptions(column).catch(() => {})
  }
}

async function save() {
  formError.value = ''
  try {
    if (editingId.value) {
      await api('PUT', `/api/admin/tables/${key.value}/${editingId.value}`, editing.value)
    } else {
      await api('POST', `/api/admin/tables/${key.value}`, editing.value)
    }
    editing.value = null
    await load()
  } catch (e) {
    formError.value = describeError(t, e)
  }
}

async function act(method: string, path: string, done = '') {
  error.value = ''
  notice.value = ''
  try {
    const result = await api<{ created?: number } | undefined>(method, path)
    notice.value = done && t(done, { n: result?.created ?? 0 })
    await load()
  } catch (e) {
    error.value = describeError(t, e)
  }
}

watch(key, () => {
  search.value = ''
  load()
}, { immediate: true })
</script>

<template>
  <main class="admin">
    <h1>{{ t(`admin.sections.${key}`) }}</h1>
    <AdminSections />
    <form class="filters" @submit.prevent="load()">
      <InputText v-model.trim="search" :placeholder="t('admin.search')" />
      <Button type="submit" :label="t('admin.search')" />
      <Button type="button" :label="t('admin.add')" outlined @click="open(null)" />
    </form>
    <Message v-if="error" severity="error">{{ error }}</Message>
    <Message v-if="notice" severity="success">{{ notice }}</Message>

    <div v-for="row in rows" :key="row.id!" class="card">
      <strong>{{ row.label }}</strong>
      <small v-for="column in table?.columns" :key="column.name">
        {{ t(`admin.columns.${column.name}`) }}: {{ shown(row, column) ?? '—' }}
      </small>
      <div>
        <Button :label="t('admin.edit')" size="small" text @click="open(row)" />
        <Button
          :label="t('admin.delete')"
          size="small"
          severity="danger"
          text
          @click="act('DELETE', `/api/admin/tables/${key}/${row.id}`)"
        />
        <Button
          v-if="key === 'professor' && !row.archived_at"
          :label="t('admin.archive')"
          size="small"
          severity="secondary"
          text
          @click="act('POST', `/api/admin/tables/professor/${row.id}/archive`)"
        />
        <Button
          v-if="key === 'study_group'"
          :label="t('admin.createOfferings')"
          size="small"
          severity="secondary"
          text
          @click="act('POST', `/api/admin/tables/study_group/${row.id}/offerings`, 'admin.offeringsCreated')"
        />
        <Button
          v-if="key === 'academic_year'"
          :label="t('admin.rollover')"
          size="small"
          severity="secondary"
          text
          @click="act('POST', `/api/admin/tables/academic_year/${row.id}/rollover`, 'admin.rolloverDone')"
        />
      </div>
    </div>
    <Button v-if="more" :label="t('admin.more')" text @click="load(true)" />

    <Dialog :visible="editing !== null" modal :header="t(editingId ? 'admin.edit' : 'admin.add')" @update:visible="editing = null">
      <form v-if="editing" class="form" @submit.prevent="save">
        <template v-for="column in editable" :key="column.name">
          <label :for="`field-${column.name}`">{{ t(`admin.columns.${column.name}`) }}</label>
          <Select
            v-if="column.ref"
            v-model="editing[column.name]"
            :input-id="`field-${column.name}`"
            :options="options[column.name] ?? []"
            option-value="value"
            option-label="label"
            :show-clear="column.nullable"
            filter
            auto-filter-focus
            fluid
            @filter="(event: { value: string }) => loadOptions(column, event.value)"
          />
          <InputText
            v-else
            :id="`field-${column.name}`"
            v-model="editing[column.name]"
            :placeholder="placeholders[column.type]"
            :inputmode="column.type === 'smallint' ? 'numeric' : undefined"
            :required="!column.nullable"
            fluid
          />
        </template>
        <Message v-if="formError" severity="error">{{ formError }}</Message>
        <Button type="submit" :label="t('admin.save')" />
      </form>
    </Dialog>
  </main>
</template>

<style scoped>
.filters {
  display: flex;
  gap: 0.5rem;
  margin: 1rem 0;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--p-content-border-color);
}
.form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-width: min(24rem, 80vw);
}
</style>
