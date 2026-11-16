<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

defineProps<{ title: string }>()

// Listed in the order a new year is set up, so the sidebar doubles as a checklist.
const groups = {
  year: [
    'academic_year',
    'semester',
    'study_group',
    'subgroup',
    'course_offering',
    'teaching_assignment',
  ],
  people: ['leaders', 'professor'],
  catalogue: [
    'faculty',
    'department',
    'specialization',
    'subject',
    'study_plan',
    'curriculum_item',
  ],
  records: ['history', 'reports'],
}
const pages = ['leaders', 'history', 'reports']

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const current = computed(() =>
  route.name === 'admin-table'
    ? String(route.params.table)
    : String(route.name).replace('admin-', ''),
)

function target(section: string) {
  return pages.includes(section)
    ? { name: `admin-${section}` }
    : { name: 'admin-table', params: { table: section } }
}
</script>

<template>
  <main class="page-wide lg:grid lg:grid-cols-[13rem_minmax(0,1fr)] lg:gap-10">
    <nav :aria-label="t('nav.admin')" class="hidden lg:block">
      <div class="sticky top-24 grid gap-6 pt-1">
        <div v-for="(sections, group) in groups" :key="group">
          <h2 class="kind-tag mb-1.5 text-muted">{{ t(`admin.groups.${group}`) }}</h2>
          <ul class="m-0 grid list-none gap-0.5 p-0">
            <li v-for="section in sections" :key="section">
              <RouterLink
                :to="target(section)"
                class="block border-l-2 py-1.5 pl-3 text-sm no-underline transition-colors"
                :class="
                  current === section
                    ? 'border-copper font-semibold text-ink'
                    : 'border-transparent text-muted hover:border-line hover:text-ink'
                "
                :aria-current="current === section ? 'page' : undefined"
              >
                {{ t(`admin.sections.${section}`) }}
              </RouterLink>
            </li>
          </ul>
        </div>
      </div>
    </nav>

    <div class="min-w-0">
      <span class="eyebrow">{{ t('nav.admin') }}</span>
      <h1 class="h1">{{ title }}</h1>
      <p class="lede text-sm">{{ t(`admin.hints.${current}`) }}</p>
      <label class="field mt-4 lg:hidden">
        {{ t('admin.section') }}
        <select
          :value="current"
          class="input"
          @change="router.push(target(($event.target as HTMLSelectElement).value))"
        >
          <optgroup
            v-for="(sections, group) in groups"
            :key="group"
            :label="t(`admin.groups.${group}`)"
          >
            <option v-for="section in sections" :key="section" :value="section">
              {{ t(`admin.sections.${section}`) }}
            </option>
          </optgroup>
        </select>
      </label>
      <slot />
    </div>
  </main>
</template>
