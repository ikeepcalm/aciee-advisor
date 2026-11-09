<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  DialogClose,
  DialogContent,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle,
} from 'reka-ui'
import { X } from 'lucide-vue-next'

// `sheet` slides up from the bottom on phones and in from the right on desktop; `modal` is centred.
withDefaults(defineProps<{ title: string; side?: 'sheet' | 'modal' }>(), { side: 'sheet' })
const open = defineModel<boolean>({ required: true })
const { t } = useI18n()

const placement = {
  sheet:
    'inset-x-0 bottom-0 max-h-[85dvh] rounded-t-xl shadow-sheet data-[state=open]:animate-[rise_250ms_ease-out] md:inset-y-0 md:right-0 md:left-auto md:w-96 md:max-h-none md:rounded-none md:rounded-l-xl md:data-[state=open]:animate-[slide_250ms_ease-out]',
  modal:
    'inset-x-4 top-1/2 mx-auto max-h-[85dvh] w-auto max-w-lg -translate-y-1/2 rounded-xl shadow-modal data-[state=open]:animate-[fade_200ms_ease-out]',
}
</script>

<template>
  <DialogRoot v-model:open="open">
    <DialogPortal>
      <DialogOverlay class="fixed inset-0 z-40 bg-ink/40 backdrop-blur-[2px]" />
      <DialogContent
        class="fixed z-50 flex flex-col overflow-hidden border border-line bg-panel text-ink focus:outline-none"
        :class="placement[side]"
      >
        <header class="flex items-center justify-between gap-4 border-b border-line px-5 py-3">
          <DialogTitle class="h2">{{ title }}</DialogTitle>
          <DialogClose class="btn btn-ghost btn-icon -mr-2" :aria-label="t('common.close')">
            <X class="size-5" />
          </DialogClose>
        </header>
        <div
          class="min-h-0 flex-1 overflow-y-auto overscroll-contain p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))]"
        >
          <slot />
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<style>
@keyframes rise {
  from {
    translate: 0 100%;
  }
}

@keyframes slide {
  from {
    translate: 100% 0;
  }
}

@keyframes fade {
  from {
    opacity: 0;
    scale: 0.97;
  }
}
</style>
