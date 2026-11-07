<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Select from 'primevue/select'

const sections = [
  'leaders',
  'faculty',
  'department',
  'specialization',
  'subject',
  'study_plan',
  'curriculum_item',
  'academic_year',
  'semester',
  'study_group',
  'subgroup',
  'course_offering',
  'professor',
  'teaching_assignment',
  'history',
  'reports',
]

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const current = computed(() => (route.name === 'admin-table' ? route.params.table : String(route.name).replace('admin-', '')))
const options = computed(() => sections.map((value) => ({ value, label: t(`admin.sections.${value}`) })))

function go(section: string) {
  if (['leaders', 'history', 'reports'].includes(section)) {
    router.push({ name: `admin-${section}` })
  } else {
    router.push({ name: 'admin-table', params: { table: section } })
  }
}
</script>

<template>
  <label for="admin-section">{{ t('admin.section') }}</label>
  <Select input-id="admin-section" :model-value="current" :options="options" option-value="value" option-label="label" fluid @update:model-value="go" />
</template>
