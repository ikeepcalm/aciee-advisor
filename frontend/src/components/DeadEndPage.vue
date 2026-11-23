<script setup lang="ts">
defineProps<{ code: string; title: string; body?: string }>()
</script>

<template>
  <main class="page-narrow text-center">
    <div class="relative mx-auto mt-6 w-fit md:mt-0">
      <p class="code display text-[clamp(6.5rem,32vw,9.5rem)] leading-none">{{ code }}</p>
      <svg
        viewBox="0 0 120 70"
        class="absolute -top-7 -right-16 w-28"
        fill="none"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path
          d="M4 62C30 68 46 42 35 31S13 35 26 47s42 5 56-16"
          stroke="var(--copper)"
          stroke-width="2"
          stroke-dasharray="1 7"
        />
        <path
          d="M82 27 115 7 101 41 95 30Z"
          fill="var(--mark)"
          stroke="var(--ink)"
          stroke-width="2"
        />
        <path d="M95 30 115 7" stroke="var(--ink)" stroke-width="2" />
      </svg>
    </div>
    <h1 class="h1 mt-5">{{ title }}</h1>
    <p v-if="body" class="lede mx-auto">{{ body }}</p>
    <nav class="mt-6 grid grid-cols-2 gap-2">
      <slot />
    </nav>
  </main>
</template>

<style scoped>
.code {
  position: relative;
}

.code::after {
  content: '';
  position: absolute;
  inset: auto 0 -0.25rem;
  height: 0.9rem;
  background: var(--copper);
  mask: var(--scribble) center / 100% 100% no-repeat;
}
</style>
