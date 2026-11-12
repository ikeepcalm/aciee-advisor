<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loader2 } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import NoteStar from '@/components/NoteStar.vue'
import { anonApi, api, ApiError, describeError, lookups } from '@/api'
import { useAuthStore } from '@/stores/auth'
import {
  localTokens,
  markSubmitted,
  tokenFor,
  vaultExists,
  type RateableOffering,
} from '@/crypto/tokens'

interface Post {
  id: string
  kind: 'tip' | 'comment'
  status: 'pending' | 'approved' | 'rejected'
  reason: string | null
  published: boolean
}

interface Entry {
  offeringId: number
  title: string
  academicYear: string
  posts: Post[] | null
  confirming: boolean
}

const { t } = useI18n()
const userId = useAuthStore().user!.id
const entries = ref<Entry[] | null>(null)
const needsRestore = ref(false)
const threshold = ref(5)
const error = ref('')

function postTone(post: Post) {
  if (post.status === 'rejected') {
    return 'text-danger'
  }
  return post.status === 'approved' && post.published ? 'text-mask' : ''
}

function postStatus(post: Post) {
  if (post.status === 'pending') {
    return post.published ? t('submissions.publishedPendingEdit') : t('submissions.pending')
  }
  if (post.status === 'rejected') {
    return t('submissions.rejected', { reason: post.reason })
  }
  return post.published
    ? t('submissions.published')
    : t('submissions.hiddenUntil', { k: threshold.value })
}

async function load() {
  const tokens = (await localTokens(userId)).filter((tk) => tk.submitted)
  if (!tokens.length) {
    needsRestore.value = !(await localTokens(userId)).length && (await vaultExists())
    entries.value = []
    return
  }
  threshold.value = (await lookups()).threshold
  const offerings = new Map(
    (await api<RateableOffering[]>('GET', '/api/tokens/offerings')).map((o) => [o.offeringId, o]),
  )
  entries.value = await Promise.all(
    tokens.map(async (tk) => {
      const token = (await tokenFor(userId, tk.offeringId))!
      const posts = await anonApi<{ posts: Post[] }>(
        'GET',
        `/anon/submissions/${tk.offeringId}`,
        undefined,
        token.msg,
      ).then(
        (s) => s.posts,
        (e) => {
          if (e instanceof ApiError && e.status === 404) {
            return null
          }
          throw e
        },
      )
      const offering = offerings.get(tk.offeringId)
      return {
        offeringId: tk.offeringId,
        title: offering?.title ?? `#${tk.offeringId}`,
        academicYear: offering?.academicYear ?? '',
        posts,
        confirming: false,
      }
    }),
  )
}

async function remove(entry: Entry) {
  error.value = ''
  try {
    const token = (await tokenFor(userId, entry.offeringId))!
    await anonApi('DELETE', `/anon/submissions/${entry.offeringId}`, undefined, token.msg)
    await markSubmitted(userId, entry.offeringId, false)
    entries.value = entries.value!.filter((e) => e !== entry)
  } catch (e) {
    error.value = describeError(t, e)
  }
}

onMounted(() => load().catch((e) => (error.value = describeError(t, e))))
</script>

<template>
  <main class="page">
    <span class="eyebrow">{{ t('rate.title') }}</span>
    <h1 class="h1">{{ t('nav.submissions') }}</h1>
    <p class="lede text-sm">{{ t('submissions.deviceOnly') }}</p>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <p v-if="!entries && !error" class="mt-6 flex items-center gap-2 text-sm text-muted">
      <Loader2 class="size-4 animate-spin" aria-hidden="true" /> {{ t('common.loading') }}
    </p>
    <section v-if="needsRestore" class="card mt-6 grid max-w-md gap-4">
      <p>{{ t('submissions.restore') }}</p>
      <RouterLink to="/me/recovery" class="btn btn-primary">{{ t('recovery.title') }}</RouterLink>
    </section>
    <section v-else-if="entries && !entries.length" class="card mt-6 grid max-w-md gap-4">
      <NoteStar class="size-16" />
      <p>{{ t('submissions.none') }}</p>
      <RouterLink to="/rate" class="btn btn-primary">{{ t('nav.rate') }}</RouterLink>
    </section>
    <article v-for="entry in entries" :key="entry.offeringId" class="card mt-4">
      <strong class="block font-semibold [overflow-wrap:anywhere]">{{ entry.title }}</strong>
      <span class="mono text-xs text-muted">{{ entry.academicYear }}</span>
      <p v-if="entry.posts === null" class="mt-2 text-sm text-muted">
        {{ t('submissions.notFound') }}
      </p>
      <template v-else>
        <ul class="m-0 mt-3 list-none p-0 text-sm">
          <li class="border-t border-line py-1.5 text-mask">{{ t('submissions.ratingSent') }}</li>
          <li
            v-for="post in entry.posts"
            :key="post.id"
            class="flex flex-wrap justify-between gap-x-3 border-t border-line py-1.5"
          >
            <span class="text-muted">{{
              post.kind === 'tip' ? t('submissions.tip') : t('submissions.comment')
            }}</span>
            <span class="text-right" :class="postTone(post)">{{ postStatus(post) }}</span>
          </li>
        </ul>
        <div class="mt-3 flex flex-wrap gap-2">
          <RouterLink :to="`/rate/${entry.offeringId}`" class="btn btn-outline btn-sm">{{
            t('rateForm.edit')
          }}</RouterLink>
          <button
            v-if="!entry.confirming"
            class="btn btn-danger btn-sm border-transparent"
            @click="entry.confirming = true"
          >
            {{ t('submissions.delete') }}
          </button>
        </div>
        <AppNotice v-if="entry.confirming" kind="warn" class="mb-0">
          {{ t('submissions.deleteWarning') }}
          <div class="mt-2 flex gap-2">
            <button class="btn btn-danger btn-sm" @click="remove(entry)">
              {{ t('submissions.delete') }}
            </button>
            <button class="btn btn-ghost btn-sm" @click="entry.confirming = false">
              {{ t('common.cancel') }}
            </button>
          </div>
        </AppNotice>
      </template>
    </article>
  </main>
</template>
