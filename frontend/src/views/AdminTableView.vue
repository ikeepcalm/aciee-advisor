<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AdminShell from '@/components/AdminShell.vue'
import ComboSelect from '@/components/ComboSelect.vue'
import DateRangeField from '@/components/DateRangeField.vue'
import AppNotice from '@/components/AppNotice.vue'
import AppSheet from '@/components/AppSheet.vue'
import { Plus, Trash2 } from 'lucide-vue-next'
import { api, describeError } from '@/api'
import { parseRanges } from '@/dateRange'

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
  int4range: '[2025,)',
  timestamptz: '2027-02-15 00:00+02',
}

// The columns worth scanning in the list; the rest are one click away in the form.
const summaries: Record<string, string[]> = {
  faculty: ['code'],
  department: ['faculty_id'],
  specialization: ['code', 'degree', 'duration_years'],
  subject: ['name_en'],
  curriculum_item: ['plan_id', 'hours', 'elective_slot'],
  academic_year: ['during'],
  semester: ['during', 'breaks', 'rating_opens_at'],
  study_group: ['plan_id', 'study_year', 'max_size'],
  professor: ['title_code', 'department_id', 'archived_at'],
  teaching_assignment: ['component', 'subgroup_id', 'valid_during'],
}

const { t, locale } = useI18n()
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
const loaded = ref(false)
const confirmDelete = ref(false)

const key = computed(() => route.params.table as string)
const table = computed(() => tables.value.find((candidate) => candidate.key === key.value))
const editable = computed(() => table.value?.columns.filter((column) => column.editable) ?? [])
const open = computed({
  get: () => editing.value !== null,
  set: (v) => v || (editing.value = null),
})

const summary = computed(() => summaries[key.value] ?? [])

function day(date: string) {
  return new Date(`${date}T00:00:00Z`).toLocaleDateString(locale.value, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

function display(row: Row, name: string) {
  if (name === 'hours') {
    return ['lecture_hours', 'seminar_hours', 'lab_hours', 'project_hours']
      .map((hours) => row[hours] ?? 0)
      .join(' / ')
  }
  const column = table.value?.columns.find((candidate) => candidate.name === name)
  const value = column?.ref ? row[`${name}_label`] : row[name]
  if (!value) return '—'
  if (column?.type === 'daterange' || column?.type === 'datemultirange') {
    return parseRanges(value)
      .map(({ from, to }) => `${from ? day(from) : '…'} – ${to ? day(to) : '…'}`)
      .join(', ')
  }
  if (column?.type === 'timestamptz') {
    return new Date(value.replace(' ', 'T').replace(/([+-]\d\d)$/, '$1:00')).toLocaleString(
      locale.value,
      { dateStyle: 'medium', timeStyle: 'short' },
    )
  }
  return value
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
    loaded.value = true
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
  confirmDelete.value = false
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

async function remove() {
  if (!confirmDelete.value) {
    confirmDelete.value = true
    return
  }
  try {
    await api('DELETE', `/api/admin/tables/${key.value}/${editingId.value}`)
    editing.value = null
    await load()
  } catch (e) {
    formError.value = describeError(t, e)
    confirmDelete.value = false
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

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(search, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => load(), 300)
})
</script>

<template>
  <AdminShell :title="t(`admin.sections.${key}`)">
    <div class="mt-5 flex flex-wrap items-center gap-2">
      <input
        v-model.trim="search"
        type="search"
        class="input min-w-40 flex-1"
        :placeholder="t('admin.search')"
        :aria-label="t('admin.search')"
      />
      <button type="button" class="btn btn-primary" @click="openForm(null)">
        <Plus class="size-4" aria-hidden="true" />
        {{ t('admin.add') }}
      </button>
    </div>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <AppNotice v-if="notice" kind="success">{{ notice }}</AppNotice>

    <p v-if="loaded && !rows.length" class="mt-6 text-sm text-muted">
      {{ t(search ? 'common.noResults' : 'admin.empty') }}
    </p>
    <table v-else-if="rows.length" class="mt-4 w-full border-collapse text-sm">
      <thead>
        <tr class="border-b-[1.5px] border-line text-left text-xs text-muted">
          <th class="py-2 pr-3 font-medium">{{ t('admin.name') }}</th>
          <th
            v-for="column in summary"
            :key="column"
            class="hidden py-2 pr-3 font-medium md:table-cell"
          >
            {{ t(`admin.columns.${column}`) }}
          </th>
          <th class="py-2">
            <span class="sr-only">{{ t('admin.actions') }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in rows"
          :key="row.id!"
          class="cursor-pointer border-b border-line/60 align-top transition-colors hover:bg-panel"
          @click="openForm(row)"
        >
          <td class="py-2.5 pr-3">
            <button
              type="button"
              class="cursor-pointer text-left font-semibold [overflow-wrap:anywhere] hover:text-mask"
              @click.stop="openForm(row)"
            >
              {{ row.label }}
            </button>
            <span
              v-if="summary.length"
              class="mt-0.5 block text-xs text-muted [overflow-wrap:anywhere] md:hidden"
            >
              {{ summary.map((column) => display(row, column)).join(' · ') }}
            </span>
          </td>
          <td
            v-for="column in summary"
            :key="column"
            class="hidden py-2.5 pr-3 [overflow-wrap:anywhere] md:table-cell"
            :class="column === 'hours' && 'mono'"
          >
            {{ display(row, column) }}
          </td>
          <td class="py-1.5 text-right whitespace-nowrap">
            <button
              v-if="key === 'study_group'"
              class="btn btn-ghost btn-sm"
              @click.stop="
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
              class="btn btn-ghost btn-sm"
              @click.stop="
                act(
                  'POST',
                  `/api/admin/tables/academic_year/${row.id}/rollover`,
                  'admin.rolloverDone',
                )
              "
            >
              {{ t('admin.rollover') }}
            </button>
            <button
              v-if="key === 'professor' && !row.archived_at"
              class="btn btn-ghost btn-sm text-muted"
              @click.stop="act('POST', `/api/admin/tables/professor/${row.id}/archive`)"
            >
              {{ t('admin.archive') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <button v-if="more" class="btn btn-outline mt-4 w-full" @click="load(true)">
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
          <DateRangeField
            v-else-if="column.type === 'daterange' || column.type === 'datemultirange'"
            :id="`field-${column.name}`"
            :key="`${editingId}-${column.name}`"
            v-model="editing[column.name]"
            :multiple="column.type === 'datemultirange'"
            :required="!column.nullable"
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
        <div class="flex flex-wrap gap-2">
          <button type="submit" class="btn btn-primary flex-1">{{ t('admin.save') }}</button>
          <button
            v-if="editingId"
            type="button"
            class="btn btn-danger"
            :class="confirmDelete && 'bg-danger text-white'"
            @click="remove"
          >
            <Trash2 class="size-4" aria-hidden="true" />
            {{ t(confirmDelete ? 'admin.deleteConfirm' : 'admin.delete') }}
          </button>
        </div>
      </form>
    </AppSheet>
  </AdminShell>
</template>
