<script setup lang="ts">
import { BUBBLE, BUBBLE_LETTER } from '@/brand'
</script>

<template>
  <svg viewBox="0 0 32 32" aria-hidden="true" fill="none">
    <path
      :d="BUBBLE"
      fill="var(--mark)"
      stroke="var(--ink)"
      stroke-width="2"
      stroke-linejoin="round"
    />
    <path
      :d="BUBBLE_LETTER"
      stroke="var(--on-mark)"
      stroke-width="2.2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>
