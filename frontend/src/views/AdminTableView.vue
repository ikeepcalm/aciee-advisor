<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AdminSections from '@/components/AdminSections.vue'
import ComboSelect from '@/components/ComboSelect.vue'
import AppNotice from '@/components/AppNotice.vue'
import AppSheet from '@/components/AppSheet.vue'
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
const open = computed({
  get: () => editing.value !== null,
  set: (v) => v || (editing.value = null),
})

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
  const selected = options.value[column.name]?.find(
    (option) => option.value === editing.value?.[column.name],
  )
  options.value[column.name] =
    selected && !found.some((option) => option.value === selected.value)
      ? [selected, ...found]
      : found
}

function openForm(row: Row | null) {
  formError.value = ''
  editingId.value = row?.id ?? null
  editing.value = Object.fromEntries(
    editable.value.map((column) => [column.name, row?.[column.name] ?? '']),
  )
  for (const column of editable.value.filter((candidate) => candidate.ref)) {
    const selected = row?.[column.name]
    options.value[column.name] = selected
      ? [{ value: selected, label: row![`${column.name}_label`] ?? selected }]
      : []
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

watch(
  key,
  () => {
    search.value = ''
    load()
  },
  { immediate: true },
)
</script>

<template>
  <main class="page">
    <span class="eyebrow">{{ t('nav.admin') }}</span>
    <h1 class="h1">{{ t(`admin.sections.${key}`) }}</h1>
    <AdminSections />
    <form class="mt-4 flex flex-wrap gap-2" @submit.prevent="load()">
      <input
        v-model.trim="search"
        class="input flex-1"
        :placeholder="t('admin.search')"
        :aria-label="t('admin.search')"
      />
      <button type="submit" class="btn btn-primary">{{ t('admin.search') }}</button>
      <button type="button" class="btn btn-outline" @click="openForm(null)">
        {{ t('admin.add') }}
      </button>
    </form>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <AppNotice v-if="notice" kind="success">{{ notice }}</AppNotice>

    <div v-for="row in rows" :key="row.id!" class="border-t border-line py-3">
      <strong class="block font-semibold">{{ row.label }}</strong>
      <dl class="mt-1 grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-xs">
        <template v-for="column in table?.columns" :key="column.name">
          <dt class="text-muted">{{ t(`admin.columns.${column.name}`) }}</dt>
          <dd class="m-0 break-all">{{ shown(row, column) ?? '—' }}</dd>
        </template>
      </dl>
      <div class="mt-2 flex flex-wrap gap-1">
        <button class="btn btn-ghost btn-sm" @click="openForm(row)">{{ t('admin.edit') }}</button>
        <button
          class="btn btn-danger btn-sm border-transparent"
          @click="act('DELETE', `/api/admin/tables/${key}/${row.id}`)"
        >
          {{ t('admin.delete') }}
        </button>
        <button
          v-if="key === 'professor' && !row.archived_at"
          class="btn btn-ghost btn-sm text-muted"
          @click="act('POST', `/api/admin/tables/professor/${row.id}/archive`)"
        >
          {{ t('admin.archive') }}
        </button>
        <button
          v-if="key === 'study_group'"
          class="btn btn-ghost btn-sm text-muted"
          @click="
            act(
              'POST',
              `/api/admin/tables/study_group/${row.id}/offerings`,
              'admin.offeringsCreated',
            )
          "
        >
          {{ t('admin.createOfferings') }}
        </button>
        <button
          v-if="key === 'academic_year'"
          class="btn btn-ghost btn-sm text-muted"
          @click="
            act('POST', `/api/admin/tables/academic_year/${row.id}/rollover`, 'admin.rolloverDone')
          "
        >
          {{ t('admin.rollover') }}
        </button>
      </div>
    </div>
    <button v-if="more" class="btn btn-ghost mt-4" @click="load(true)">
      {{ t('admin.more') }}
    </button>

    <AppSheet v-model="open" :title="t(editingId ? 'admin.edit' : 'admin.add')" side="modal">
      <form v-if="editing" class="grid gap-4" @submit.prevent="save">
        <div v-for="column in editable" :key="column.name" class="field">
          <label :for="`field-${column.name}`">{{ t(`admin.columns.${column.name}`) }}</label>
          <ComboSelect
            v-if="column.ref"
            :id="`field-${column.name}`"
            v-model="editing[column.name]"
            :options="options[column.name] ?? []"
            :clearable="column.nullable"
            remote
            @search="loadOptions(column, $event)"
          />
          <input
            v-else
            :id="`field-${column.name}`"
            v-model="editing[column.name]"
            class="input"
            :placeholder="placeholders[column.type]"
            :inputmode="column.type === 'smallint' ? 'numeric' : undefined"
            :required="!column.nullable"
          />
        </div>
        <AppNotice v-if="formError" kind="error" class="my-0">{{ formError }}</AppNotice>
        <button type="submit" class="btn btn-primary">{{ t('admin.save') }}</button>
      </form>
    </AppSheet>
  </main>
</template>
