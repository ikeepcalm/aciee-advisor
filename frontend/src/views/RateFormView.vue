<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Loader2 } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import SegmentedChips from '@/components/SegmentedChips.vue'
import { anonApi, ApiError, describeError, lookups, type Lookups } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { markSubmitted, tokenFor } from '@/crypto/tokens'

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

interface Existing {
  examDifficulty: number | null
  labsHelp: number | null
  attendanceStrictness: number | null
  attendanceUseful: string | null
  examFormat: string | null
  weeklyHours: number | null
  ratings: { teachingAssignmentId: number; scores: (number | null)[] }[]
  posts: { kind: string; teachingAssignmentId: number | null; body: string }[]
}

const { t } = useI18n()
const offeringId = Number(useRoute().params.offeringId)
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
  examFormat: null as string | null,
  weeklyHours: null as number | null,
})
const scores = reactive<Record<number, (number | null)[]>>({})
const comments = reactive<Record<number, string>>({})
const tip = ref('')
const scale = Array.from({ length: 11 }, (_, i) => ({ value: i, label: String(i) }))

const pillars = computed(() =>
  (labels.value?.pillars ?? []).map((pillar) => ({
    ...pillar,
    axes: (labels.value?.axes ?? [])
      .map((axis, index) => ({ ...axis, index }))
      .filter((axis) => axis.group === pillar.code),
  })),
)

function prefill(existing: Existing) {
  Object.assign(survival, {
    examDifficulty: existing.examDifficulty,
    labsHelp: existing.labsHelp,
    attendanceStrictness: existing.attendanceStrictness,
    attendanceUseful: existing.attendanceUseful,
    examFormat: existing.examFormat,
    weeklyHours: existing.weeklyHours,
  })
  for (const rating of existing.ratings) {
    scores[rating.teachingAssignmentId] = rating.scores
  }
  for (const post of existing.posts) {
    if (post.kind === 'tip') {
      tip.value = post.body
    } else if (post.teachingAssignmentId) {
      comments[post.teachingAssignmentId] = post.body
    }
  }
}

