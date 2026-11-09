<script setup lang="ts" generic="T extends string | number | null | undefined">
import { useId } from 'vue'

// Radio chips. `clearable` lets a second tap on the chosen chip unset it (scores may stay N/A).
withDefaults(
  defineProps<{
    options: { value: NonNullable<T>; label: string }[]
    clearable?: boolean
    size?: 'md' | 'sm'
  }>(),
  { size: 'md' },
)
const model = defineModel<T>({ required: true })
const name = useId()

function pick(value: NonNullable<T>, clearable?: boolean) {
  model.value = clearable && model.value === value ? (null as T) : value
}
</script>

<template>
  <div class="flex flex-wrap gap-1.5" role="radiogroup">
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
        @click="pick(o.value, clearable)"
        @keydown.space.prevent="pick(o.value, clearable)"
      />
      {{ o.label }}
    </label>
  </div>
</template>
