<script setup lang="ts" generic="T extends string | number | null | undefined">
import { useId } from 'vue'

withDefaults(
  defineProps<{
    options: { value: NonNullable<T>; label: string }[]
    nullLabel?: string
    scale?: boolean
    size?: 'md' | 'sm'
  }>(),
  { size: 'md' },
)
const model = defineModel<T>({ required: true })
const name = useId()
</script>

<template>
  <div class="flex flex-wrap gap-1.5" :class="{ scale }" role="radiogroup">
    <label
      v-for="o in options"
      :key="o.value"
      class="chip has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-copper"
      :class="[{ 'chip-on': model === o.value }, size === 'sm' ? 'min-w-11 px-3 text-xs' : '']"
    >
      <input
        type="radio"
        class="sr-only"
        :name="name"
        :value="o.value"
        :checked="model === o.value"
        @click="model = o.value"
      />
      {{ o.label }}
    </label>
    <label
      v-if="nullLabel"
      class="chip null-chip has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-copper"
      :class="[
        model == null ? 'chip-none' : 'text-muted',
        size === 'sm' ? 'min-w-11 px-3 text-xs' : '',
      ]"
    >
      <input
        type="radio"
        class="sr-only"
        :name="name"
        value=""
        :checked="model == null"
        @click="model = null as T"
      />
      {{ nullLabel }}
    </label>
  </div>
</template>

<style scoped>
@media (min-width: 360px) and (max-width: 389.98px) {
  .scale {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 0.25rem;
  }

  .scale .chip {
    min-width: 0;
    padding-inline: 0;
  }

  .scale .null-chip {
    font-size: 0.625rem;
    line-height: 1.1;
    text-align: center;
  }
}
</style>
