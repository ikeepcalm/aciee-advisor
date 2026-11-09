<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

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
const current = computed(() =>
  route.name === 'admin-table' ? route.params.table : String(route.name).replace('admin-', ''),
)

function go(event: Event) {
  const section = (event.target as HTMLSelectElement).value
  if (['leaders', 'history', 'reports'].includes(section)) {
    router.push({ name: `admin-${section}` })
  } else {
    router.push({ name: 'admin-table', params: { table: section } })
  }
}
</script>

<template>
  <label class="field mt-5">
    {{ t('admin.section') }}
    <select id="admin-section" :value="current" class="input" @change="go">
      <option v-for="s in sections" :key="s" :value="s">{{ t(`admin.sections.${s}`) }}</option>
    </select>
  </label>
</template>