onMounted(async () => {
  try {
    token.value = await tokenFor(userId, offeringId)
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
    state.value = 'ready'
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
    ratings: form
      .value!.assignments.map((a) => ({
        teachingAssignmentId: a.id,
        scores: scores[a.id],
        comment: comments[a.id],
      }))
      .filter((r) => r.scores!.some((s) => s !== null) || r.comment),
    tip: tip.value,
  }
  try {
    if (editing.value) {
      await anonApi('PUT', `/anon/submissions/${offeringId}`, body, token.value!.msg)
    } else {
      await anonApi('POST', '/anon/submissions', body)
    }
    await markSubmitted(userId, offeringId, true)
    state.value = 'saved'
  } catch (e) {
    duplicate.value = e instanceof ApiError && e.status === 409
    if (duplicate.value) {
      await markSubmitted(userId, offeringId, true)
    }
    error.value = describeError(t, e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="page">
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
    <template v-if="state === 'saved'">
      <AppNotice kind="success">{{ t('rateForm.saved') }}</AppNotice>
      <RouterLink to="/me/submissions" class="btn btn-outline">{{
        t('nav.submissions')
      }}</RouterLink>
    </template>
    <form v-if="state === 'ready' && form && labels" class="mt-2" @submit.prevent="submit">
      <span class="eyebrow">
        {{ form.academicYear }} · {{ t('rate.semester', { n: form.semesterNo }) }} ·
        {{ t('rateForm.anonymous') }}
      </span>
      <h1 class="h1">{{ form.title }}</h1>

      <section class="card mt-8 grid gap-5">
        <h2 class="h2"><span class="mono text-copper">1</span> {{ t('rateForm.howToPass') }}</h2>
        <fieldset class="m-0 min-w-0 border-0 p-0">
          <legend class="mb-1.5 text-sm font-medium">{{ t('rateForm.examDifficulty') }}</legend>
          <SegmentedChips v-model="survival.examDifficulty" :options="scale" clearable size="sm" />
        </fieldset>
        <fieldset class="m-0 min-w-0 border-0 p-0">
          <legend class="mb-1.5 text-sm font-medium">{{ t('rateForm.labsHelp') }}</legend>
          <SegmentedChips v-model="survival.labsHelp" :options="scale" clearable size="sm" />
        </fieldset>
        <fieldset class="m-0 min-w-0 border-0 p-0">
          <legend class="mb-1.5 text-sm font-medium">{{ t('rateForm.attendanceUseful') }}</legend>
          <div class="flex flex-wrap gap-x-5 gap-y-2">
            <label
              v-for="o in labels.attendance"
              :key="o.code"
              class="inline-flex min-h-11 items-center gap-2 text-sm"
            >
              <input
                v-model="survival.attendanceUseful"
                type="radio"
                :value="o.code"
                class="check"
              />
              {{ o.label }}
            </label>
          </div>
        </fieldset>
        <div class="grid gap-5 sm:grid-cols-2">
          <label class="field">
            {{ t('rateForm.weeklyHours') }}
            <input
              v-model.number="survival.weeklyHours"
              type="number"
              min="0"
              max="80"
              inputmode="numeric"
              class="input"
            />
          </label>
          <label class="field">
            {{ t('rateForm.examFormat') }}
            <select v-model="survival.examFormat" class="input">
              <option :value="null">—</option>
              <option v-for="o in labels.examFormats" :key="o.code" :value="o.code">
                {{ o.label }}
              </option>
            </select>
          </label>
        </div>
        <fieldset class="m-0 min-w-0 border-0 p-0">
          <legend class="mb-1.5 text-sm font-medium">
            {{ t('rateForm.attendanceStrictness') }}
          </legend>
          <SegmentedChips
            v-model="survival.attendanceStrictness"
            :options="scale"
            clearable
            size="sm"
          />
        </fieldset>
      </section>

      <section class="mt-8">
        <h2 class="h2"><span class="mono text-copper">2</span> {{ t('rateForm.teachers') }}</h2>
        <p class="mt-1 text-sm text-muted">{{ t('rateForm.teachersHint') }}</p>
        <p v-if="!form.assignments.length" class="mt-3 text-sm text-muted">
          {{ t('rateForm.noTeachers') }}
        </p>
        <details
          v-for="a in form.assignments"
          :key="a.id"
          class="card mt-3"
          :open="scores[a.id]?.some((s) => s !== null) || !!comments[a.id]"
        >
          <summary class="-m-4 cursor-pointer p-4 font-semibold">
            <span class="tag mr-2 bg-board text-muted">{{ a.componentLabel }}</span>
            {{ a.title }} {{ a.professor }}
            <span v-if="a.subgroup" class="text-sm font-normal text-muted">
              · {{ t('rateForm.subgroup', { label: a.subgroup }) }}</span
            >
          </summary>
          <div v-for="pillar in pillars" :key="pillar.code" class="mt-5">
            <h3 class="eyebrow">{{ pillar.label }}</h3>
            <fieldset
              v-for="axis in pillar.axes"
              :key="axis.code"
              class="m-0 mt-3 min-w-0 border-0 p-0"
            >
              <legend class="mb-1.5 text-sm font-medium">
                <span class="mono text-xs text-muted">{{ axis.index + 1 }}.</span> {{ axis.label }}
              </legend>
              <SegmentedChips
                v-model="scores[a.id]![axis.index]"
                :options="scale"
                clearable
                size="sm"
              />
            </fieldset>
          </div>
          <label class="field mt-5">
            {{ t('rateForm.comment') }}
            <textarea v-model="comments[a.id]" maxlength="280" rows="3" class="input"></textarea>
            <span class="text-xs font-normal text-muted">
              <span class="mono">{{ (comments[a.id] ?? '').length }} / 280</span> ·
              {{ t('rateForm.commentHint') }}
            </span>
          </label>
        </details>
      </section>

      <section class="card mt-8">
        <label class="field">
          <h2 class="h2 font-display">
            <span class="mono text-copper">3</span> {{ t('rateForm.tip') }}
          </h2>
          <textarea v-model="tip" maxlength="1000" rows="4" class="input mt-2"></textarea>
          <span class="mono text-xs font-normal text-muted">{{ tip.length }} / 1000</span>
        </label>
      </section>

      <button type="submit" class="btn btn-primary mt-8 w-full" :disabled="busy">
        <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
        {{ editing ? t('rateForm.save') : t('rateForm.submit') }}
      </button>
    </form>
  </main>
</template>
