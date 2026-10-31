<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Message from 'primevue/message'
import RadioButton from 'primevue/radiobutton'
import { api, describeError } from '@/api'

interface Preview {
  group: { code: string; specialization: string; studyYear: number; academicYear: string }
  subgroups: { id: number; label: string }[]
}

const { t } = useI18n()
const token = useRoute().params.token as string
const preview = ref<Preview | null>(null)
const subgroupId = ref<number | null>(null)
const error = ref('')
const sent = ref(false)
const busy = ref(false)

onMounted(async () => {
  try {
    preview.value = await api<Preview>('GET', `/api/groups/invites/${encodeURIComponent(token)}`)
  } catch (e) {
    error.value = describeError(t, e)
  }
})

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await api('POST', '/api/groups/join', { token, subgroupId: subgroupId.value })
    sent.value = true
  } catch (e) {
    error.value = describeError(t, e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main>
    <h1>{{ t('group.joinTitle') }}</h1>
    <Message v-if="error" severity="error">{{ error }}</Message>
    <form v-if="preview && !sent" @submit.prevent="submit">
      <p>
        <strong>{{ t('group.summary', { code: preview.group.code, specialization: preview.group.specialization, year: preview.group.studyYear }) }}</strong>
        <br />{{ preview.group.academicYear }}
      </p>
      <fieldset v-if="preview.subgroups.length">
        <legend>{{ t('group.subgroup') }}</legend>
        <span v-for="s in preview.subgroups" :key="s.id">
          <RadioButton v-model="subgroupId" :input-id="`subgroup-${s.id}`" :value="s.id" required />
          <label :for="`subgroup-${s.id}`">{{ s.label }}</label>
        </span>
      </fieldset>
      <Button type="submit" :label="t('group.sendRequest')" :loading="busy" fluid />
      <p>{{ t('group.joinHint') }}</p>
    </form>
    <Message v-if="sent" severity="success">{{ t('group.joinSent') }}</Message>
  </main>
</template>
