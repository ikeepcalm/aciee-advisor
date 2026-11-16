<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminShell from '@/components/AdminShell.vue'
import AppNotice from '@/components/AppNotice.vue'
import { api, describeError } from '@/api'

interface Claim {
  id: number
  email: string
  displayName: string | null
  groupCode: string
  academicYear: string
  memberSince: string | null
}
interface Leader {
  membershipId: number
  email: string
  displayName: string | null
  groupCode: string
  academicYear: string
}

const { t, d } = useI18n()
const claims = ref<Claim[]>([])
const leaders = ref<Leader[]>([])
const error = ref('')

async function load() {
  const overview = await api<{ claims: Claim[]; leaders: Leader[] }>('GET', '/api/admin/leaders')
  claims.value = overview.claims
  leaders.value = overview.leaders
}

async function act(method: string, path: string) {
  error.value = ''
  try {
    await api(method, path)
    await load()
  } catch (e) {
    error.value = describeError(t, e)
  }
}

onMounted(() => load().catch((e) => (error.value = describeError(t, e))))
</script>

<template>
  <AdminShell :title="t('admin.leadersTitle')">
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>

    <h2 class="eyebrow mt-8 mb-3">{{ t('admin.pendingClaims') }}</h2>
    <p v-if="!claims.length" class="text-sm text-muted">{{ t('admin.noClaims') }}</p>
    <div
      v-for="c in claims"
      :key="c.id"
      class="card mb-2 flex flex-wrap items-center gap-x-3 gap-y-2 text-sm"
    >
      <div class="min-w-0 basis-full sm:flex-1 sm:basis-0">
        <div class="font-semibold [overflow-wrap:anywhere]">{{ c.displayName ?? c.email }}</div>
        <div v-if="c.displayName" class="text-xs text-muted [overflow-wrap:anywhere]">
          {{ c.email }}
        </div>
        <div class="text-muted">
          <strong class="text-ink">{{ c.groupCode }}</strong> ·
          <span class="mono text-xs">{{ c.academicYear }}</span> ·
          {{
            c.memberSince
              ? t('admin.memberSince', { since: d(new Date(c.memberSince), 'short') })
              : t('admin.notMemberYet')
          }}
        </div>
      </div>
      <button
        class="btn btn-primary btn-sm"
        @click="act('POST', `/api/admin/leaders/claims/${c.id}/approve`)"
      >
        {{ t('group.approve') }}
      </button>
      <button
        class="btn btn-ghost btn-sm text-muted"
        @click="act('POST', `/api/admin/leaders/claims/${c.id}/reject`)"
      >
        {{ t('group.reject') }}
      </button>
    </div>

    <h2 class="eyebrow mt-8 mb-3">{{ t('admin.activeLeaders') }}</h2>
    <p
      v-for="l in leaders"
      :key="l.membershipId"
      class="flex items-center gap-2 border-t border-line py-1 text-sm"
    >
      <strong>{{ l.groupCode }}</strong>
      <span class="min-w-0 flex-1 [overflow-wrap:anywhere]"
        >{{ l.displayName ?? l.email }}
        <span v-if="l.displayName" class="text-xs text-muted">{{ l.email }}</span></span
      >
      <button
        class="btn btn-danger btn-sm border-transparent"
        @click="act('DELETE', `/api/admin/leaders/${l.membershipId}`)"
      >
        {{ t('admin.revoke') }}
      </button>
    </p>
  </AdminShell>
</template>
