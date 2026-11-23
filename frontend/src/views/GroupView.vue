<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { TabsContent, TabsList, TabsRoot, TabsTrigger } from 'reka-ui'
import AppNotice from '@/components/AppNotice.vue'
import GroupPeople from '@/components/group/GroupPeople.vue'
import GroupPicker from '@/components/group/GroupPicker.vue'
import GroupTeaching from '@/components/group/GroupTeaching.vue'
import { api } from '@/api'
import { useRun, type GroupSummary, type JoinRequest } from '@/group'

interface Membership {
  groupId: number
  groupCode: string
  specialization: string
  studyYear: number
  academicYear: string
  subgroup: string | null
  since: string
  until: string | null
  leader: boolean
}

const { t, d } = useI18n()
const { error, notice, run } = useRun()
const memberships = ref<Membership[]>([])
const requests = ref<JoinRequest[]>([])
const groups = ref<GroupSummary[]>([])
const pendingCount = ref(0)
const loaded = ref(false)

const today = new Date().toISOString().slice(0, 10)
const current = computed(() =>
  memberships.value.find((m) => m.since <= today && (!m.until || today < m.until)),
)
const leads = computed(() => current.value?.leader === true)

function date(value: string) {
  return d(new Date(value), 'short')
}

async function load() {
  const mine = await api<{ memberships: Membership[]; requests: JoinRequest[] }>(
    'GET',
    '/api/groups/mine',
  )
  memberships.value = mine.memberships
  requests.value = mine.requests
  loaded.value = true
  if (!current.value) {
    groups.value = await api<GroupSummary[]>('GET', '/api/groups')
  }
}

async function claim(groupId: number, subgroupId: number | null) {
  await run(async () => {
    await api('POST', `/api/groups/${groupId}/leader-claims`, { subgroupId })
    await load()
  }, t('group.claimSent'))
  window.scrollTo({ top: 0 })
}

async function request(groupId: number, subgroupId: number | null) {
  await run(async () => {
    await api('POST', `/api/groups/${groupId}/requests`, { subgroupId })
    await load()
  }, t('group.requestSent'))
  window.scrollTo({ top: 0 })
}

onMounted(() => run(load))
</script>

<template>
  <main class="page">
    <span class="eyebrow">{{ t('nav.groups') }}</span>
    <h1 class="h1">{{ current ? current.groupCode : t('group.title') }}</h1>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <AppNotice v-if="notice" kind="success">{{ notice }}</AppNotice>

    <section v-if="current" class="card mt-6 flex flex-wrap items-center justify-between gap-4">
      <div class="min-w-0">
        <p class="font-semibold [overflow-wrap:anywhere]">
          {{ current.specialization }} · {{ t('catalog.year', { n: current.studyYear }) }}
          <span class="mono ml-1 text-xs whitespace-nowrap font-normal text-muted">{{
            current.academicYear
          }}</span>
        </p>
        <p class="mt-1 text-sm text-muted">
          {{
            current.subgroup
              ? t('group.subgroupSince', { subgroup: current.subgroup, since: date(current.since) })
              : t('group.since', { since: date(current.since) })
          }}
        </p>
      </div>
      <span v-if="leads" class="tag bg-mask text-on-mask">{{ t('group.leader') }}</span>
      <div class="grid basis-full gap-2 sm:flex sm:flex-wrap">
        <RouterLink
          :to="{ name: 'rate' }"
          class="btn"
          :class="leads ? 'btn-outline' : 'btn-primary'"
          >{{ t('rate.title') }}</RouterLink
        >
        <button v-if="!leads" class="btn btn-ghost" @click="claim(current.groupId, null)">
          {{ t('group.claim') }}
        </button>
      </div>
    </section>

    <section v-if="requests.length" class="mt-8">
      <h2 class="eyebrow mb-3">{{ t('group.myRequests') }}</h2>
      <p
        v-for="r in requests"
        :key="r.id"
        class="flex flex-wrap items-center gap-2 border-t border-line py-2 text-sm"
      >
        <strong>{{ r.groupCode }}</strong>
        <span class="mono text-xs whitespace-nowrap text-muted">{{ r.academicYear }}</span>
        <span class="text-muted">· {{ t(`group.kind.${r.kind}`) }}</span>
        <span class="tag ml-auto bg-board text-muted">{{ t(`group.status.${r.status}`) }}</span>
      </p>
    </section>

    <GroupPicker v-if="loaded && !current" :groups="groups" @claim="claim" @request="request" />

    <TabsRoot v-if="current && leads" default-value="people" class="mt-8">
      <TabsList class="flex gap-1 border-b border-line" :aria-label="t('group.leader')">
        <TabsTrigger
          v-for="tab in ['people', 'teaching']"
          :key="tab"
          :value="tab"
          class="-mb-px flex min-h-11 items-center gap-2 border-b-2 border-transparent px-3 text-sm font-medium whitespace-nowrap text-muted data-[state=active]:border-copper data-[state=active]:text-ink"
        >
          {{ t(tab === 'people' ? 'group.tabs.members' : 'group.tabs.teaching') }}
          <span v-if="tab === 'people' && pendingCount" class="tag bg-mark text-on-mark">{{
            pendingCount
          }}</span>
        </TabsTrigger>
      </TabsList>
      <TabsContent value="people" force-mount class="pt-5 data-[state=inactive]:hidden">
        <GroupPeople
          :group-id="current.groupId"
          :group-code="current.groupCode"
          @pending="pendingCount = $event"
        />
      </TabsContent>
      <TabsContent value="teaching" force-mount class="pt-5 data-[state=inactive]:hidden">
        <GroupTeaching :group-id="current.groupId" />
      </TabsContent>
    </TabsRoot>
  </main>
</template>
