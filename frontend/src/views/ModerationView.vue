<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Message from 'primevue/message'
import SelectButton from 'primevue/selectbutton'
import { api, ApiError, describeError, lookups, type Option } from '@/api'

interface QueueItem {
  postId: string
  versionNo: number
  kind: 'tip' | 'comment'
  body: string
  week: string
  course: string
  previousBody: string | null
}

interface Report {
  postId: string
  kind: 'tip' | 'comment'
  course: string
  body: string
  reasons: string
  count: number
  week: string
}

const { t, d } = useI18n()
const tab = ref<'texts' | 'reports'>('texts')
const queue = ref<QueueItem[]>([])
const total = ref(0)
const reports = ref<Report[]>([])
const reasons = ref<Option[]>([])
const error = ref('')
const info = ref('')

const current = computed(() => queue.value[0])
const tabs = computed(() => [
  { value: 'texts', label: t('moderation.texts', { n: total.value }) },
  { value: 'reports', label: t('moderation.reports', { n: reports.value.length }) },
])

const FLAGS: [RegExp, string][] = [
  [/[\w.+-]+@[\w-]+\.[\w.]+/, 'moderation.flagEmail'],
  [/(\+?4?0)?\s?7\d{2}[\s.-]?\d{3}[\s.-]?\d{3}/, 'moderation.flagPhone'],
]
const flags = computed(() =>
  current.value
    ? FLAGS.filter(([re]) => re.test(current.value!.body)).map(([, key]) => t(key))
    : [],
)

async function load() {
  error.value = ''
  try {
    const result = await api<{ total: number; items: QueueItem[] }>('GET', '/api/moderation/queue')
    queue.value = result.items
    total.value = result.total
    reports.value = await api<Report[]>('GET', '/api/moderation/reports')
  } catch (e) {
    error.value = describeError(t, e)
  }
}

async function act(path: string, body?: unknown) {
  error.value = ''
  info.value = ''
  try {
    await api('POST', path, body)
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      info.value = t('moderation.alreadyDone')
    } else {
      error.value = describeError(t, e)
      return
    }
  }
  await load()
}

const decide = (item: QueueItem, reasonCode?: string) =>
  act(
    `/api/moderation/posts/${item.postId}/versions/${item.versionNo}/${reasonCode ? 'reject' : 'approve'}`,
    reasonCode ? { reasonCode } : undefined,
  )

const closeReport = (report: Report, reasonCode?: string) =>
  act(
    `/api/moderation/reports/${report.postId}/${reasonCode ? 'uphold' : 'dismiss'}`,
    reasonCode ? { reasonCode } : undefined,
  )

function skip() {
  queue.value.push(queue.value.shift()!)
}

onMounted(async () => {
  reasons.value = (await lookups()).moderationReasons
  await load()
})
</script>

<template>
  <main>
    <h1>{{ t('nav.moderation') }}</h1>
    <SelectButton
      v-model="tab"
      :options="tabs"
      option-label="label"
      option-value="value"
      :allow-empty="false"
    />
    <Message v-if="error" severity="error">{{ error }}</Message>
    <Message v-if="info" severity="info">{{ info }}</Message>

    <template v-if="tab === 'texts'">
      <p v-if="!current" class="muted">{{ t('moderation.empty') }}</p>
      <section v-else class="card">
        <p>
          <strong>{{
            current.kind === 'tip' ? t('submissions.tip') : t('submissions.comment')
          }}</strong>
          · {{ current.course }}
        </p>
        <p class="muted">{{ t('moderation.submitted', { date: d(current.week, 'short') }) }}</p>
        <blockquote>{{ current.body }}</blockquote>
        <p v-for="flag in flags" :key="flag" class="flag">⚑ {{ flag }}</p>
        <details v-if="current.previousBody">
          <summary>{{ t('moderation.previous') }}</summary>
          <blockquote>{{ current.previousBody }}</blockquote>
        </details>
        <Button :label="t('moderation.approve')" fluid @click="decide(current)" />
        <p>{{ t('moderation.rejectFor') }}</p>
        <div class="reasons">
          <Button
            v-for="r in reasons"
            :key="r.code"
            :label="r.label"
            severity="danger"
            outlined
            size="small"
            @click="decide(current, r.code)"
          />
        </div>
        <Button v-if="queue.length > 1" :label="t('moderation.next')" text @click="skip" />
      </section>
    </template>

    <template v-else>
      <p v-if="!reports.length" class="muted">{{ t('moderation.noReports') }}</p>
      <section v-for="report in reports" :key="report.postId" class="card">
        <p>
          <strong>{{
            report.kind === 'tip' ? t('submissions.tip') : t('submissions.comment')
          }}</strong>
          · {{ report.course }}
        </p>
        <p class="muted">
          {{
            t('moderation.reportLine', {
              reasons: report.reasons,
              n: report.count,
              date: d(report.week, 'short'),
            })
          }}
        </p>
        <blockquote>{{ report.body }}</blockquote>
        <Button :label="t('moderation.dismiss')" text @click="closeReport(report)" />
        <p>{{ t('moderation.upholdFor') }}</p>
        <div class="reasons">
          <Button
            v-for="r in reasons"
            :key="r.code"
            :label="r.label"
            severity="danger"
            outlined
            size="small"
            @click="closeReport(report, r.code)"
          />
        </div>
      </section>
    </template>
  </main>
</template>

<style scoped>
section {
  margin-top: 1rem;
}

blockquote {
  margin: 0.75rem 0;
  padding: 0.75rem;
  border-left: 3px solid var(--p-content-border-color, #e5e7eb);
  white-space: pre-wrap;
}

.flag {
  color: var(--p-orange-600, #c2410c);
}

.reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}
</style>
