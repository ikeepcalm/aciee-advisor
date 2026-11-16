<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, X } from 'lucide-vue-next'
import { formatRange, formatRanges, parseRanges, type DateRange } from '@/dateRange'

const props = defineProps<{ id: string; multiple?: boolean; required?: boolean }>()
const model = defineModel<string | null | undefined>({ required: true })
const { t } = useI18n()

const parsed = parseRanges(model.value)
const ranges = ref<DateRange[]>(parsed.length || !props.multiple ? parsed : [])
if (!ranges.value.length && !props.multiple) ranges.value.push({ from: '', to: '' })

watch(
  ranges,
  (value) => {
    model.value = props.multiple ? formatRanges(value) : formatRange(value[0]!)
  },
  { deep: true },
)
</script>

<template>
  <div :id="id" class="grid gap-2">
    <div v-for="(range, i) in ranges" :key="i" class="flex items-end gap-2">
      <label class="field flex-1 text-xs text-muted">
        {{ t('admin.rangeFrom') }}
        <input v-model="range.from" type="date" class="input" :required="required" />
      </label>
      <label class="field flex-1 text-xs text-muted">
        {{ t('admin.rangeTo') }}
        <input v-model="range.to" type="date" class="input" :min="range.from || undefined" />
      </label>
      <button
        v-if="multiple"
        type="button"
        class="btn btn-ghost btn-sm min-h-11 min-w-11"
        :aria-label="t('admin.rangeRemove')"
        @click="ranges.splice(i, 1)"
      >
        <X class="size-4" aria-hidden="true" />
      </button>
    </div>
    <button
      v-if="multiple"
      type="button"
      class="btn btn-outline btn-sm justify-self-start"
      @click="ranges.push({ from: '', to: '' })"
    >
      <Plus class="size-4" aria-hidden="true" />
      {{ t('admin.rangeAdd') }}
    </button>
  </div>
</template>
