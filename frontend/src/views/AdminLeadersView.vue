<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Message from 'primevue/message'
import AdminSections from '@/components/AdminSections.vue'
import { api, describeError } from '@/api'

interface Claim {
  id: number
  email: string
  groupCode: string
  academicYear: string
  memberSince: string | null
}
interface Leader {
  membershipId: number
  email: string
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
  <main>
    <h1>{{ t('admin.leadersTitle') }}</h1>
    <AdminSections />
    <Message v-if="error" severity="error">{{ error }}</Message>

    <h2>{{ t('admin.pendingClaims') }}</h2>
    <p v-if="!claims.length">{{ t('admin.noClaims') }}</p>
    <div v-for="c in claims" :key="c.id" class="card">
      <div>{{ c.email }}</div>
      <div>{{ c.groupCode }} · {{ c.academicYear }}</div>
      <div>
        {{ c.memberSince ? t('admin.memberSince', { since: d(new Date(c.memberSince), 'short') }) : t('admin.notMemberYet') }}
      </div>
      <Button :label="t('group.approve')" size="small" @click="act('POST', `/api/admin/leaders/claims/${c.id}/approve`)" />
      <Button :label="t('group.reject')" size="small" severity="secondary" text @click="act('POST', `/api/admin/leaders/claims/${c.id}/reject`)" />
    </div>

    <h2>{{ t('admin.activeLeaders') }}</h2>
    <p v-for="l in leaders" :key="l.membershipId">
      {{ l.groupCode }} · {{ l.email }}
      <Button :label="t('admin.revoke')" size="small" severity="danger" text @click="act('DELETE', `/api/admin/leaders/${l.membershipId}`)" />
    </p>
  </main>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
</style>
