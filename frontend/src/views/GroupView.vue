<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { TabsContent, TabsList, TabsRoot, TabsTrigger } from 'reka-ui'
import ComboSelect from '@/components/ComboSelect.vue'
import AppNotice from '@/components/AppNotice.vue'
import { api, describeError } from '@/api'

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
interface JoinRequest {
  id: number
  email: string
  groupCode: string
  academicYear: string
  kind: string
  subgroup: string | null
  status: string
}
interface GroupSummary {
  id: number
  code: string
  specialization: string
  studyYear: number
  academicYear: string
}
interface RosterEntry {
  email: string
  subgroup: string | null
  since: string
  leader: boolean
}
interface Invite {
  id: number
  expiresAt: string
  maxUses: number
  uses: number
}
interface TeachingSlot {
  offeringId: number
  title: string
  semesterNo: number
  component: string
  subgroupId: number | null
  subgroup: string | null
  professorId: number | null
}

const { t, d } = useI18n()
const memberships = ref<Membership[]>([])
const requests = ref<JoinRequest[]>([])
const groups = ref<GroupSummary[]>([])
const claimGroupId = ref<number | null>(null)
const pending = ref<{ requests: JoinRequest[]; members: number; maxSize: number }>({
  requests: [],
  members: 0,
  maxSize: 0,
})
const roster = ref<RosterEntry[]>([])
const invites = ref<Invite[]>([])
const teaching = ref<{ slots: TeachingSlot[]; professors: { id: number; label: string }[] }>({
  slots: [],
  professors: [],
})
const inviteDays = ref(7)
const inviteUses = ref(30)
const newInviteLink = ref('')
const error = ref('')
const notice = ref('')

const today = new Date().toISOString().slice(0, 10)
const current = computed(() =>
  memberships.value.find((m) => m.since <= today && (!m.until || today < m.until)),
)
const leads = computed(() => current.value?.leader === true)
const groupOptions = computed(() =>
  groups.value.map((g) => ({
    value: g.id,
    label: `${g.code} · ${g.specialization} · ${g.academicYear}`,
  })),
)
const professorOptions = computed(() =>
  teaching.value.professors.map((p) => ({ value: p.id, label: p.label })),
)
const isNewCourse = (index: number) =>
  index === 0 ||
  teaching.value.slots[index - 1]!.offeringId !== teaching.value.slots[index]!.offeringId ||
  teaching.value.slots[index - 1]!.component !== teaching.value.slots[index]!.component

function date(value: string) {
  return d(new Date(value), 'short')
}

async function run(action: () => Promise<unknown>, success = '') {
  error.value = ''
  notice.value = ''
  try {
    await action()
    notice.value = success
  } catch (e) {
    error.value = describeError(t, e)
  }
}

async function load() {
  const mine = await api<{ memberships: Membership[]; requests: JoinRequest[] }>(
    'GET',
    '/api/groups/mine',
  )
  memberships.value = mine.memberships
  requests.value = mine.requests
  if (!current.value) {
    groups.value = await api<GroupSummary[]>('GET', '/api/groups')
  } else if (leads.value) {
    await loadLeaderTabs(current.value.groupId)
  }
}

async function loadLeaderTabs(groupId: number) {
  ;[pending.value, roster.value, invites.value, teaching.value] = await Promise.all([
    api<typeof pending.value>('GET', `/api/groups/${groupId}/requests`),
    api<RosterEntry[]>('GET', `/api/groups/${groupId}/members`),
    api<Invite[]>('GET', `/api/groups/${groupId}/invites`),
    api<typeof teaching.value>('GET', `/api/groups/${groupId}/teaching`),
  ])
}

function claim(groupId: number) {
  return run(async () => {
    await api('POST', `/api/groups/${groupId}/leader-claims`)
    await load()
  }, t('group.claimSent'))
}

