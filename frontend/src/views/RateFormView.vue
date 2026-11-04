<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'
import RadioButton from 'primevue/radiobutton'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import Textarea from 'primevue/textarea'
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
const scale = Array.from({ length: 11 }, (_, i) => i)

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
  <main>
    <RouterLink to="/rate">← {{ t('rate.title') }}</RouterLink>
    <Message v-if="error" severity="error">
      {{ error }}
      <RouterLink v-if="duplicate" to="/me/submissions">{{ t('rateForm.edit') }}</RouterLink>
    </Message>
    <p v-if="state === 'noToken'">
      {{ t('rateForm.noToken') }} <RouterLink to="/rate">{{ t('rate.title') }}</RouterLink>
    </p>
    <template v-if="state === 'saved'">
      <Message severity="success">{{ t('rateForm.saved') }}</Message>
      <RouterLink to="/me/submissions">{{ t('nav.submissions') }}</RouterLink>
    </template>
    <form v-if="state === 'ready' && form && labels" @submit.prevent="submit">
      <h1>{{ form.title }}</h1>
      <p class="muted">
        {{ form.academicYear }} · {{ t('rate.semester', { n: form.semesterNo }) }} ·
        {{ t('rateForm.anonymous') }}
      </p>

      <h2>1. {{ t('rateForm.howToPass') }}</h2>
      <fieldset>
        <legend>{{ t('rateForm.examDifficulty') }}</legend>
        <SelectButton
          v-model="survival.examDifficulty"
          :options="scale"
          size="small"
          class="scale"
        />
      </fieldset>
      <fieldset>
        <legend>{{ t('rateForm.labsHelp') }}</legend>
        <SelectButton v-model="survival.labsHelp" :options="scale" size="small" class="scale" />
      </fieldset>
      <fieldset>
        <legend>{{ t('rateForm.attendanceUseful') }}</legend>
        <span v-for="o in labels.attendance" :key="o.code" class="radio">
          <RadioButton
            v-model="survival.attendanceUseful"
            :input-id="`att-${o.code}`"
            :value="o.code"
          />
          <label :for="`att-${o.code}`">{{ o.label }}</label>
        </span>
      </fieldset>
      <label class="field">
        {{ t('rateForm.weeklyHours') }}
        <InputNumber v-model="survival.weeklyHours" :min="0" :max="80" show-buttons />
      </label>
      <label class="field">
        {{ t('rateForm.examFormat') }}
        <Select
          v-model="survival.examFormat"
          :options="labels.examFormats"
          option-label="label"
          option-value="code"
          show-clear
        />
      </label>
      <fieldset>
        <legend>{{ t('rateForm.attendanceStrictness') }}</legend>
        <SelectButton
          v-model="survival.attendanceStrictness"
          :options="scale"
          size="small"
          class="scale"
        />
      </fieldset>

      <h2>2. {{ t('rateForm.teachers') }}</h2>
      <p class="muted">{{ t('rateForm.teachersHint') }}</p>
      <p v-if="!form.assignments.length" class="muted">{{ t('rateForm.noTeachers') }}</p>
      <details
        v-for="a in form.assignments"
        :key="a.id"
        class="card"
        :open="scores[a.id]?.some((s) => s !== null) || !!comments[a.id]"
      >
        <summary>
          <strong>{{ a.componentLabel }}: {{ a.title }} {{ a.professor }}</strong>
          <span v-if="a.subgroup" class="muted">
            ({{ t('rateForm.subgroup', { label: a.subgroup }) }})</span
          >
        </summary>
        <div v-for="pillar in pillars" :key="pillar.code">
          <h3>{{ pillar.label }}</h3>
          <fieldset v-for="axis in pillar.axes" :key="axis.code">
            <legend>{{ axis.index + 1 }}. {{ axis.label }}</legend>
            <SelectButton
              v-model="scores[a.id]![axis.index]"
              :options="scale"
              size="small"
              class="scale"
            />
          </fieldset>
        </div>
        <label class="field">
          {{ t('rateForm.comment') }}
          <Textarea v-model="comments[a.id]" :maxlength="280" rows="3" auto-resize />
          <span class="muted"
            >{{ (comments[a.id] ?? '').length }} / 280 · {{ t('rateForm.commentHint') }}</span
          >
        </label>
      </details>

      <h2>3. {{ t('rateForm.tip') }}</h2>
      <Textarea v-model="tip" :maxlength="1000" rows="4" auto-resize fluid />
      <p class="muted">{{ tip.length }} / 1000</p>

      <Button
        type="submit"
        :label="editing ? t('rateForm.save') : t('rateForm.submit')"
        :loading="busy"
        fluid
      />
    </form>
  </main>
</template>

<style scoped>
h2 {
  font-size: 1.05rem;
  margin: 1.5rem 0 0.5rem;
}

h3 {
  font-size: 0.95rem;
  margin: 1rem 0 0.25rem;
}

fieldset {
  border: none;
  padding: 0;
  margin: 0 0 0.75rem;
}

legend {
  font-size: 0.9rem;
  margin-bottom: 0.25rem;
}

.scale {
  display: flex;
  flex-wrap: wrap;
}

.radio {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-right: 1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
}

summary {
  cursor: pointer;
}
</style>
