import { computed, ref, watchEffect } from 'vue'

export type Theme = 'system' | 'light' | 'dark'

const KEY = 'theme'
const media = matchMedia('(prefers-color-scheme: dark)')
const systemDark = ref(media.matches)
media.addEventListener('change', (e) => (systemDark.value = e.matches))

function stored(): Theme {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'light' || value === 'dark' ? value : 'system'
  } catch {
    return 'system'
  }
}

export const theme = ref<Theme>(stored())
export const isDark = computed(
  () => theme.value === 'dark' || (theme.value === 'system' && systemDark.value),
)

export function setTheme(value: Theme) {
  theme.value = value
  try {
    localStorage.setItem(KEY, value)
  } catch {
    // storage blocked: the choice lasts until the tab closes
  }
}

watchEffect(() => document.documentElement.classList.toggle('dark', isDark.value))
