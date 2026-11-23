<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDown, ChevronUp, Pencil, UserPlus } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import AppSheet from '@/components/AppSheet.vue'
import ComboSelect from '@/components/ComboSelect.vue'
import SegmentedChips from '@/components/SegmentedChips.vue'
import { api, describeError } from '@/api'
import {
  partIsSet,
  slotKey,
  teachingCourses,
  useRun,
  type TeachingPart,
  type TeachingSlot,
} from '@/group'

interface ProfessorOption {
  id: number
  label: string
  fullName: string
  renamable: boolean
}
interface Teaching {
  slots: TeachingSlot[]
  professors: ProfessorOption[]
  titles: { code: string; label: string }[]
}

const props = defineProps<{ groupId: number }>()
const { t } = useI18n()
const { error, notice, run } = useRun()
const teaching = ref<Teaching>({ slots: [], professors: [], titles: [] })
const saved = ref<Record<string, number | null>>({})
const filter = ref<'missing' | 'all'>('all')
const expanded = ref(new Set<string>())
const nameForm = ref<{
  slot: TeachingSlot | null
  professorId: number | null
  fullName: string
  titleCode: string
} | null>(null)
const nameError = ref('')
const nameSaving = ref(false)
const namesVersion = ref(0)
const kinds: Record<string, string> = { lecture: 'k-C', seminar: 'k-S', lab: 'k-L', project: 'k-P' }

const professorOptions = computed(() =>
  teaching.value.professors.map((p) => ({ value: p.id, label: p.label })),
)
const renamable = computed(
  () => new Set(teaching.value.professors.filter((p) => p.renamable).map((p) => p.id)),
)
const nameFormOpen = computed({
  get: () => nameForm.value !== null,
  set: () => (nameForm.value = null),
})
const courses = computed(() => teachingCourses(teaching.value.slots))
const parts = computed(() => courses.value.flatMap((course) => course.parts))
const missing = computed(() => parts.value.filter((part) => !partIsSet(part)).length)
const unsaved = computed(
  () => teaching.value.slots.filter((slot) => savedProfessor(slot) !== slot.professorId).length,
)
const visible = computed(() =>
  courses.value
    .map((course) => ({
      ...course,
      parts: filter.value === 'missing' ? course.parts.filter(wasMissing) : course.parts,
    }))
    .filter((course) => course.parts.length),
)

function savedProfessor(slot: TeachingSlot) {
  return saved.value[slotKey(slot)] ?? null
}

function wasMissing(part: TeachingPart) {
  return !partIsSet(part, savedProfessor)
}

function shownSlots(part: TeachingPart) {
  if (!expanded.value.has(part.key)) {
    return [part.whole]
  }
  return part.whole.professorId === null ? part.subgroups : [part.whole, ...part.subgroups]
}

function canToggle(part: TeachingPart) {
  return (
    part.subgroups.length > 0 &&
    (!expanded.value.has(part.key) || part.subgroups.every((slot) => slot.professorId === null))
  )
}

function toggleSubgroups(part: TeachingPart) {
  if (!expanded.value.delete(part.key)) {
    expanded.value.add(part.key)
  }
}

async function load() {
  teaching.value = await api<Teaching>('GET', `/api/groups/${props.groupId}/teaching`)
  saved.value = Object.fromEntries(
    teaching.value.slots.map((slot) => [slotKey(slot), slot.professorId]),
  )
  for (const part of parts.value) {
    if (part.subgroups.some((slot) => slot.professorId !== null)) {
      expanded.value.add(part.key)
    }
  }
  if (!parts.value.some(wasMissing)) {
    filter.value = 'all'
  }
}

function save() {
  return run(async () => {
    await api('PUT', `/api/groups/${props.groupId}/teaching`, teaching.value.slots)
    await load()
  }, t('group.teachingSaved'))
}

function openNameForm(slot: TeachingSlot | null, professorId: number | null) {
  const titles = teaching.value.titles
  nameError.value = ''
  nameForm.value = {
    slot,
    professorId,
    fullName: teaching.value.professors.find((p) => p.id === professorId)?.fullName ?? '',
    titleCode: (titles.find((title) => title.code === 'cadru') ?? titles[0])?.code ?? '',
  }
}

async function saveName() {
  const form = nameForm.value!
  nameError.value = ''
  nameSaving.value = true
  try {
    const path = `/api/groups/${props.groupId}/professors`
    const savedName = await api<ProfessorOption>(
      form.professorId ? 'PATCH' : 'POST',
      form.professorId ? `${path}/${form.professorId}` : path,
      { fullName: form.fullName, titleCode: form.titleCode },
    )
    const fresh = await api<Teaching>('GET', `/api/groups/${props.groupId}/teaching`)
    teaching.value = { ...fresh, slots: teaching.value.slots }
    if (form.slot) {
      form.slot.professorId = savedName.id
    }
    namesVersion.value++
    nameForm.value = null
  } catch (e) {
    nameError.value = describeError(t, e)
  } finally {
    nameSaving.value = false
  }
}

onMounted(() =>
  run(async () => {
    await load()
    filter.value = missing.value ? 'missing' : 'all'
  }),
)
</script>

