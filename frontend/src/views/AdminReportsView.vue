<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loader2 } from 'lucide-vue-next'
import AdminShell from '@/components/AdminShell.vue'
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
  <AdminShell :title="t('admin.sections.reports')">
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
      <button
        class="btn btn-outline flex-1 px-2 text-xs whitespace-nowrap sm:flex-none sm:px-4 sm:text-sm"
        @click="download('csv')"
      >
        {{ t('reports.csv') }}
      </button>
      <button
        class="btn btn-outline flex-1 px-2 text-xs whitespace-nowrap sm:flex-none sm:px-4 sm:text-sm"
        @click="download('xlsx')"
      >
        {{ t('reports.xlsx') }}
      </button>
      <button
        class="btn btn-outline flex-1 px-2 text-xs whitespace-nowrap sm:flex-none sm:px-4 sm:text-sm"
        @click="download('pdf')"
      >
        {{ t('reports.pdf') }}
      </button>
    </div>
    <p v-if="loading" class="mt-8 flex items-center gap-2 text-sm text-muted">
      <Loader2 class="size-4 animate-spin" aria-hidden="true" /> {{ t('common.loading') }}
    </p>
    <template v-if="table && !loading">
      <h2 class="eyebrow mt-8 mb-3">{{ t('reports.preview', { n: table.rows.length }) }}</h2>
      <p v-if="!table.rows.length" class="text-sm text-muted">{{ t('reports.empty') }}</p>
      <div
        v-else
        class="max-h-[70dvh] overflow-auto overscroll-x-contain rounded-lg border border-line bg-panel [background:linear-gradient(to_right,var(--panel)_30%,transparent)_left/3rem_100%_no-repeat_local,linear-gradient(to_left,var(--panel)_30%,transparent)_right/3rem_100%_no-repeat_local,linear-gradient(to_right,color-mix(in_srgb,var(--ink)_22%,transparent),transparent)_left/0.75rem_100%_no-repeat_scroll,linear-gradient(to_left,color-mix(in_srgb,var(--ink)_22%,transparent),transparent)_right/0.75rem_100%_no-repeat_scroll,var(--panel)]"
        tabindex="0"
      >
        <table class="w-full border-collapse text-xs">
          <thead>
            <tr>
              <th
                v-for="column in table.columns"
                :key="column"
                class="mono sticky top-0 min-w-24 border-b border-line bg-board px-3 py-2 text-left font-medium text-muted uppercase"
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
  </AdminShell>
</template>
