<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppNotice from '@/components/AppNotice.vue'
import SegmentedChips from '@/components/SegmentedChips.vue'
import { matchesGroup, type GroupSummary } from '@/group'

const props = defineProps<{ groups: GroupSummary[] }>()
const emit = defineEmits<{ claim: [groupId: number, subgroupId: number | null] }>()
const { t } = useI18n()
const intent = ref<'join' | 'lead' | null>(null)
const query = ref('')
const pickedId = ref<number | null>(null)
const subgroupId = ref<number | null>(null)
watch(pickedId, () => (subgroupId.value = null))

const picked = computed(() => props.groups.find((group) => group.id === pickedId.value))
const sections = computed(() => {
  const found = props.groups
    .filter((group) => matchesGroup(group, query.value))
    .sort(
      (a, b) =>
        a.specialization.localeCompare(b.specialization) ||
        a.studyYear - b.studyYear ||
        a.code.localeCompare(b.code),
    )
  const sections: {
    specialization: string
    years: { year: number; options: { value: number; label: string }[] }[]
  }[] = []
  for (const group of found) {
    let section = sections.at(-1)
    if (section?.specialization !== group.specialization) {
      section = { specialization: group.specialization, years: [] }
      sections.push(section)
    }
    let year = section.years.at(-1)
    if (year?.year !== group.studyYear) {
      year = { year: group.studyYear, options: [] }
      section.years.push(year)
    }
    year.options.push({
      value: group.id,
      label: group.subgroups?.length
        ? `${group.code} · ${group.subgroups.map((s) => s.label).join(', ')}`
        : group.code,
    })
  }
  return sections
})
</script>

<template>
  <section class="card mt-6 grid gap-3">
    <p class="font-semibold">{{ t('group.noGroup') }}</p>
    <SegmentedChips
      v-model="intent"
      :aria-label="t('group.title')"
      :options="[
        { value: 'join', label: t('group.intent.join') },
        { value: 'lead', label: t('group.intent.lead') },
      ]"
    />
    <p v-if="intent === 'join'" class="max-w-prose text-sm text-muted">{{ t('group.joinHow') }}</p>
  </section>

  <section v-if="intent === 'lead'" class="mt-6">
    <label class="field" for="group-search">
      {{ t('group.findGroup') }}
      <input
        id="group-search"
        v-model="query"
        type="search"
        class="input"
        autocomplete="off"
        autocapitalize="characters"
        :placeholder="t('group.findGroupExample')"
      />
    </label>
    <p v-if="!sections.length" class="mt-4 text-sm text-muted" role="status">
      {{ t('group.noGroupFound') }}
    </p>
    <div v-for="section in sections" :key="section.specialization" class="mt-6">
      <h2 class="h2 [overflow-wrap:anywhere]">{{ section.specialization }}</h2>
      <div
        v-for="year in section.years"
        :key="year.year"
        class="mt-2 grid gap-1.5 sm:grid-cols-[4.5rem_1fr] sm:items-center"
      >
        <span class="mono text-xs text-muted">{{ t('catalog.year', { n: year.year }) }}</span>
        <SegmentedChips
          v-model="pickedId"
          :options="year.options"
          :aria-label="`${section.specialization} · ${t('catalog.year', { n: year.year })}`"
        />
      </div>
    </div>
    <div v-if="picked" class="card action-bar mt-6 grid gap-2 p-3">
      <p class="text-sm [overflow-wrap:anywhere]">
        <strong class="mono">{{ picked.code }}</strong> · {{ picked.specialization }} ·
        {{ t('catalog.year', { n: picked.studyYear })
        }}<template v-if="picked.subgroups?.length">
          · {{ t('group.subgroups', { list: picked.subgroups.map((s) => s.label).join(', ') }) }}</template
        >
      </p>
      <AppNotice v-if="picked.hasLeader" kind="warn" class="my-0">{{
        t('group.hasLeader')
      }}</AppNotice>
      <template v-else>
        <div v-if="picked.subgroups?.length" class="field">
          {{ t('group.subgroup') }}
          <SegmentedChips
            v-model="subgroupId"
            :aria-label="t('group.subgroup')"
            :options="picked.subgroups.map((s) => ({ value: s.id, label: s.label }))"
          />
        </div>
        <button
          class="btn btn-primary w-full"
          :disabled="!!picked.subgroups?.length && subgroupId === null"
          @click="emit('claim', picked.id, subgroupId)"
        >
          {{ t('group.claimGroup', { code: picked.code }) }}
        </button>
        <p class="text-xs text-muted">{{ t('group.claimHint') }}</p>
      </template>
    </div>
  </section>
</template>
