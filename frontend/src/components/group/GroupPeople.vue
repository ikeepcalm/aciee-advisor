<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppNotice from '@/components/AppNotice.vue'
import GroupInvite from '@/components/group/GroupInvite.vue'
import { api } from '@/api'
import { useRun, type JoinRequest } from '@/group'

interface RosterEntry {
  email: string
  displayName: string | null
  subgroup: string | null
  since: string
  leader: boolean
}
interface Pending {
  requests: JoinRequest[]
  members: number
  maxSize: number
}

const props = defineProps<{ groupId: number; groupCode: string }>()
const emit = defineEmits<{ pending: [count: number] }>()
const { t } = useI18n()
const { error, run } = useRun()
const pending = ref<Pending>({ requests: [], members: 0, maxSize: 0 })
const roster = ref<RosterEntry[]>([])
const deciding = ref<number | null>(null)

const subgroups = computed(() => {
  const bySubgroup = new Map<string | null, RosterEntry[]>()
  for (const member of roster.value) {
    bySubgroup.set(member.subgroup, [...(bySubgroup.get(member.subgroup) ?? []), member])
  }
  return [...bySubgroup]
})

async function load() {
  ;[pending.value, roster.value] = await Promise.all([
    api<Pending>('GET', `/api/groups/${props.groupId}/requests`),
    api<RosterEntry[]>('GET', `/api/groups/${props.groupId}/members`),
  ])
  emit('pending', pending.value.requests.length)
}

async function decide(requestId: number, decision: 'approve' | 'reject') {
  deciding.value = requestId
  await run(async () => {
    await api('POST', `/api/groups/requests/${requestId}/${decision}`)
    await load()
  })
  deciding.value = null
}

onMounted(() => run(load))
</script>

<template>
  <section>
    <h2 class="eyebrow">{{ t('group.requests') }}</h2>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <p v-if="!pending.requests.length" class="text-sm text-muted">{{ t('group.noRequests') }}</p>
    <article v-for="r in pending.requests" :key="r.id" class="card mb-3 grid gap-3">
      <div class="min-w-0">
        <p class="font-semibold [overflow-wrap:anywhere]">{{ r.displayName ?? r.email }}</p>
        <p v-if="r.displayName" class="text-xs text-muted [overflow-wrap:anywhere]">
          {{ r.email }}
        </p>
        <p class="mt-2 flex flex-wrap gap-1.5">
          <span v-if="r.subgroup" class="tag bg-mark text-on-mark"
            >{{ t('group.subgroup') }} {{ r.subgroup }}</span
          >
          <span v-else class="tag bg-board text-muted">{{ t('group.noSubgroup') }}</span>
          <span v-if="r.kind !== 'regular'" class="tag bg-board text-muted">{{
            t(`group.kind.${r.kind}`)
          }}</span>
        </p>
      </div>
      <div class="grid grid-cols-2 gap-2">
        <button
          class="btn btn-primary min-h-12"
          :disabled="deciding === r.id"
          @click="decide(r.id, 'approve')"
        >
          {{ t('group.approve') }}
        </button>
        <button
          class="btn btn-outline min-h-12"
          :disabled="deciding === r.id"
          @click="decide(r.id, 'reject')"
        >
          {{ t('group.reject') }}
        </button>
      </div>
    </article>
  </section>

  <GroupInvite :group-id="groupId" :group-code="groupCode" class="mt-6" />

  <section class="mt-8">
    <div class="flex flex-wrap items-baseline justify-between gap-2">
      <h2 class="eyebrow">{{ t('group.tabs.members') }}</h2>
      <span class="mono text-xs text-muted">{{
        t('group.occupancy', { members: pending.members, max: pending.maxSize })
      }}</span>
    </div>
    <div v-for="[subgroup, members] in subgroups" :key="subgroup ?? ''" class="mb-5">
      <h3 class="h2 text-lg">
        {{ subgroup ? `${t('group.subgroup')} ${subgroup}` : t('group.noSubgroup') }}
        <span class="mono ml-1 text-xs font-normal text-muted">{{ members.length }}</span>
      </h3>
      <p
        v-for="m in members"
        :key="m.email"
        class="flex flex-wrap items-center gap-x-2 border-b border-line py-2 text-sm"
      >
        <span class="[overflow-wrap:anywhere]">{{ m.displayName ?? m.email }}</span>
        <span v-if="m.displayName" class="text-xs text-muted [overflow-wrap:anywhere]">{{
          m.email
        }}</span>
        <span v-if="m.leader" class="tag bg-mask text-on-mask">{{ t('group.leader') }}</span>
      </p>
    </div>
  </section>
</template>
