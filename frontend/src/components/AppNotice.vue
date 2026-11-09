<script setup lang="ts">
withDefaults(defineProps<{ kind?: 'error' | 'info' | 'success' | 'warn' }>(), { kind: 'info' })

const colors = {
  error: 'border-danger text-danger',
  info: 'border-line text-ink',
  success: 'border-mask text-ink',
  warn: 'border-copper text-ink',
}
</script>

<template>
  <div
    :role="kind === 'error' ? 'alert' : 'status'"
    class="my-3 rounded-md border-l-[3px] bg-panel px-4 py-3 text-sm leading-snug shadow-[inset_0_0_0_1px_var(--line)]"
    :class="colors[kind]"
  >
    <slot />
  </div>
</template>
