<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Flag } from 'lucide-vue-next'
import { anonApi, describeError, lookups, type Option } from '@/api'

const props = defineProps<{ postId: string }>()
const { t } = useI18n()
const open = ref(false)
const reasons = ref<Option[]>([])
const reason = ref('')
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
  <span v-if="state === 'sent'">{{ t('report.thanks') }}</span>
  <span v-else-if="open" class="inline-flex flex-wrap items-center gap-2">
    <select v-model="reason" class="input w-auto py-0 text-xs" :aria-label="t('report.reason')">
      <option value="" disabled>{{ t('report.reason') }}</option>
      <option v-for="r in reasons" :key="r.code" :value="r.code">{{ r.label }}</option>
    </select>
    <button class="btn btn-outline" :disabled="!reason" @click="send">
      {{ t('report.send') }}
    </button>
    <span v-if="error" class="text-danger">{{ error }}</span>
  </span>
  <button v-else class="btn btn-ghost -my-3 px-2 text-muted" @click="open = true">
    <Flag class="size-3.5" aria-hidden="true" /> {{ t('report.action') }}
  </button>
</template>
