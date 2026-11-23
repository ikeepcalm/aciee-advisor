<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{ name: string; photo: string | null }>()
const broken = ref(false)
const initials = computed(() => {
  const words = props.name.split(/\s+/).filter(Boolean)
  return [words[0], words.length > 1 ? words.at(-1) : '']
    .map((word) => word?.[0] ?? '')
    .join('')
    .toUpperCase()
})
</script>

<template>
  <span class="avatar" aria-hidden="true">
    <img
      v-if="photo && !broken"
      :src="photo"
      alt=""
      loading="lazy"
      class="size-full object-cover object-top"
      @error="broken = true"
    />
    <svg v-else viewBox="0 0 100 100" class="initials size-full">
      <text x="50" y="54" text-anchor="middle" dominant-baseline="middle" font-size="44">
        {{ initials }}
      </text>
    </svg>
  </span>
</template>

<style scoped>
.avatar {
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1.5px solid var(--ink);
  border-radius: 47% 53% 44% 56% / 55% 46% 54% 45%;
  color: var(--on-mark);
  background: color-mix(in srgb, var(--mark) 70%, var(--panel));
  box-shadow: 2px 3px 0 color-mix(in srgb, var(--ink) 28%, transparent);
  rotate: -2deg;
}

.initials {
  font-family: var(--font-display);
  font-weight: 700;
  fill: currentColor;
  rotate: 2deg;
}
</style>
