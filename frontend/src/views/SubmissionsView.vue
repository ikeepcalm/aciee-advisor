<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Message from 'primevue/message'
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
  <main>
    <h1>{{ t('nav.submissions') }}</h1>
    <p class="muted">{{ t('submissions.deviceOnly') }}</p>
    <Message v-if="error" severity="error">{{ error }}</Message>
    <p v-if="needsRestore">
      {{ t('submissions.restore') }}
      <RouterLink to="/me/recovery">{{ t('recovery.title') }}</RouterLink>
    </p>
    <p v-else-if="entries && !entries.length">
      {{ t('submissions.none') }} <RouterLink to="/rate">{{ t('nav.rate') }}</RouterLink>
    </p>
    <article v-for="entry in entries" :key="entry.offeringId" class="card">
      <strong>{{ entry.title }}</strong> <span class="muted">{{ entry.academicYear }}</span>
      <p v-if="entry.posts === null" class="muted">{{ t('submissions.notFound') }}</p>
      <template v-else>
        <p>{{ t('submissions.ratingSent') }}</p>
        <p v-for="post in entry.posts" :key="post.id">
          {{ post.kind === 'tip' ? t('submissions.tip') : t('submissions.comment') }}:
          {{ postStatus(post) }}
        </p>
        <div class="actions">
          <Button
            as="router-link"
            :to="`/rate/${entry.offeringId}`"
            :label="t('rateForm.edit')"
            size="small"
          />
          <Button
            v-if="!entry.confirming"
            :label="t('submissions.delete')"
            severity="danger"
            text
            size="small"
            @click="entry.confirming = true"
          />
        </div>
        <Message v-if="entry.confirming" severity="warn">
          {{ t('submissions.deleteWarning') }}
          <div class="actions">
            <Button
              :label="t('submissions.delete')"
              severity="danger"
              size="small"
              @click="remove(entry)"
            />
            <Button
              :label="t('common.cancel')"
              text
              size="small"
              @click="entry.confirming = false"
            />
          </div>
        </Message>
      </template>
    </article>
  </main>
</template>

<style scoped>
.actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

article p {
  margin: 0.35rem 0;
}
</style>
