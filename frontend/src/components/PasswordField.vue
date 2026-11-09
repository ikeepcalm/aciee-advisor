<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Eye, EyeOff } from 'lucide-vue-next'

defineProps<{ id: string; autocomplete: string }>()
const model = defineModel<string>({ required: true })
const { t } = useI18n()
const shown = ref(false)
</script>

<template>
  <div class="relative">
    <input
      :id="id"
      v-model="model"
      :type="shown ? 'text' : 'password'"
      :autocomplete="autocomplete"
      class="input pr-12"
      required
    />
    <button
      type="button"
      class="btn btn-ghost btn-icon absolute inset-y-0 right-0 text-muted"
      :aria-label="shown ? t('common.hidePassword') : t('common.showPassword')"
      :aria-pressed="shown"
      @click="shown = !shown"
    >
      <EyeOff v-if="shown" class="size-5" />
      <Eye v-else class="size-5" />
    </button>
  </div>
</template>
