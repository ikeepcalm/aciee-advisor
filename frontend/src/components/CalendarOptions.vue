<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Calendar, Check, Copy, Smartphone } from 'lucide-vue-next'

const props = defineProps<{ feedUrl: string | null; group: string }>()
const { t } = useI18n()
const copied = ref(false)
const prefersApple = /iPhone|iPad|Macintosh/.test(navigator.userAgent)

const webcalUrl = computed(() => props.feedUrl?.replace(/^https?:/, 'webcal:') ?? '')
const googleUrl = computed(
  () => `https://calendar.google.com/calendar/render?cid=${encodeURIComponent(webcalUrl.value)}`,
)

async function copyLink() {
  try {
    await navigator.clipboard.writeText(props.feedUrl!)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    copied.value = false
  }
}
</script>

<template>
  <p v-if="!feedUrl">{{ t('timetable.calendar.unavailable') }}</p>
  <div v-else class="flex flex-col gap-3">
    <p>{{ t('timetable.calendar.lede', { group }) }}</p>
    <div class="flex flex-col gap-2" :class="{ 'flex-col-reverse': prefersApple }">
      <a
        class="btn"
        :class="prefersApple ? 'btn-outline' : 'btn-primary'"
        :href="googleUrl"
        target="_blank"
        rel="noopener"
      >
        <Calendar class="size-4" aria-hidden="true" />
        {{ t('timetable.calendar.google') }}
      </a>
      <a class="btn" :class="prefersApple ? 'btn-primary' : 'btn-outline'" :href="webcalUrl">
        <Smartphone class="size-4" aria-hidden="true" />
        {{ t('timetable.calendar.apple') }}
      </a>
    </div>
    <button type="button" class="btn btn-ghost" @click="copyLink">
      <component :is="copied ? Check : Copy" class="size-4" aria-hidden="true" />
      <span aria-live="polite">
        {{ copied ? t('timetable.calendar.copied') : t('timetable.calendar.copy') }}
      </span>
    </button>
    <p class="text-sm text-muted">{{ t('timetable.calendar.googleNote') }}</p>
  </div>
</template>
