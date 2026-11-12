<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import ProfessorAvatar from '@/components/ProfessorAvatar.vue'
import SegmentedChips from '@/components/SegmentedChips.vue'
import { api, describeError, type ProfessorListItem } from '@/api'
import { fold } from '@/timetable/catchUp'

type Sort = 'name' | 'best' | 'most'

const { t } = useI18n()
const professors = ref<ProfessorListItem[] | null>(null)
const error = ref('')
const query = ref('')
const sort = ref<Sort>('name')

const sorts = computed(() => [
  { value: 'name' as Sort, label: t('professors.sortName') },
  { value: 'best' as Sort, label: t('professors.sortBest') },
  { value: 'most' as Sort, label: t('professors.sortMost') },
])

const shown = computed(() => {
  const needle = fold(query.value.trim())
  const found = (professors.value ?? []).filter((p) =>
    fold(`${p.fullName} ${p.department} ${p.title}`).includes(needle),
  )
  if (sort.value === 'best') {
    return found.sort((a, b) => (b.overall ?? -1) - (a.overall ?? -1))
  }
  return sort.value === 'most' ? found.sort((a, b) => b.ratings - a.ratings) : found
})

async function load() {
  error.value = ''
  try {
    professors.value = await api<ProfessorListItem[]>('GET', '/api/catalog/professors')
  } catch (e) {
    error.value = describeError(t, e)
  }
}

onMounted(load)
</script>

<template>
  <main class="page-wide">
    <header>
      <span class="eyebrow">{{ t('nav.professors') }}</span>
      <h1 class="h1">{{ t('professors.title') }}</h1>
      <p class="lede text-sm md:text-base">{{ t('professors.tagline') }}</p>
    </header>

    <AppNotice v-if="error" kind="error">
      {{ error }}
      <button class="btn btn-outline mt-2 flex" @click="load">{{ t('common.retry') }}</button>
    </AppNotice>

    <section class="mt-5 grid gap-3 md:grid-cols-[minmax(0,1fr)_auto] md:items-center">
      <label class="relative block">
        <span class="sr-only">{{ t('professors.search') }}</span>
        <Search
          class="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted"
          aria-hidden="true"
        />
        <input
          v-model="query"
          type="search"
          class="input pl-9"
          autocomplete="off"
          :placeholder="t('professors.search')"
        />
      </label>
      <SegmentedChips
        v-model="sort"
        :options="sorts"
        size="sm"
        :aria-label="t('professors.sort')"
      />
    </section>

    <p v-if="!professors && !error" class="mt-8 text-muted" role="status">
      {{ t('common.loading') }}
    </p>
    <template v-if="professors">
      <p class="mono mt-4 text-xs text-muted" role="status">
        {{ shown.length ? t('professors.count', { n: shown.length }) : t('common.noResults') }}
      </p>
      <ul class="m-0 mt-2 grid list-none gap-2 p-0 md:grid-cols-2 xl:grid-cols-3">
        <li v-for="p in shown" :key="p.slug">
          <RouterLink :to="`/professors/${p.slug}`" class="row-link h-full">
            <ProfessorAvatar :name="p.fullName" :photo="p.photo" class="size-14 shrink-0" />
            <span class="min-w-0 flex-1">
              <strong class="block font-semibold text-balance">{{ p.fullName }}</strong>
              <span class="block text-sm text-muted">{{ p.title }}</span>
              <span class="mt-0.5 flex flex-wrap gap-x-3 text-xs text-muted">
                <span>{{ p.department }}</span>
                <span class="whitespace-nowrap">{{
                  t('professors.subjects', { n: p.subjects })
                }}</span>
              </span>
            </span>
            <span v-if="p.overall !== null" class="shrink-0 text-right">
              <span class="display block text-3xl leading-none">{{ p.overall.toFixed(1) }}</span>
              <span class="mono text-[0.625rem] text-muted">/ 10</span>
            </span>
            <span v-else class="tag shrink-0 bg-board text-muted">{{ t('professors.few') }}</span>
          </RouterLink>
        </li>
      </ul>
    </template>
  </main>
</template>
