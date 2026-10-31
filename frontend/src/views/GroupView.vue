<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
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
const pending = ref<{ requests: JoinRequest[]; members: number; maxSize: number }>({ requests: [], members: 0, maxSize: 0 })
const roster = ref<RosterEntry[]>([])
const invites = ref<Invite[]>([])
const teaching = ref<{ slots: TeachingSlot[]; professors: { id: number; label: string }[] }>({ slots: [], professors: [] })
const inviteDays = ref(7)
const inviteUses = ref(30)
const newInviteLink = ref('')
const error = ref('')
const notice = ref('')

const today = new Date().toISOString().slice(0, 10)
const current = computed(() => memberships.value.find((m) => m.since <= today && (!m.until || today < m.until)))
const leads = computed(() => current.value?.leader === true)
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
  const mine = await api<{ memberships: Membership[]; requests: JoinRequest[] }>('GET', '/api/groups/mine')
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
    await loadLeaderTabs(current.value!.groupId)
  })
}

function createInvite(groupId: number) {
  return run(async () => {
    const created = await api<{ token: string }>('POST', `/api/groups/${groupId}/invites`, {
      days: inviteDays.value,
      maxUses: inviteUses.value,
    })
    newInviteLink.value = `${location.origin}/join/${created.token}`
    await loadLeaderTabs(groupId)
  })
}

function deactivate(inviteId: number) {
  return run(async () => {
    await api('PATCH', `/api/groups/invites/${inviteId}`)
    await loadLeaderTabs(current.value!.groupId)
  })
}

function saveTeaching(groupId: number) {
  return run(async () => {
    await api('PUT', `/api/groups/${groupId}/teaching`, teaching.value.slots)
    await loadLeaderTabs(groupId)
  }, t('group.teachingSaved'))
}

onMounted(() => run(load))
</script>

<template>
  <main>
    <h1>{{ t('group.title') }}</h1>
    <Message v-if="error" severity="error">{{ error }}</Message>
    <Message v-if="notice" severity="success">{{ notice }}</Message>

    <section v-if="current">
      <p>
        <strong>{{ t('group.summary', { code: current.groupCode, specialization: current.specialization, year: current.studyYear }) }}</strong>
        · {{ current.academicYear }}
      </p>
      <p v-if="current.subgroup">{{ t('group.subgroupSince', { subgroup: current.subgroup, since: date(current.since) }) }}</p>
      <p v-else>{{ t('group.since', { since: date(current.since) }) }}</p>
      <Button v-if="!leads" :label="t('group.claim')" outlined @click="claim(current.groupId)" />
    </section>
    <section v-else>
      <p>{{ t('group.noGroup') }}</p>
      <label for="claim-group">{{ t('group.claimOther') }}</label>
      <Select
        input-id="claim-group"
        v-model="claimGroupId"
        :options="groups"
        option-value="id"
        :option-label="(g: GroupSummary) => `${g.code} · ${g.specialization} · ${g.academicYear}`"
        :placeholder="t('group.pickGroup')"
        filter
        fluid
      />
      <Button :label="t('group.claimOther')" :disabled="!claimGroupId" outlined @click="claim(claimGroupId!)" />
    </section>

    <section v-if="requests.length">
      <h2>{{ t('group.myRequests') }}</h2>
      <p v-for="r in requests" :key="r.id">
        {{ r.groupCode }} ({{ r.academicYear }}) · {{ t(`group.kind.${r.kind}`) }} — {{ t(`group.status.${r.status}`) }}
      </p>
    </section>

    <Tabs v-if="leads" value="requests">
      <TabList>
        <Tab value="members">{{ t('group.tabs.members') }}</Tab>
        <Tab value="requests">{{ t('group.tabs.requests', { n: pending.requests.length }) }}</Tab>
        <Tab value="invites">{{ t('group.tabs.invites') }}</Tab>
        <Tab value="teaching">{{ t('group.tabs.teaching') }}</Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="members">
          <p v-for="m in roster" :key="m.email">
            {{ m.email }} <span v-if="m.subgroup">· {{ m.subgroup }}</span> <span v-if="m.leader">· {{ t('group.leader') }}</span>
          </p>
        </TabPanel>
        <TabPanel value="requests">
          <p v-if="!pending.requests.length">{{ t('group.noRequests') }}</p>
          <div v-for="r in pending.requests" :key="r.id" class="card">
            <div>{{ r.email }}</div>
            <div>{{ t(`group.kind.${r.kind}`) }}<span v-if="r.subgroup"> · {{ t('group.subgroup') }} {{ r.subgroup }}</span></div>
            <Button :label="t('group.approve')" size="small" @click="decide(r.id, 'approve')" />
            <Button :label="t('group.reject')" size="small" severity="secondary" text @click="decide(r.id, 'reject')" />
          </div>
          <p>{{ t('group.occupancy', { members: pending.members, max: pending.maxSize }) }}</p>
        </TabPanel>
        <TabPanel value="invites">
          <h3>{{ t('group.newInvite') }}</h3>
          <label for="invite-days">{{ t('group.expiresInDays') }}</label>
          <InputNumber input-id="invite-days" v-model="inviteDays" :min="1" :max="30" fluid />
          <label for="invite-uses">{{ t('group.maxUses') }}</label>
          <InputNumber input-id="invite-uses" v-model="inviteUses" :min="1" :max="200" fluid />
          <Button :label="t('group.generate')" @click="createInvite(current!.groupId)" />
          <Message v-if="newInviteLink" severity="warn">
            {{ t('group.inviteOnce') }}<br /><code>{{ newInviteLink }}</code>
          </Message>
          <h3>{{ t('group.activeInvites') }}</h3>
          <p v-for="i in invites" :key="i.id">
            {{ t('group.inviteLine', { uses: i.uses, max: i.maxUses, until: date(i.expiresAt) }) }}
            <Button :label="t('group.deactivate')" size="small" text @click="deactivate(i.id)" />
          </p>
        </TabPanel>
        <TabPanel value="teaching">
          <p v-if="!teaching.slots.length">{{ t('group.noTeaching') }}</p>
          <template v-for="(slot, index) in teaching.slots" :key="`${slot.offeringId}-${slot.component}-${slot.subgroupId}`">
            <h3 v-if="isNewCourse(index)">{{ slot.title }} · {{ t(`group.component.${slot.component}`) }}</h3>
            <label :for="`teacher-${index}`">
              {{ slot.subgroup ? `${t('group.subgroup')} ${slot.subgroup}` : t('group.wholeGroup') }}
            </label>
            <Select
              v-model="slot.professorId"
              :input-id="`teacher-${index}`"
              :options="teaching.professors"
              option-value="id"
              option-label="label"
              :placeholder="t('group.pickProfessor')"
              show-clear
              filter
              fluid
            />
          </template>
          <Button v-if="teaching.slots.length" :label="t('admin.save')" @click="saveTeaching(current!.groupId)" />
        </TabPanel>
      </TabPanels>
    </Tabs>
  </main>
</template>

<style scoped>
section,
.card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
</style>
