<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminSections from '@/components/AdminSections.vue'
import AppNotice from '@/components/AppNotice.vue'
import SegmentedChips from '@/components/SegmentedChips.vue'
import { api, ApiError, describeError, lookups, type Option } from '@/api'

interface ReportTable {
  title: string
  columns: string[]
  rows: (string | number | null)[][]
}

const { t } = useI18n()
const type = ref('radar')
const specialization = ref('')
const department = ref('')
const locked = ref(false)
const filters = ref<{ specializations: Option[]; departments: Option[] }>({
  specializations: [],
  departments: [],
})
const threshold = ref(5)
const table = ref<ReportTable | null>(null)
const error = ref('')
const loading = ref(false)

const types = computed(() =>
  ['radar', 'survival'].map((value) => ({ value, label: t(`reports.types.${value}`) })),
)
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
    error.value = describeError(
      t,
      new ApiError(response.status, await response.json().catch(() => ({}))),
    )
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
  return column === nColumn.value && Number(value) < threshold.value
    ? `${value} · ${t('reports.locked')}`
    : value
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
  <main class="page-wide">
    <span class="eyebrow">{{ t('nav.admin') }}</span>
    <h1 class="h1">{{ t('admin.sections.reports') }}</h1>
    <div class="max-w-3xl">
      <AdminSections />
    </div>
    <form class="card mt-4 grid gap-4 md:grid-cols-2" @submit.prevent>
      <div class="md:col-span-2"><SegmentedChips v-model="type" :options="types" /></div>
      <label class="field" for="report-specialization">
        {{ t('reports.specialization') }}
        <select id="report-specialization" v-model="specialization" class="input">
          <option value="">{{ t('reports.all') }}</option>
          <option v-for="o in filters.specializations" :key="o.code" :value="o.code">
            {{ o.label }}
          </option>
        </select>
      </label>
      <label v-if="type === 'radar'" class="field" for="report-department">
        {{ t('reports.department') }}
        <select id="report-department" v-model="department" class="input">
          <option value="">{{ t('reports.all') }}</option>
          <option v-for="o in filters.departments" :key="o.code" :value="o.code">
            {{ o.label }}
          </option>
        </select>
      </label>
      <label class="inline-flex min-h-11 items-center gap-3 text-sm font-medium md:col-span-2">
        <input v-model="locked" type="checkbox" class="check" />
        {{ t('reports.includeLocked', { k: threshold }) }}
      </label>
    </form>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <div class="mt-4 flex flex-wrap gap-2">
      <button class="btn btn-outline" @click="download('csv')">{{ t('reports.csv') }}</button>
      <button class="btn btn-outline" @click="download('xlsx')">{{ t('reports.xlsx') }}</button>
      <button class="btn btn-outline" @click="download('pdf')">{{ t('reports.pdf') }}</button>
    </div>
    <template v-if="table && !loading">
      <h2 class="eyebrow mt-8 mb-3">{{ t('reports.preview', { n: table.rows.length }) }}</h2>
      <p v-if="!table.rows.length" class="text-sm text-muted">{{ t('reports.empty') }}</p>
      <div v-else class="overflow-x-auto rounded-lg border border-line bg-panel">
        <table class="w-full border-collapse text-xs">
          <thead>
            <tr>
              <th
                v-for="column in table.columns"
                :key="column"
                class="mono min-w-24 border-b border-line px-3 py-2 text-left font-medium text-muted uppercase"
              >
                {{ column }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, r) in table.rows.slice(0, 50)" :key="r">
              <td
                v-for="(value, c) in row"
                :key="c"
                class="border-b border-line px-3 py-1.5 align-top tabular-nums"
              >
                {{ cell(value, c) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="mt-3 text-xs text-muted">{{ t('reports.note') }}</p>
    </template>
  </main>
</template>
