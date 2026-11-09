<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Flag } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import SegmentedChips from '@/components/SegmentedChips.vue'
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
  { value: 'texts' as const, label: t('moderation.texts', { n: total.value }) },
  { value: 'reports' as const, label: t('moderation.reports', { n: reports.value.length }) },
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
  <main class="page">
    <span class="eyebrow">{{ t('nav.moderation') }}</span>
    <h1 class="h1">{{ t('nav.moderation') }}</h1>
    <div class="mt-5">
      <SegmentedChips v-model="tab" :options="tabs" />
    </div>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <AppNotice v-if="info">{{ info }}</AppNotice>

    <template v-if="tab === 'texts'">
      <p v-if="!current" class="mt-8 text-center text-muted">{{ t('moderation.empty') }}</p>
      <section v-else class="card mt-5 grid gap-4">
        <div class="flex flex-wrap items-center gap-2 text-sm">
          <span class="tag bg-board text-muted">{{
            current.kind === 'tip' ? t('submissions.tip') : t('submissions.comment')
          }}</span>
          <strong>{{ current.course }}</strong>
          <span class="ml-auto text-xs text-muted">{{
            t('moderation.submitted', { date: d(current.week, 'short') })
          }}</span>
        </div>
        <blockquote class="quote">{{ current.body }}</blockquote>
        <p
          v-for="flag in flags"
          :key="flag"
          class="flex items-center gap-2 text-sm font-medium text-copper"
        >
          <Flag class="size-4" aria-hidden="true" /> {{ flag }}
        </p>
        <details v-if="current.previousBody" class="text-sm">
          <summary class="cursor-pointer font-medium text-mask">
            {{ t('moderation.previous') }}
          </summary>
          <blockquote class="quote mt-2 border-line text-muted">
            {{ current.previousBody }}
          </blockquote>
        </details>
        <button class="btn btn-primary w-full" @click="decide(current)">
          {{ t('moderation.approve') }}
        </button>
        <div>
          <p class="mb-2 text-sm text-muted">{{ t('moderation.rejectFor') }}</p>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="r in reasons"
              :key="r.code"
              class="btn btn-danger btn-sm"
              @click="decide(current, r.code)"
            >
              {{ r.label }}
            </button>
          </div>
        </div>
        <button v-if="queue.length > 1" class="btn btn-ghost justify-self-start" @click="skip">
          {{ t('moderation.next') }}
        </button>
      </section>
    </template>

    <template v-else>
      <p v-if="!reports.length" class="mt-8 text-center text-muted">
        {{ t('moderation.noReports') }}
      </p>
      <section v-for="report in reports" :key="report.postId" class="card mt-5 grid gap-4">
        <div class="flex flex-wrap items-center gap-2 text-sm">
          <span class="tag bg-board text-muted">{{
            report.kind === 'tip' ? t('submissions.tip') : t('submissions.comment')
          }}</span>
          <strong>{{ report.course }}</strong>
        </div>
        <p class="text-xs text-muted">
          {{
            t('moderation.reportLine', {
              reasons: report.reasons,
              n: report.count,
              date: d(report.week, 'short'),
            })
          }}
        </p>
        <blockquote class="quote">{{ report.body }}</blockquote>
        <button class="btn btn-outline justify-self-start" @click="closeReport(report)">
          {{ t('moderation.dismiss') }}
        </button>
        <div>
          <p class="mb-2 text-sm text-muted">{{ t('moderation.upholdFor') }}</p>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="r in reasons"
              :key="r.code"
              class="btn btn-danger btn-sm"
              @click="closeReport(report, r.code)"
            >
              {{ r.label }}
            </button>
          </div>
        </div>
      </section>
    </template>
  </main>
</template>
