<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check, Copy, Share2 } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import { api } from '@/api'
import { useRun } from '@/group'
import { readStored, writeStored } from '@/storage'

interface Invite {
  id: number
  expiresAt: string
  maxUses: number
  uses: number
}

const props = defineProps<{ groupId: number; groupCode: string }>()
const { t, d } = useI18n()
const { error, run } = useRun()
const defaults = { days: 7, uses: 30 }
const invites = ref<Invite[]>([])
const links = ref<Record<number, string>>(storedLinks())
const days = ref(defaults.days)
const uses = ref(defaults.uses)
const copied = ref(false)
const canShare = !!navigator.share

const current = computed(() => invites.value.filter((invite) => links.value[invite.id]).at(-1))
const others = computed(() => invites.value.filter((invite) => invite !== current.value))

function storedLinks(): Record<number, string> {
  try {
    return JSON.parse(readStored('group-invite-links') ?? '{}')
  } catch {
    return {}
  }
}

function line(invite: Invite) {
  return t('group.inviteLine', {
    uses: invite.uses,
    max: invite.maxUses,
    until: d(new Date(invite.expiresAt), 'short'),
  })
}

async function load() {
  invites.value = await api<Invite[]>('GET', `/api/groups/${props.groupId}/invites`)
  links.value = Object.fromEntries(
    invites.value.filter((i) => links.value[i.id]).map((i) => [i.id, links.value[i.id]!]),
  )
  writeStored('group-invite-links', JSON.stringify(links.value))
}

function create(forDays: number, maxUses: number) {
  return run(async () => {
    const created = await api<{ id: number; token: string }>(
      'POST',
      `/api/groups/${props.groupId}/invites`,
      { days: forDays, maxUses },
    )
    links.value[created.id] = `${location.origin}/join/${created.token}`
    await load()
    await copy()
  })
}

async function copy() {
  try {
    await navigator.clipboard.writeText(links.value[current.value!.id]!)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    copied.value = false
  }
}

function share() {
  return navigator
    .share({
      text: t('group.shareText', { code: props.groupCode }),
      url: links.value[current.value!.id],
    })
    .catch(() => undefined)
}

function deactivate(inviteId: number) {
  return run(async () => {
    await api('PATCH', `/api/groups/invites/${inviteId}`)
    await load()
  })
}

onMounted(() => run(load))
</script>

<template>
  <section class="card grid gap-3">
    <h2 class="h2">{{ t('group.inviteTitle') }}</h2>
    <AppNotice v-if="error" kind="error" class="my-0">{{ error }}</AppNotice>
    <template v-if="current">
      <code class="mono block rounded-md bg-board px-3 py-2 text-xs break-all select-all">{{
        links[current.id]
      }}</code>
      <div class="grid gap-2" :class="{ 'grid-cols-2': canShare }">
        <button class="btn btn-primary" @click="copy">
          <component :is="copied ? Check : Copy" class="size-4" aria-hidden="true" />
          <span aria-live="polite">{{ copied ? t('recovery.copied') : t('group.copyLink') }}</span>
        </button>
        <button v-if="canShare" class="btn btn-outline" @click="share">
          <Share2 class="size-4" aria-hidden="true" />
          {{ t('timetable.share.share') }}
        </button>
      </div>
      <p class="mono flex items-center justify-between gap-2 text-xs text-muted">
        {{ line(current) }}
        <button class="btn btn-ghost btn-sm font-sans" @click="deactivate(current.id)">
          {{ t('group.deactivate') }}
        </button>
      </p>
    </template>
    <template v-else>
      <button class="btn btn-primary" @click="create(defaults.days, defaults.uses)">
        {{ t('group.createInvite') }}
      </button>
      <p class="text-sm text-muted">{{ t('group.inviteDefaults', defaults) }}</p>
    </template>

    <details class="border-t border-dashed border-line">
      <summary class="flex min-h-11 cursor-pointer items-center text-sm font-medium text-mask">
        {{ t('group.customInvite') }}
      </summary>
      <form class="grid grid-cols-2 gap-3 pb-1" @submit.prevent="create(days, uses)">
        <label class="field" for="invite-days">
          {{ t('group.expiresInDays') }}
          <input
            id="invite-days"
            v-model.number="days"
            type="number"
            inputmode="numeric"
            min="1"
            max="30"
            required
            class="input"
          />
        </label>
        <label class="field" for="invite-uses">
          {{ t('group.maxUses') }}
          <input
            id="invite-uses"
            v-model.number="uses"
            type="number"
            inputmode="numeric"
            min="1"
            max="200"
            required
            class="input"
          />
        </label>
        <button class="btn btn-outline col-span-2">{{ t('group.generate') }}</button>
      </form>
    </details>

    <div v-if="others.length" class="border-t border-dashed border-line pt-3">
      <h3 class="text-sm font-semibold font-sans">{{ t('group.otherInvites') }}</h3>
      <p class="text-xs text-muted">{{ t('group.otherInvitesHint') }}</p>
      <p
        v-for="invite in others"
        :key="invite.id"
        class="mono flex items-center justify-between gap-2 text-xs"
      >
        {{ line(invite) }}
        <button class="btn btn-ghost btn-sm font-sans" @click="deactivate(invite.id)">
          {{ t('group.deactivate') }}
        </button>
      </p>
    </div>
  </section>
</template>
