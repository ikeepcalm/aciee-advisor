<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight, Play, Undo2 } from 'lucide-vue-next'
import { reducedMotion } from '@/viewTransition'

const { t, locale } = useI18n()
const language = computed(() => (locale.value === 'ro' ? 'ro' : 'en'))
const tour = ref(false)
const manual = ref(reducedMotion.matches)
const video = ref<HTMLVideoElement>()
const source = computed(() => `/preview/${tour.value ? 'tour' : 'loop'}-${language.value}.mp4`)

function play() {
  video.value?.play().catch((error) => (manual.value ||= error.name === 'NotAllowedError'))
}

function onVisibility() {
  if (tour.value) return
  if (document.hidden) {
    video.value?.pause()
  } else if (!manual.value) {
    play()
  }
}

watch(video, (element) => {
  if (element && (tour.value || !manual.value)) play()
})

onMounted(() => document.addEventListener('visibilitychange', onVisibility))
onUnmounted(() => document.removeEventListener('visibilitychange', onVisibility))
</script>

<template>
  <main
    class="page-wide grid items-center gap-x-12 gap-y-5 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.5fr)] lg:pt-14"
  >
    <div class="lg:self-end">
      <span class="eyebrow">{{ t('home.eyebrow') }}</span>
      <h1 class="display text-[clamp(2.125rem,8.5vw,3rem)] leading-[1.22]">
        {{ t('preview.title') }}<br />
        <span class="hl">{{ t('preview.titleMark') }}</span>
      </h1>
    </div>

    <figure class="m-0 lg:col-start-2 lg:row-span-2 lg:row-start-1">
      <div class="frame">
        <video
          :key="source"
          ref="video"
          class="block aspect-video w-full rounded-sm bg-board"
          width="1280"
          height="720"
          :src="source"
          :poster="`/preview/poster-${language}.jpg`"
          :aria-label="t('preview.video')"
          :autoplay="tour || !manual"
          :loop="!tour"
          :controls="tour || manual"
          muted
          playsinline
          preload="metadata"
          @ended="tour = false"
        />
      </div>
      <figcaption class="mono mt-3 text-center text-xs text-muted">
        {{ t('preview.note') }}
      </figcaption>
    </figure>

    <div class="lg:self-start">
      <p class="lede mt-0 text-base md:text-lg">{{ t('preview.lede') }}</p>
      <div class="mt-5 grid gap-2 sm:flex sm:flex-wrap">
        <RouterLink :to="{ name: 'home' }" class="btn btn-primary min-h-12 text-base">
          {{ t('preview.open') }}
          <ArrowRight class="size-4" aria-hidden="true" />
        </RouterLink>
        <button type="button" class="btn btn-outline min-h-12" @click="tour = !tour">
          <component :is="tour ? Undo2 : Play" class="size-4" aria-hidden="true" />
          {{ t(tour ? 'preview.short' : 'preview.tour') }}
        </button>
      </div>
    </div>
  </main>
</template>

<style scoped>
.frame {
  position: relative;
  padding: 0.5rem;
  border: 1.5px solid var(--ink);
  border-radius: var(--wobble-alt);
  background: var(--panel);
  box-shadow: 4px 5px 0 color-mix(in srgb, var(--ink) 28%, transparent);
}

.frame::before {
  content: '';
  position: absolute;
  top: -0.8rem;
  left: 50%;
  width: 6rem;
  height: 1.5rem;
  translate: -50%;
  rotate: -2.5deg;
  background: color-mix(in srgb, var(--mark) 85%, transparent);
}

@media (min-width: 64rem) {
  .frame {
    rotate: 0.6deg;
  }
}
</style>
