<script setup lang="ts" generic="T extends string | number | null | undefined">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ComboboxAnchor,
  ComboboxCancel,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxPortal,
  ComboboxRoot,
  ComboboxTrigger,
  ComboboxViewport,
} from 'reka-ui'
import { ChevronDown, X } from 'lucide-vue-next'

// A searchable select. With `remote`, filtering is left to the parent via the `search` event.
const props = defineProps<{
  options: { value: NonNullable<T>; label: string }[]
  id?: string
  placeholder?: string
  clearable?: boolean
  remote?: boolean
}>()
const emit = defineEmits<{ search: [term: string] }>()
const model = defineModel<T>({ required: true })
const { t } = useI18n()

const labels = computed(
  () => new Map<unknown, string>(props.options.map((o) => [o.value, o.label])),
)
const inner = computed({
  get: () => (model.value ?? undefined) as string | number | undefined,
  set: (v) => (model.value = (v ?? null) as T),
})
</script>

<template>
  <ComboboxRoot v-model="inner" :ignore-filter="remote" reset-search-term-on-blur open-on-click>
    <ComboboxAnchor class="input flex items-center gap-1 px-0">
      <ComboboxInput
        :id="id"
        :placeholder="placeholder"
        :display-value="(v) => labels.get(v) ?? ''"
        class="min-h-11 min-w-0 flex-1 bg-transparent px-3 outline-none"
        @update:model-value="emit('search', $event)"
      />
      <ComboboxCancel
        v-if="clearable && model !== null"
        class="btn btn-ghost btn-icon text-muted"
        :aria-label="t('common.cancel')"
        @click="inner = undefined"
      >
        <X class="size-4" />
      </ComboboxCancel>
      <ComboboxTrigger class="btn btn-ghost btn-icon text-muted" :aria-label="placeholder">
        <ChevronDown class="size-4" />
      </ComboboxTrigger>
    </ComboboxAnchor>
    <ComboboxPortal>
      <ComboboxContent
        position="popper"
        :side-offset="4"
        class="z-50 w-(--reka-combobox-trigger-width) overflow-hidden rounded-md border border-line bg-panel text-ink shadow-modal"
      >
        <ComboboxViewport class="max-h-72 overflow-y-auto p-1">
          <ComboboxEmpty class="px-3 py-2 text-sm text-muted">
            {{ t('common.noResults') }}
          </ComboboxEmpty>
          <ComboboxItem
            v-for="o in options"
            :key="o.value"
            :value="o.value"
            :text-value="o.label"
            class="cursor-pointer rounded-sm px-3 py-2.5 text-sm outline-none data-[highlighted]:bg-board data-[state=checked]:font-semibold data-[state=checked]:text-mask"
          >
            {{ o.label }}
          </ComboboxItem>
        </ComboboxViewport>
      </ComboboxContent>
    </ComboboxPortal>
  </ComboboxRoot>
</template>
