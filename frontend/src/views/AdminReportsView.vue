<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Message from 'primevue/message'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import AdminSections from '@/components/AdminSections.vue'
import { api, ApiError, describeError, lookups, type Option } from '@/api'

interface ReportTable {
  title: string
  columns: string[]
  rows: (string | number | null)[][]
}

const { t } = useI18n()
const type = ref('radar')
const specialization = ref<string | null>(null)
const department = ref<string | null>(null)
const locked = ref(false)
const filters = ref<{ specializations: Option[]; departments: Option[] }>({ specializations: [], departments: [] })
const threshold = ref(5)
const table = ref<ReportTable | null>(null)
const error = ref('')
const loading = ref(false)

const types = computed(() => ['radar', 'survival'].map((value) => ({ value, label: t(`reports.types.${value}`) })))
const nColumn = computed(() => table.value?.columns.indexOf('n') ?? -1)

function query() {
  const params = new URLSearchParams({ locked: String(locked.value) })
  if (specialization.value) params.set('specialization', specialization.value)
  if (department.value && type.value === 'radar') params.set('department', department.value)
  return params
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    table.value = await api<ReportTable>('GET', `/api/reports/${type.value}?${query()}`)
  } catch (e) {
    error.value = describeError(t, e)
  } finally {
    loading.value = false
  }
}

async function download(format: string) {
  error.value = ''
  const response = await fetch(`/api/reports/${type.value}/${format}?${query()}`)
  if (!response.ok) {
    error.value = describeError(t, new ApiError(response.status, await response.json().catch(() => ({}))))
    return
  }
  const link = document.createElement('a')
  link.href = URL.createObjectURL(await response.blob())
  link.download = `aciee-${type.value}.${format}`
  link.click()
  URL.revokeObjectURL(link.href)
}

function cell(value: string | number | null, column: number) {
  if (value === null) return '–'
  return column === nColumn.value && Number(value) < threshold.value ? `${value} · ${t('reports.locked')}` : value
}

watch([type, specialization, department, locked], load)

onMounted(async () => {
  try {
    filters.value = await api('GET', '/api/reports/filters')
    threshold.value = (await lookups()).threshold
  } catch (e) {
    error.value = describeError(t, e)
  }
  await load()
})
</script>

<template>
  <main class="admin">
    <h1>{{ t('admin.sections.reports') }}</h1>
    <AdminSections />
    <form class="filters" @submit.prevent>
      <SelectButton v-model="type" :options="types" option-value="value" option-label="label" :allow-empty="false" />
      <label for="report-specialization">{{ t('reports.specialization') }}</label>
      <Select
        v-model="specialization"
        input-id="report-specialization"
        :options="filters.specializations"
        option-value="code"
        option-label="label"
        :placeholder="t('reports.all')"
        show-clear
        fluid
      />
      <template v-if="type === 'radar'">
        <label for="report-department">{{ t('reports.department') }}</label>
        <Select
          v-model="department"
          input-id="report-department"
          :options="filters.departments"
          option-value="code"
          option-label="label"
          :placeholder="t('reports.all')"
          show-clear
          fluid
        />
      </template>
      <div class="check">
        <Checkbox v-model="locked" input-id="report-locked" binary />
        <label for="report-locked">{{ t('reports.includeLocked', { k: threshold }) }}</label>
      </div>
    </form>
    <Message v-if="error" severity="error">{{ error }}</Message>
    <div class="downloads">
      <Button :label="t('reports.csv')" outlined @click="download('csv')" />
      <Button :label="t('reports.xlsx')" outlined @click="download('xlsx')" />
      <Button :label="t('reports.pdf')" outlined @click="download('pdf')" />
    </div>
    <template v-if="table && !loading">
      <h2>{{ t('reports.preview', { n: table.rows.length }) }}</h2>
      <p v-if="!table.rows.length">{{ t('reports.empty') }}</p>
      <div v-else class="scroll">
        <table>
          <thead>
            <tr>
              <th v-for="column in table.columns" :key="column">{{ column }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, r) in table.rows.slice(0, 50)" :key="r">
              <td v-for="(value, c) in row" :key="c">{{ cell(value, c) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="note">{{ t('reports.note') }}</p>
    </template>
  </main>
</template>

<style scoped>
.filters {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 1rem 0;
}
.check {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.downloads {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 1rem 0;
}
.scroll {
  overflow-x: auto;
}
table {
  border-collapse: collapse;
  font-size: 0.8rem;
}
th,
td {
  border-bottom: 1px solid var(--p-content-border-color);
  padding: 0.25rem 0.5rem;
  text-align: left;
  vertical-align: top;
}
th {
  min-width: 6rem;
}
.note {
  font-size: 0.8rem;
  opacity: 0.8;
}
</style>