<template>
  <p v-if="!teaching.slots.length" class="text-sm text-muted">
    {{ error || t('group.noTeaching') }}
  </p>
  <template v-else>
    <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
      <p class="display text-2xl" role="status">
        {{ t('group.teachingSet', { set: parts.length - missing, total: parts.length }) }}
        <span v-if="missing" class="hl ml-1 whitespace-nowrap">{{
          t('group.teachingMissing', { n: missing })
        }}</span>
      </p>
      <SegmentedChips
        v-model="filter"
        size="sm"
        :aria-label="t('group.tabs.teaching')"
        :options="[
          { value: 'missing', label: t('group.filter.missing') },
          { value: 'all', label: t('group.filter.all') },
        ]"
      />
    </div>
    <p class="quote mt-4 max-w-prose text-sm text-muted">{{ t('group.teachingAuto') }}</p>
    <button type="button" class="btn btn-ghost btn-sm mt-1 px-2" @click="openNameForm(null, null)">
      <UserPlus class="size-4" aria-hidden="true" />
      {{ t('group.addProfessorTitle') }}
    </button>

    <p v-if="!visible.length" class="mt-4 text-sm text-muted">{{ t('group.nothingMissing') }}</p>
    <template v-for="(course, index) in visible" :key="course.offeringId">
      <h2 v-if="course.semesterNo !== visible[index - 1]?.semesterNo" class="eyebrow mt-6 mb-0">
        {{ t('catalog.semester', { n: course.semesterNo }) }}
      </h2>
      <article class="card mt-3">
        <h3 class="h2 text-lg [overflow-wrap:anywhere]">{{ course.title }}</h3>
        <div
          v-for="part in course.parts"
          :key="part.key"
          class="mt-3 grid gap-2 border-t border-dashed border-line pt-3 sm:grid-cols-[8rem_1fr]"
          :class="kinds[part.component]"
        >
          <p class="flex flex-wrap items-center gap-2 self-start sm:min-h-11">
            <span class="kind-tag text-xs">{{ t(`group.component.${part.component}`) }}</span>
            <span v-if="!partIsSet(part)" class="tag bg-mark text-on-mark">{{
              t('group.missing')
            }}</span>
          </p>
          <div class="grid min-w-0 gap-2">
            <div v-for="slot in shownSlots(part)" :key="slotKey(slot)" class="field">
              <label
                :for="`teacher-${slotKey(slot)}`"
                class="font-normal text-muted"
                :class="{ 'sr-only': !part.subgroups.length }"
              >
                {{
                  slot.subgroup ? `${t('group.subgroup')} ${slot.subgroup}` : t('group.wholeGroup')
                }}
              </label>
              <ComboSelect
                :id="`teacher-${slotKey(slot)}`"
                :key="namesVersion"
                v-model="slot.professorId"
                :options="professorOptions"
                :placeholder="t('group.pickProfessor')"
                clearable
              >
                <template #footer>
                  <button
                    type="button"
                    class="btn btn-ghost w-full justify-start rounded-none border-t border-line px-4"
                    @click="openNameForm(slot, null)"
                  >
                    <UserPlus class="size-4" aria-hidden="true" />
                    {{ t('group.addProfessor') }}
                  </button>
                </template>
              </ComboSelect>
              <button
                v-if="slot.professorId !== null && renamable.has(slot.professorId)"
                type="button"
                class="btn btn-ghost btn-sm -mt-1 justify-self-start px-2"
                @click="openNameForm(slot, slot.professorId)"
              >
                <Pencil class="size-3.5" aria-hidden="true" />
                {{ t('group.fixName') }}
              </button>
            </div>
            <button
              v-if="canToggle(part)"
              type="button"
              class="btn btn-ghost btn-sm -my-1 justify-self-start px-2"
              @click="toggleSubgroups(part)"
            >
              <component
                :is="expanded.has(part.key) ? ChevronUp : ChevronDown"
                class="size-4"
                aria-hidden="true"
              />
              {{ t(expanded.has(part.key) ? 'group.sameForAll' : 'group.perSubgroup') }}
            </button>
          </div>
        </div>
      </article>
    </template>

    <div v-if="unsaved || notice || error" class="card action-bar mt-6 grid gap-2">
      <AppNotice v-if="error" kind="error" class="my-0">{{ error }}</AppNotice>
      <div class="flex items-center justify-between gap-3">
        <span class="pl-2 text-sm text-muted" role="status">{{
          unsaved ? t('group.unsaved', { n: unsaved }) : notice
        }}</span>
        <button class="btn btn-primary" :disabled="!unsaved" @click="save">
          {{ t('admin.save') }}
        </button>
      </div>
    </div>
  </template>

  <AppSheet
    v-model="nameFormOpen"
    :title="t(nameForm?.professorId ? 'group.fixNameTitle' : 'group.addProfessorTitle')"
  >
    <form v-if="nameForm" class="grid gap-4" @submit.prevent="saveName">
      <p class="text-sm text-muted">
        {{ t(nameForm.professorId ? 'group.fixNameHint' : 'group.addProfessorHint') }}
      </p>
      <label class="field" for="professor-name">
        {{ t('group.fullName') }}
        <input
          id="professor-name"
          v-model.trim="nameForm.fullName"
          class="input"
          autocomplete="off"
          autocapitalize="words"
          required
          minlength="3"
          maxlength="200"
          :aria-invalid="!!nameError"
          aria-describedby="professor-name-error"
        />
      </label>
      <label v-if="!nameForm.professorId" class="field" for="professor-title">
        {{ t('group.academicTitle') }}
        <select id="professor-title" v-model="nameForm.titleCode" class="input">
          <option v-for="title in teaching.titles" :key="title.code" :value="title.code">
            {{ title.label }}
          </option>
        </select>
      </label>
      <AppNotice v-if="nameError" id="professor-name-error" kind="error" class="my-0">{{
        nameError
      }}</AppNotice>
      <button class="btn btn-primary" :disabled="nameSaving">
        {{ t(nameForm.professorId ? 'admin.save' : 'group.addProfessorTitle') }}
      </button>
    </form>
  </AppSheet>
</template>