function decide(requestId: number, decision: 'approve' | 'reject') {
  return run(async () => {
    await api('POST', `/api/groups/requests/${requestId}/${decision}`)
    const groupId = current.value!.groupId
    ;[pending.value, roster.value] = await Promise.all([
      api<typeof pending.value>('GET', `/api/groups/${groupId}/requests`),
      api<RosterEntry[]>('GET', `/api/groups/${groupId}/members`),
    ])
  })
}

function createInvite(groupId: number) {
  return run(async () => {
    const created = await api<{ token: string }>('POST', `/api/groups/${groupId}/invites`, {
      days: inviteDays.value,
      maxUses: inviteUses.value,
    })
    newInviteLink.value = `${location.origin}/join/${created.token}`
    invites.value = await api<Invite[]>('GET', `/api/groups/${groupId}/invites`)
  })
}

function deactivate(inviteId: number) {
  return run(async () => {
    await api('PATCH', `/api/groups/invites/${inviteId}`)
    invites.value = await api<Invite[]>('GET', `/api/groups/${current.value!.groupId}/invites`)
  })
}

function saveTeaching(groupId: number) {
  return run(async () => {
    await api('PUT', `/api/groups/${groupId}/teaching`, teaching.value.slots)
    teaching.value = await api<typeof teaching.value>('GET', `/api/groups/${groupId}/teaching`)
  }, t('group.teachingSaved'))
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
      <div>
        <p class="font-semibold">
          {{ current.specialization }} · {{ t('catalog.year', { n: current.studyYear }) }}
          <span class="mono ml-1 text-xs whitespace-nowrap font-normal text-muted">{{ current.academicYear }}</span>
        </p>
        <p class="mt-1 text-sm text-muted">
          {{
            current.subgroup
              ? t('group.subgroupSince', { subgroup: current.subgroup, since: date(current.since) })
              : t('group.since', { since: date(current.since) })
          }}
        </p>
      </div>
      <button v-if="!leads" class="btn btn-outline" @click="claim(current.groupId)">
        {{ t('group.claim') }}
      </button>
      <span v-else class="tag bg-mask text-on-mask">{{ t('group.leader') }}</span>
    </section>
    <section v-else class="card mt-6 grid gap-4">
      <p class="text-sm text-muted">{{ t('group.noGroup') }}</p>
      <div class="field">
        <label for="claim-group">{{ t('group.claimOther') }}</label>
        <ComboSelect
          id="claim-group"
          v-model="claimGroupId"
          :options="groupOptions"
          :placeholder="t('group.pickGroup')"
        />
      </div>
      <button
        class="btn btn-outline justify-self-start"
        :disabled="!claimGroupId"
        @click="claim(claimGroupId!)"
      >
        {{ t('group.claimOther') }}
      </button>
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

    <TabsRoot v-if="leads" default-value="requests" class="mt-8">
      <TabsList
        class="flex gap-1 overflow-x-auto border-b border-line"
        :aria-label="t('group.leader')"
      >
        <TabsTrigger
          v-for="tab in ['members', 'requests', 'invites', 'teaching']"
          :key="tab"
          :value="tab"
          class="-mb-px min-h-11 border-b-2 border-transparent px-3 text-sm font-medium whitespace-nowrap text-muted data-[state=active]:border-copper data-[state=active]:text-ink"
        >
          {{ t(`group.tabs.${tab}`, { n: pending.requests.length }) }}
        </TabsTrigger>
      </TabsList>

      <TabsContent value="members" class="pt-4">
        <p
          v-for="m in roster"
          :key="m.email"
          class="flex flex-wrap items-center gap-2 border-b border-line py-2 text-sm"
        >
          <span class="break-all">{{ m.email }}</span>
          <span v-if="m.subgroup" class="mono text-xs text-muted">{{ m.subgroup }}</span>
          <span v-if="m.leader" class="tag bg-mask text-on-mask">{{ t('group.leader') }}</span>
        </p>
      </TabsContent>

      <TabsContent value="requests" class="pt-4">
        <p v-if="!pending.requests.length" class="text-sm text-muted">
          {{ t('group.noRequests') }}
        </p>
        <div
          v-for="r in pending.requests"
          :key="r.id"
          class="card mb-2 flex flex-wrap items-center gap-3"
        >
          <div class="min-w-0 flex-1 text-sm">
            <div class="font-semibold break-all">{{ r.email }}</div>
            <div class="text-muted">
              {{ t(`group.kind.${r.kind}`)
              }}<span v-if="r.subgroup"> · {{ t('group.subgroup') }} {{ r.subgroup }}</span>
            </div>
          </div>
          <button class="btn btn-primary btn-sm" @click="decide(r.id, 'approve')">
            {{ t('group.approve') }}
          </button>
          <button class="btn btn-ghost btn-sm text-muted" @click="decide(r.id, 'reject')">
            {{ t('group.reject') }}
          </button>
        </div>
        <p class="mono mt-3 text-xs text-muted">
          {{ t('group.occupancy', { members: pending.members, max: pending.maxSize }) }}
        </p>
      </TabsContent>

      <TabsContent value="invites" class="grid gap-4 pt-4">
        <div class="card grid gap-4">
          <h3 class="h2 text-base">{{ t('group.newInvite') }}</h3>
          <div class="grid grid-cols-2 gap-4">
            <label class="field" for="invite-days">
              {{ t('group.expiresInDays') }}
              <input
                id="invite-days"
                v-model.number="inviteDays"
                type="number"
                min="1"
                max="30"
                class="input"
              />
            </label>
            <label class="field" for="invite-uses">
              {{ t('group.maxUses') }}
              <input
                id="invite-uses"
                v-model.number="inviteUses"
                type="number"
                min="1"
                max="200"
                class="input"
              />
            </label>
          </div>
          <button
            class="btn btn-primary justify-self-start"
            @click="createInvite(current!.groupId)"
          >
            {{ t('group.generate') }}
          </button>
          <AppNotice v-if="newInviteLink" kind="warn" class="my-0">
            {{ t('group.inviteOnce') }}
            <code class="mono mt-1 block text-xs break-all select-all">{{ newInviteLink }}</code>
          </AppNotice>
        </div>
        <h3 class="eyebrow">{{ t('group.activeInvites') }}</h3>
        <p
          v-for="i in invites"
          :key="i.id"
          class="mono flex items-center justify-between gap-2 border-b border-line py-1 text-xs"
        >
          {{ t('group.inviteLine', { uses: i.uses, max: i.maxUses, until: date(i.expiresAt) }) }}
          <button class="btn btn-ghost btn-sm font-sans" @click="deactivate(i.id)">
            {{ t('group.deactivate') }}
          </button>
        </p>
      </TabsContent>

      <TabsContent value="teaching" class="pt-4">
        <p v-if="!teaching.slots.length" class="text-sm text-muted">{{ t('group.noTeaching') }}</p>
        <template
          v-for="(slot, index) in teaching.slots"
          :key="`${slot.offeringId}-${slot.component}-${slot.subgroupId}`"
        >
          <h3 v-if="isNewCourse(index)" class="h2 mt-5 text-base">
            {{ slot.title }}
            <span class="tag ml-1 bg-board text-muted">{{
              t(`group.component.${slot.component}`)
            }}</span>
          </h3>
          <div class="field mt-3">
            <label :for="`teacher-${index}`">
              {{
                slot.subgroup ? `${t('group.subgroup')} ${slot.subgroup}` : t('group.wholeGroup')
              }}
            </label>
            <ComboSelect
              :id="`teacher-${index}`"
              v-model="slot.professorId"
              :options="professorOptions"
              :placeholder="t('group.pickProfessor')"
              clearable
            />
          </div>
        </template>
        <button
          v-if="teaching.slots.length"
          class="btn btn-primary mt-6"
          @click="saveTeaching(current!.groupId)"
        >
          {{ t('admin.save') }}
        </button>
      </TabsContent>
    </TabsRoot>
  </main>
</template>
