<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import Message from 'primevue/message'
import { api, describeError } from '@/api'

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
    label: t('catalog.year', { n: i + 1 }),
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
    [item.lectureHours, t('catalog.lecture')],
    [item.seminarHours, t('catalog.seminar')],
    [item.labHours, t('catalog.lab')],
    [item.projectHours, t('catalog.project')],
  ]
    .filter(([h]) => Number(h) > 0)
    .map(([h, label]) => `${label} ${h}h`)
    .join(' · ')
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
  <main>
    <h1>{{ t('catalog.title') }}</h1>
    <p>{{ t('catalog.tagline') }}</p>
    <Message v-if="error" severity="error">{{ error }}</Message>
    <div class="filters">
      <label>
        {{ t('catalog.specialization') }}
        <Select
          v-model="spec"
          :options="specializations"
          option-label="name"
          option-value="code"
          fluid
          @change="changeSpec"
        />
      </label>
      <label v-if="plans.length">
        {{ t('catalog.plan') }}
        <Select v-model="planId" :options="plans" option-label="label" option-value="id" fluid />
      </label>
      <SelectButton
        v-model="year"
        :options="years"
        option-label="label"
        option-value="value"
        :allow-empty="false"
      />
    </div>
    <Message v-if="current && !current.plans.length" severity="info">{{
      t('catalog.noPlan')
    }}</Message>
    <p v-else-if="items && !items.length">{{ t('catalog.noItems') }}</p>
    <section v-for="[title, group] in sections" :key="title">
      <h2>{{ title }}</h2>
      <RouterLink
        v-for="item in group"
        :key="item.id"
        :to="`/courses/${item.id}`"
        class="card course"
      >
        <strong>{{ item.subjectName }}</strong>
        <span class="muted">{{ hours(item) }}</span>
      </RouterLink>
    </section>
  </main>
</template>

<style scoped>
.filters {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.filters label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

h2 {
  font-size: 1rem;
  margin: 1.25rem 0 0.5rem;
}

.course {
  display: flex;
  flex-direction: column;
  text-decoration: none;
  color: inherit;
}
</style>
