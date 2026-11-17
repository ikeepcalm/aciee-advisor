<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{ target: Element | null }>()
const rect = ref<DOMRect | null>(null)
let frame = 0

function track() {
  rect.value = props.target?.isConnected ? props.target.getBoundingClientRect() : null
  frame = props.target ? requestAnimationFrame(track) : 0
}

watch(
  () => props.target,
  () => {
    cancelAnimationFrame(frame)
    track()
  },
  { immediate: true },
)

onBeforeUnmount(() => cancelAnimationFrame(frame))
</script>

<template>
  <div
    class="spot"
    :class="{ 'is-on': rect }"
    :style="
      rect
        ? {
            top: `${rect.top - 10}px`,
            left: `${rect.left - 10}px`,
            width: `${rect.width + 20}px`,
            height: `${rect.height + 20}px`,
          }
        : undefined
    "
  />
</template>

<style scoped>
.spot {
  position: fixed;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  border-radius: 0.9rem;
  box-shadow: 0 0 0 200vmax rgb(14 18 28 / 0);
  transition:
    box-shadow 450ms ease,
    top 450ms cubic-bezier(0.65, 0, 0.35, 1),
    left 450ms cubic-bezier(0.65, 0, 0.35, 1),
    width 450ms cubic-bezier(0.65, 0, 0.35, 1),
    height 450ms cubic-bezier(0.65, 0, 0.35, 1);
}
.spot.is-on {
  box-shadow: 0 0 0 200vmax rgb(14 18 28 / 0.45);
}
</style>
