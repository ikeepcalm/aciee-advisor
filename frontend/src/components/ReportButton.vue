<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Select from 'primevue/select'
import { anonApi, describeError, lookups, type Option } from '@/api'

const props = defineProps<{ postId: string }>()
const { t } = useI18n()
const open = ref(false)
const reasons = ref<Option[]>([])
const reason = ref<string | null>(null)
const state = ref<'idle' | 'sent'>('idle')
const error = ref('')

onMounted(async () => (reasons.value = (await lookups()).reportReasons))

async function send() {
  error.value = ''
  try {
    await anonApi('POST', `/anon/posts/${props.postId}/reports`, { reasonCode: reason.value })
    state.value = 'sent'
  } catch (e) {
    error.value = describeError(t, e)
  }
}
</script>

<template>
  <span v-if="state === 'sent'" class="muted">{{ t('report.thanks') }}</span>
  <span v-else-if="open" class="report">
    <Select
      v-model="reason"
      :options="reasons"
      option-label="label"
      option-value="code"
      :placeholder="t('report.reason')"
      size="small"
    />
    <Button :label="t('report.send')" size="small" :disabled="!reason" @click="send" />
    <span v-if="error" class="muted">{{ error }}</span>
  </span>
  <Button v-else :label="t('report.action')" link size="small" @click="open = true" />
</template>

<style scoped>
.report {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}
</style>
