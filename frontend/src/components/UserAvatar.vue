<script setup lang="ts">
import { computed } from 'vue'
import { initials } from '@/person'

const props = defineProps<{ name?: string | null; email: string }>()
const letters = computed(() => initials(props.name, props.email))
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center justify-center rounded-full bg-mark font-bold text-on-mark"
    aria-hidden="true"
  >
    {{ letters }}
  </span>
</template>
