<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  useId,
  useTemplateRef,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, ArrowRight, Check, ListMinus, ListPlus, Loader2 } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import NoteStar from '@/components/NoteStar.vue'
import SegmentedChips from '@/components/SegmentedChips.vue'
import { anonApi, ApiError, describeError, lookups, type Lookups } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { issueTokens, localMaster, markSubmitted, tokenFor } from '@/crypto/tokens'
import { rateDraftPrefix, readStored, removeStored, writeStored } from '@/storage'

interface Assignment {
  id: number
  componentLabel: string
  professor: string
  title: string
  subgroup: string | null
}

interface OfferingForm {
  title: string
  academicYear: string
  semesterNo: number
  assignments: Assignment[]
}

type TeacherAnswer = 'yes' | 'no' | null

interface Draft {
  survival: Partial<typeof survival>
  scores: Record<number, (number | null)[]>
  comments: Record<number, string>
  hadTeacher: Record<number, TeacherAnswer>
  allCriteria?: boolean
  tip: string
  step: number
  part: number
}

interface Existing {
  examDifficulty: number | null
  labsHelp: number | null
  attendanceStrictness: number | null
  attendanceUseful: string | null
  exam: string[]
  weeklyHours: number | null
  ratings: { teachingAssignmentId: number; scores: (number | null)[] }[]
  posts: { kind: string; teachingAssignmentId: number | null; body: string }[]
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const offeringId = Number(route.params.offeringId)
const userId = useAuthStore().user!.id
const form = ref<OfferingForm | null>(null)
const labels = ref<Lookups | null>(null)
const token = ref<Awaited<ReturnType<typeof tokenFor>>>(null)
const editing = ref(false)
const state = ref<'loading' | 'ready' | 'noToken' | 'saved'>('loading')
const error = ref('')
const duplicate = ref(false)
const busy = ref(false)

const survival = reactive({
  examDifficulty: null as number | null,
  labsHelp: null as number | null,
  attendanceStrictness: null as number | null,
  attendanceUseful: null as string | null,
  exam: [] as string[],
  weeklyHours: null as number | null,
})
const scores = reactive<Record<number, (number | null)[]>>({})
const comments = reactive<Record<number, string>>({})
const hadTeacher = reactive<Record<number, TeacherAnswer>>({})
const tip = ref('')
const step = ref(0)
const part = ref(0)
const draftRestored = ref(false)
const quickAxisCodes = ['a04', 'a07', 'a10', 'a14']
const prefersAllCriteria = ref(false)
const criteriaChoice = reactive<Record<number, boolean>>({})
const wideScreen = matchMedia('(min-width: 64rem)')
const showsAllPillars = ref(wideScreen.matches)
function syncPillarLayout() {
  showsAllPillars.value = wideScreen.matches
  part.value = Math.min(part.value, partCount(step.value) - 1)
}
wideScreen.addEventListener('change', syncPillarLayout)
onBeforeUnmount(() => wideScreen.removeEventListener('change', syncPillarLayout))
const draftKey = `${rateDraftPrefix}${userId}:${offeringId}`
const fieldId = useId()
const stepNav = useTemplateRef<HTMLElement>('stepNav')
const stepPanel = useTemplateRef<HTMLElement>('stepPanel')
const scale = Array.from({ length: 11 }, (_, i) => ({ value: i, label: String(i) }))
const rowClass =
  'grid gap-1.5 lg:grid-cols-[16rem_1fr] lg:items-center lg:gap-6 xl:grid-cols-[19rem_1fr]'
const hintedLabel =
  'flex flex-wrap items-baseline justify-between gap-x-3 text-sm font-medium lg:flex-col lg:justify-start'
const yesNo = computed(() => [
  { value: 'yes' as const, label: t('rateForm.yes') },
  { value: 'no' as const, label: t('rateForm.no') },
])
const attendanceOptions = computed(() =>
  (labels.value?.attendance ?? []).map((o) => ({ value: o.code, label: o.label })),
)
const examGroups = computed(() =>
  (['form', 'content'] as const).map((kind) => ({
    kind,
    label: t(kind === 'form' ? 'rateForm.examForm' : 'rateForm.examContent'),
    options: (labels.value?.examOptions ?? []).filter((o) => o.group === kind),
  })),
)

const indexedAxes = computed(() =>
  (labels.value?.axes ?? []).map((axis, index) => ({ ...axis, index })),
)

const pillars = computed(() =>
  (labels.value?.pillars ?? []).map((pillar) => ({
    ...pillar,
    axes: indexedAxes.value.filter((axis) => axis.group === pillar.code),
  })),
)

const quickAxes = computed(() =>
  indexedAxes.value.filter((axis) => quickAxisCodes.includes(axis.code)),
)

const steps = computed(() => [
  { key: 'course', label: t('rateForm.howToPass'), assignment: null },
  ...(form.value?.assignments ?? []).map((assignment) => ({
    key: `teacher-${assignment.id}`,
    label: assignment.professor,
    assignment,
  })),
  { key: 'finish', label: t('rateForm.finish'), assignment: null },
])
const teacher = computed(() => steps.value[step.value]?.assignment ?? null)
const teacherShowsAll = computed(() => !!teacher.value && showsAllCriteria(teacher.value.id))
const shownPillars = computed(() =>
  showsAllPillars.value ? pillars.value : pillars.value.slice(part.value, part.value + 1),
)
const isLastStep = computed(() => step.value === steps.value.length - 1)
const hoursUnknown = computed(() => typeof survival.weeklyHours !== 'number')
const ratings = computed(() =>
  (form.value?.assignments ?? [])
    .filter((a) => hadTeacher[a.id] === 'yes')
    .map((a) => ({
      teachingAssignmentId: a.id,
      scores: scores[a.id],
      comment: comments[a.id],
    }))
    .filter((r) => r.scores!.some((s) => s !== null) || r.comment),
)

function showsAllCriteria(assignmentId: number) {
  return (
    criteriaChoice[assignmentId] ??
    (prefersAllCriteria.value ||
      !quickAxes.value.length ||
      scores[assignmentId]!.some(
        (score, index) => score !== null && quickAxes.value.every((axis) => axis.index !== index),
      ))
  )
}

function toggleCriteria(assignmentId: number) {
  prefersAllCriteria.value = !showsAllCriteria(assignmentId)
  criteriaChoice[assignmentId] = prefersAllCriteria.value
  part.value = 0
}

function partCount(stepIndex: number) {
  const assignment = steps.value[stepIndex]?.assignment
  return assignment &&
    hadTeacher[assignment.id] === 'yes' &&
    showsAllCriteria(assignment.id) &&
    !showsAllPillars.value
    ? Math.max(pillars.value.length, 1)
    : 1
}

function isStepDone(stepIndex: number) {
  const assignment = steps.value[stepIndex]?.assignment
  return assignment ? hadTeacher[assignment.id] !== null : stepIndex < step.value
}

function scoredCount(assignmentId: number) {
  const shown = showsAllCriteria(assignmentId)
    ? scores[assignmentId]!
    : quickAxes.value.map((axis) => scores[assignmentId]![axis.index])
  return shown.filter((s) => s !== null).length
}

async function goTo(stepIndex: number, partIndex = 0) {
  step.value = stepIndex
  part.value = partIndex
  await nextTick()
  stepNav.value?.scrollIntoView({ block: 'start' })
  stepPanel.value?.focus({ preventScroll: true })
}

function goNext() {
  if (part.value < partCount(step.value) - 1) {
    goTo(step.value, part.value + 1)
  } else if (!isLastStep.value) {
    goTo(step.value + 1)
  }
}

function goBack() {
  if (part.value > 0) {
    goTo(step.value, part.value - 1)
  } else if (step.value > 0) {
    goTo(step.value - 1, partCount(step.value - 1) - 1)
  }
}

function restoreDraft() {
  try {
    const draft = JSON.parse(readStored(draftKey) ?? 'null') as Draft | null
    if (!draft) {
      return
    }
    Object.assign(survival, draft.survival)
    for (const a of form.value!.assignments) {
      if (draft.scores[a.id]?.length === 14) {
        scores[a.id] = draft.scores[a.id]!
      }
      comments[a.id] = draft.comments[a.id] ?? comments[a.id]!
      hadTeacher[a.id] = draft.hadTeacher[a.id] ?? hadTeacher[a.id]!
    }
    tip.value = draft.tip ?? ''
    prefersAllCriteria.value = draft.allCriteria === true
    step.value = Math.min(Math.max(draft.step, 0), steps.value.length - 1)
    part.value = Math.min(Math.max(draft.part, 0), partCount(step.value) - 1)
    draftRestored.value = true
  } catch {
    removeStored(draftKey)
  }
}

let savesDraft = false

watch([survival, scores, comments, hadTeacher, tip, step, part, prefersAllCriteria], () => {
  if (savesDraft && state.value === 'ready') {
    const draft: Draft = {
      survival,
      scores,
      comments,
      hadTeacher,
      allCriteria: prefersAllCriteria.value,
      tip: tip.value,
      step: step.value,
      part: part.value,
    }
    writeStored(draftKey, JSON.stringify(draft))
  }
})

function prefill(existing: Existing) {
  Object.assign(survival, {
    examDifficulty: existing.examDifficulty,
    labsHelp: existing.labsHelp,
    attendanceStrictness: existing.attendanceStrictness,
    attendanceUseful: existing.attendanceUseful,
    exam: existing.exam,
    weeklyHours: existing.weeklyHours,
  })
  for (const rating of existing.ratings) {
    scores[rating.teachingAssignmentId] = rating.scores
    if (rating.scores.some((s) => s !== null)) {
      hadTeacher[rating.teachingAssignmentId] = 'yes'
    }
  }
  for (const post of existing.posts) {
    if (post.kind === 'tip') {
      tip.value = post.body
    } else if (post.teachingAssignmentId) {
      comments[post.teachingAssignmentId] = post.body
      hadTeacher[post.teachingAssignmentId] = 'yes'
    }
  }
}

onMounted(async () => {
  try {
    token.value = await tokenFor(userId, offeringId)
    if (!token.value) {
      if (!(await localMaster(userId))) {
        await router.replace({ name: 'recovery', query: { next: route.fullPath } })
        return
      }
      await issueTokens(userId).catch(() => undefined)
      token.value = await tokenFor(userId, offeringId)
    }
    if (!token.value) {
      state.value = 'noToken'
      return
    }
    ;[form.value, labels.value] = await Promise.all([
      anonApi<OfferingForm>('GET', `/anon/offerings/${offeringId}`),
      lookups(),
    ])
    for (const a of form.value.assignments) {
      scores[a.id] = Array(14).fill(null)
      comments[a.id] = ''
      hadTeacher[a.id] = null
    }
    if (token.value.submitted) {
      try {
        prefill(
          await anonApi<Existing>(
            'GET',
            `/anon/submissions/${offeringId}`,
            undefined,
            token.value.msg,
          ),
        )
        editing.value = true
      } catch (e) {
        if (!(e instanceof ApiError && e.status === 404)) {
          throw e
        }
      }
    }
    if (!editing.value) {
      restoreDraft()
    }
    state.value = 'ready'
    await nextTick()
    savesDraft = !editing.value
  } catch (e) {
    error.value = describeError(t, e)
  }
})

async function submit() {
  error.value = ''
  duplicate.value = false
  busy.value = true
  const body = {
    offeringId,
    fingerprint: token.value!.fingerprint,
    msg: token.value!.msg,
    sig: token.value!.sig,
    ...survival,
    weeklyHours: hoursUnknown.value ? null : survival.weeklyHours,
    ratings: ratings.value,
    tip: tip.value,
  }
  try {
    if (editing.value) {
      await anonApi('PUT', `/anon/submissions/${offeringId}`, body, token.value!.msg)
    } else {
      await anonApi('POST', '/anon/submissions', body)
    }
    removeStored(draftKey)
    await markSubmitted(userId, offeringId, true)
    state.value = 'saved'
  } catch (e) {
    duplicate.value = e instanceof ApiError && e.status === 409
    if (duplicate.value) {
      await markSubmitted(userId, offeringId, true)
    }
    error.value = describeError(t, e)
    window.scrollTo({ top: 0 })
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="page-wide">
    <RouterLink
      to="/rate"
      class="inline-flex min-h-11 items-center gap-1 text-sm no-underline hover:underline"
    >
      <ArrowLeft class="size-4" aria-hidden="true" /> {{ t('rate.title') }}
    </RouterLink>
    <AppNotice v-if="error" kind="error">
      {{ error }}
      <RouterLink v-if="duplicate" to="/me/submissions" class="ml-1">{{
        t('rateForm.edit')
      }}</RouterLink>
    </AppNotice>
    <p v-if="state === 'noToken'" class="lede">
      {{ t('rateForm.noToken') }} <RouterLink to="/rate">{{ t('rate.title') }}</RouterLink>
    </p>
    <section
      v-if="state === 'saved'"
      role="status"
      class="card mx-auto mt-6 max-w-xl px-6 py-10 text-center"
    >
      <NoteStar class="mx-auto size-24" />
      <h1 class="h1 mt-4">{{ t('rateForm.doneTitle') }}</h1>
      <p class="lede mx-auto">{{ t('rateForm.saved') }}</p>
      <div class="mt-6 flex flex-wrap justify-center gap-2">
        <RouterLink to="/rate" class="btn btn-primary">{{ t('rateForm.rateAnother') }}</RouterLink>
        <RouterLink to="/me/submissions" class="btn btn-outline">{{
          t('nav.submissions')
        }}</RouterLink>
      </div>
    </section>
    <form v-if="state === 'ready' && form && labels" class="mt-2" @submit.prevent="submit">
      <span class="eyebrow">
        {{ form.academicYear }} · {{ t('rate.semester', { n: form.semesterNo }) }}
      </span>
      <h1 class="h1">{{ form.title }}</h1>
      <p class="mt-2 text-sm text-muted">
        {{ t('rateForm.anonymous') }} {{ t('rateForm.optionalHint') }}
      </p>
      <AppNotice v-if="draftRestored">{{ t('rateForm.draftRestored') }}</AppNotice>

      <nav
        ref="stepNav"
        class="mt-5"
        :aria-label="t('rateForm.progress', { n: step + 1, total: steps.length })"
      >
        <ol class="m-0 flex list-none flex-wrap gap-1.5 p-0">
          <li v-for="(item, index) in steps" :key="item.key">
            <button
              type="button"
              class="chip min-w-11 gap-1.5 px-3 text-xs"
              :class="{ 'chip-on': index === step, 'border-ink': isStepDone(index) }"
              :aria-current="index === step ? 'step' : undefined"
              :aria-label="`${index + 1}. ${item.label}`"
              @click="goTo(index)"
            >
              <Check v-if="isStepDone(index)" class="size-4" aria-hidden="true" />
              <span v-else class="mono">{{ index + 1 }}</span>
              <span class="hidden max-w-44 truncate md:inline">{{ item.label }}</span>
            </button>
          </li>
        </ol>
      </nav>

      <section
        ref="stepPanel"
        :key="`${step}-${part}`"
        tabindex="-1"
        class="step-in card mt-4 outline-none max-sm:px-3 md:p-6"
      >
        <template v-if="step === 0">
          <h2 class="h2">{{ t('rateForm.howToPass') }}</h2>
          <div class="mt-4 grid gap-5">
            <div :class="rowClass">
              <span :id="`${fieldId}-examDifficulty`" :class="hintedLabel">
                {{ t('rateForm.examDifficulty') }}
                <span class="text-xs font-normal text-muted">{{
                  t('rateForm.scaleDifficulty')
                }}</span>
              </span>
              <SegmentedChips
                v-model="survival.examDifficulty"
                :options="scale"
                scale
                :null-label="t('rateForm.dontKnow')"
                :aria-labelledby="`${fieldId}-examDifficulty`"
                size="sm"
              />
            </div>
            <div v-for="group in examGroups" :key="group.kind" :class="rowClass">
              <span :id="`${fieldId}-exam-${group.kind}`" :class="hintedLabel">
                {{ group.label }}
                <span class="text-xs font-normal text-muted">{{ t('rateForm.pickAny') }}</span>
              </span>
              <div
                class="flex flex-wrap gap-1.5"
                role="group"
                :aria-labelledby="`${fieldId}-exam-${group.kind}`"
              >
                <label
                  v-for="o in group.options"
                  :key="o.code"
                  class="chip min-w-11 px-3 text-xs has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-copper"
                  :class="{ 'chip-on': survival.exam.includes(o.code) }"
                >
                  <input v-model="survival.exam" type="checkbox" class="sr-only" :value="o.code" />
                  {{ o.label }}
                </label>
              </div>
            </div>
            <div :class="rowClass">
              <span :id="`${fieldId}-labsHelp`" :class="hintedLabel">
                {{ t('rateForm.labsHelp') }}
                <span class="text-xs font-normal text-muted">{{ t('rateForm.scaleHelp') }}</span>
              </span>
              <SegmentedChips
                v-model="survival.labsHelp"
                :options="scale"
                scale
                :null-label="t('rateForm.dontKnow')"
                :aria-labelledby="`${fieldId}-labsHelp`"
                size="sm"
              />
            </div>
            <div :class="rowClass">
              <label :for="`${fieldId}-weeklyHours`" class="text-sm font-medium">{{
                t('rateForm.weeklyHours')
              }}</label>
              <div class="flex flex-wrap items-center gap-1.5">
                <input
                  :id="`${fieldId}-weeklyHours`"
                  v-model.number="survival.weeklyHours"
                  type="number"
                  min="0"
                  max="80"
                  inputmode="numeric"
                  class="input w-28"
                  @keydown.enter.prevent
                />
                <button
                  type="button"
                  class="chip min-w-11 px-3 text-xs"
                  :class="hoursUnknown ? 'chip-none' : 'text-muted'"
                  :aria-pressed="hoursUnknown"
                  @click="survival.weeklyHours = null"
                >
                  {{ t('rateForm.dontKnow') }}
                </button>
              </div>
            </div>
            <div :class="rowClass">
              <span :id="`${fieldId}-attendanceUseful`" class="text-sm font-medium">{{
                t('rateForm.attendanceUseful')
              }}</span>
              <SegmentedChips
                v-model="survival.attendanceUseful"
                :options="attendanceOptions"
                :null-label="t('rateForm.dontKnow')"
                :aria-labelledby="`${fieldId}-attendanceUseful`"
                size="sm"
              />
            </div>
            <div :class="rowClass">
              <span :id="`${fieldId}-attendanceStrictness`" :class="hintedLabel">
                {{ t('rateForm.attendanceStrictness') }}
                <span class="text-xs font-normal text-muted">{{ t('rateForm.scaleStrict') }}</span>
              </span>
              <SegmentedChips
                v-model="survival.attendanceStrictness"
                :options="scale"
                scale
                :null-label="t('rateForm.dontKnow')"
                :aria-labelledby="`${fieldId}-attendanceStrictness`"
                size="sm"
              />
            </div>
          </div>
        </template>

        <template v-else-if="teacher">
          <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span class="tag bg-board text-muted">{{ teacher.componentLabel }}</span>
            <h2 class="h2">{{ teacher.title }} {{ teacher.professor }}</h2>
            <span v-if="teacher.subgroup" class="text-sm text-muted">
              {{ t('rateForm.subgroup', { label: teacher.subgroup }) }}
            </span>
            <span
              v-if="hadTeacher[teacher.id] === 'yes'"
              class="mono text-xs text-muted md:ml-auto"
            >
              {{
                t('rateForm.scored', {
                  n: scoredCount(teacher.id),
                  total: teacherShowsAll ? 14 : quickAxes.length,
                })
              }}
            </span>
          </div>

          <template v-if="part === 0">
            <p :id="`${fieldId}-hadTeacher`" class="mt-4 font-medium">
              {{ t('rateForm.hadTeacher') }}
            </p>
            <SegmentedChips
              v-model="hadTeacher[teacher.id]"
              :options="yesNo"
              :aria-labelledby="`${fieldId}-hadTeacher`"
              class="mt-2"
            />
            <p v-if="hadTeacher[teacher.id] === 'no'" class="mt-3 text-sm text-muted">
              {{ t('rateForm.skippedTeacher') }}
            </p>
          </template>

          <template v-if="hadTeacher[teacher.id] === 'yes'">
            <div class="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2">
              <p v-if="!teacherShowsAll" class="font-medium">{{ t('rateForm.quickHint') }}</p>
              <button
                v-if="quickAxes.length"
                type="button"
                class="btn btn-outline"
                @click="toggleCriteria(teacher.id)"
              >
                <component
                  :is="teacherShowsAll ? ListMinus : ListPlus"
                  class="size-4"
                  aria-hidden="true"
                />
                {{ t(teacherShowsAll ? 'rateForm.quickOnly' : 'rateForm.allCriteria') }}
              </button>
              <p class="basis-full text-xs text-muted lg:basis-auto">
                {{ t('rateForm.scaleQuality') }}
              </p>
            </div>
            <div
              v-if="!teacherShowsAll"
              class="mt-4 grid gap-5 border-t border-dashed border-line pt-5"
            >
              <div v-for="axis in quickAxes" :key="axis.code" :class="rowClass">
                <span :id="`${fieldId}-${axis.code}`" class="text-sm font-medium">
                  {{ axis.label }}
                </span>
                <SegmentedChips
                  v-model="scores[teacher.id]![axis.index]"
                  :options="scale"
                  scale
                  :null-label="t('rateForm.na')"
                  :aria-labelledby="`${fieldId}-${axis.code}`"
                  size="sm"
                />
              </div>
            </div>
            <template v-for="pillar in teacherShowsAll ? shownPillars : []" :key="pillar.code">
              <div
                class="mt-5 flex items-baseline justify-between gap-3 border-t border-dashed border-line pt-5"
              >
                <h3 class="eyebrow mb-0">{{ pillar.label }}</h3>
                <span v-if="!showsAllPillars" class="mono text-xs text-muted"
                  >{{ part + 1 }}/{{ pillars.length }}</span
                >
              </div>
              <div class="mt-4 grid gap-5">
                <div v-for="axis in pillar.axes" :key="axis.code" :class="rowClass">
                  <span :id="`${fieldId}-${axis.code}`" class="text-sm font-medium">
                    {{ axis.label }}
                  </span>
                  <SegmentedChips
                    v-model="scores[teacher.id]![axis.index]"
                    :options="scale"
                    scale
                    :null-label="t('rateForm.na')"
                    :aria-labelledby="`${fieldId}-${axis.code}`"
                    size="sm"
                  />
                </div>
              </div>
            </template>
            <label
              v-if="!teacherShowsAll || showsAllPillars || part === pillars.length - 1"
              class="field mt-6"
            >
              {{ t('rateForm.comment') }}
              <textarea
                v-model="comments[teacher.id]"
                maxlength="280"
                rows="3"
                class="input"
              ></textarea>
              <span class="text-xs font-normal text-muted">
                <span class="mono">{{ (comments[teacher.id] ?? '').length }} / 280</span> ·
                {{ t('rateForm.commentHint') }}
              </span>
            </label>
          </template>
        </template>

        <template v-else>
          <label class="field">
            <h2 class="h2">{{ t('rateForm.tip') }}</h2>
            <textarea v-model="tip" maxlength="1000" rows="4" class="input mt-2"></textarea>
            <span class="mono text-xs font-normal text-muted">{{ tip.length }} / 1000</span>
          </label>
          <p class="mt-4 text-sm text-muted">
            {{
              form.assignments.length
                ? t('rateForm.summary', { n: ratings.length, total: form.assignments.length })
                : t('rateForm.noTeachers')
            }}
          </p>
        </template>
      </section>

      <div class="card action-bar mt-4 flex flex-wrap items-center justify-end gap-2">
        <button
          type="button"
          class="btn btn-outline btn-icon"
          :disabled="step === 0 && part === 0"
          :aria-label="t('common.back')"
          @click="goBack"
        >
          <ArrowLeft class="size-4" aria-hidden="true" />
        </button>
        <span class="mono mr-auto px-1 text-xs text-muted" aria-hidden="true">
          {{ step + 1 }}/{{ steps.length }}
        </span>
        <button
          type="submit"
          class="btn"
          :class="isLastStep ? 'btn-primary' : 'btn-ghost'"
          :disabled="busy"
        >
          <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
          {{ editing ? t('rateForm.save') : t('rateForm.submit') }}
        </button>
        <button v-if="!isLastStep" type="button" class="btn btn-primary" @click="goNext">
          {{ t('rateForm.next') }} <ArrowRight class="size-4" aria-hidden="true" />
        </button>
      </div>
    </form>
  </main>
</template>

<style scoped>
@media (prefers-reduced-motion: no-preference) {
  .step-in {
    animation: step-in 0.26s ease-out;
  }
}

@keyframes step-in {
  from {
    opacity: 0;
    translate: 14px 0;
  }
}
</style>
