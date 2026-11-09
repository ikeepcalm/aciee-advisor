<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ChevronRight } from 'lucide-vue-next'
import { api, describeError } from '@/api'
import AppNotice from '@/components/AppNotice.vue'
import SegmentedChips from '@/components/SegmentedChips.vue'

interface Plan {
  id: number
  intakeFrom: number
  intakeTo: number | null
}

interface Specialization {
  id: number
  code: string
  name: string
  degree: string
  durationYears: number
  plans: Plan[]
}

interface Item {
  id: number
  subjectName: string
  semesterNo: number
  electiveSlot: number | null
  lectureHours: number
  seminarHours: number
  labHours: number
  projectHours: number
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const specializations = ref<Specialization[]>([])
const items = ref<Item[] | null>(null)
const error = ref('')
const spec = ref<string | null>(null)
const planId = ref<number | null>(null)
const year = ref(1)
let ready = false

const current = computed(() => specializations.value.find((s) => s.code === spec.value))
const plans = computed(() =>
  (current.value?.plans ?? []).map((p) => ({
    id: p.id,
    label: p.intakeTo
      ? t('catalog.intakeRange', { from: p.intakeFrom, to: p.intakeTo - 1 })
      : t('catalog.intakeFrom', { from: p.intakeFrom }),
  })),
)
const years = computed(() =>
  Array.from({ length: current.value?.durationYears ?? 4 }, (_, i) => ({
    value: i + 1,
    label: ['I', 'II', 'III', 'IV', 'V', 'VI'][i]!,
  })),
)

const sections = computed(() => {
  const groups = new Map<string, Item[]>()
  for (const item of items.value ?? []) {
    const key = item.electiveSlot
      ? t('catalog.elective', { semester: item.semesterNo, slot: item.electiveSlot })
      : t('catalog.semester', { n: item.semesterNo })
    groups.set(key, [...(groups.get(key) ?? []), item])
  }
  return groups
})

function hours(item: Item) {
  return [
    ['C', item.lectureHours, t('catalog.lecture')],
    ['S', item.seminarHours, t('catalog.seminar')],
    ['L', item.labHours, t('catalog.lab')],
    ['P', item.projectHours, t('catalog.project')],
  ].filter(([, h]) => Number(h) > 0)
}

function defaultPlan(s: Specialization) {
  const intake = new Date().getFullYear() - year.value + (new Date().getMonth() >= 9 ? 1 : 0)
  return (
    (
      s.plans.find((p) => p.intakeFrom <= intake && (p.intakeTo === null || intake < p.intakeTo)) ??
      s.plans[0]
    )?.id ?? null
  )
}

async function loadItems() {
  error.value = ''
  if (!planId.value) {
    items.value = []
    return
  }
  try {
    items.value = await api<Item[]>('GET', `/api/catalog/plans/${planId.value}/years/${year.value}`)
  } catch (e) {
    error.value = describeError(t, e)
  }
}

onMounted(async () => {
  try {
    specializations.value = await api<Specialization[]>('GET', '/api/catalog/specializations')
  } catch (e) {
    error.value = describeError(t, e)
    return
  }
  year.value = Number(route.query.year) || 1
  spec.value = (route.query.spec as string) ?? specializations.value[0]?.code ?? null
  planId.value = Number(route.query.plan) || (current.value ? defaultPlan(current.value) : null)
  await loadItems()
  ready = true
})

function changeSpec() {
  planId.value = current.value ? defaultPlan(current.value) : null
}

watch([spec, planId, year], async ([s, p, y]) => {
  if (!ready) {
    return
  }
  await router.replace({ query: { spec: s ?? undefined, plan: p ?? undefined, year: y } })
  await loadItems()
})
</script>

<template>
  <main class="page">
    <header>
      <span class="eyebrow">{{ t('nav.courses') }}</span>
      <h1 class="h1">{{ t('catalog.title') }}</h1>
      <p class="lede">{{ t('catalog.tagline') }}</p>
    </header>

    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>

    <section class="card mt-6 grid gap-4 md:grid-cols-2">
      <label class="field">
        {{ t('catalog.specialization') }}
        <select v-model="spec" class="input" @change="changeSpec">
          <option v-for="s in specializations" :key="s.code" :value="s.code">{{ s.name }}</option>
        </select>
      </label>
      <label v-if="plans.length" class="field">
        {{ t('catalog.plan') }}
        <select v-model="planId" class="input">
          <option v-for="p in plans" :key="p.id" :value="p.id">{{ p.label }}</option>
        </select>
      </label>
      <div class="field md:col-span-2">
        {{ t('timetable.pickYear') }}
        <SegmentedChips v-model="year" :options="years" class="display text-lg" />
      </div>
    </section>

    <AppNotice v-if="current && !current.plans.length">{{ t('catalog.noPlan') }}</AppNotice>
    <p v-else-if="items && !items.length" class="mt-8 text-center text-muted">
      {{ t('catalog.noItems') }}
    </p>

    <section v-for="[title, group] in sections" :key="title" class="mt-8">
      <h2 class="eyebrow mb-3">{{ title }}</h2>
      <ul class="m-0 grid list-none gap-2 p-0">
        <li v-for="item in group" :key="item.id">
          <RouterLink :to="`/courses/${item.id}`" class="row-link">
            <span class="min-w-0 flex-1">
              <strong class="block font-semibold text-balance">{{ item.subjectName }}</strong>
              <span class="mt-1.5 flex flex-wrap gap-x-3 gap-y-1">
                <span
                  v-for="[k, h, label] in hours(item)"
                  :key="k"
                  class="kind-tag"
                  :class="`k-${k}`"
                >
                  {{ label }} {{ h }}h
                </span>
              </span>
            </span>
            <ChevronRight class="size-4 shrink-0 text-muted" aria-hidden="true" />
          </RouterLink>
        </li>
      </ul>
    </section>
  </main>
</template>
