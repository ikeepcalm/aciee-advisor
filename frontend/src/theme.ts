import { computed, ref, watchEffect } from 'vue'
import { readStored, writeStored } from '@/storage'
import { animateSwap } from '@/viewTransition'

export type Theme = 'system' | 'light' | 'dark'

const KEY = 'theme'
const media = matchMedia('(prefers-color-scheme: dark)')
const systemDark = ref(media.matches)
media.addEventListener('change', (e) => (systemDark.value = e.matches))

const saved = readStored(KEY)
export const theme = ref<Theme>(saved === 'light' || saved === 'dark' ? saved : 'system')
export const isDark = computed(
  () => theme.value === 'dark' || (theme.value === 'system' && systemDark.value),
)

export function setTheme(value: Theme, origin?: { x: number; y: number }) {
  const style = document.documentElement.style
  if (origin) {
    style.setProperty('--swap-x', `${origin.x}px`)
    style.setProperty('--swap-y', `${origin.y}px`)
  } else {
    style.removeProperty('--swap-x')
    style.removeProperty('--swap-y')
  }
  writeStored(KEY, value)
  animateSwap('theme-swap', () => {
    theme.value = value
    document.documentElement.classList.toggle('dark', isDark.value)
  })
}

watchEffect(() => document.documentElement.classList.toggle('dark', isDark.value))
