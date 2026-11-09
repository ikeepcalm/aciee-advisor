import { createI18n } from 'vue-i18n'
import ro from './ro.json'
import en from './en.json'

export type Locale = 'ro' | 'en'

const KEY = 'locale'
const short = { short: { day: 'numeric', month: 'short', year: 'numeric' } } as const

function stored(): Locale | null {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'ro' || value === 'en' ? value : null
  } catch {
    return null
  }
}

export const i18n = createI18n({
  legacy: false,
  locale: stored() ?? (navigator.language.toLowerCase().startsWith('ro') ? 'ro' : 'en'),
  fallbackLocale: 'ro',
  messages: { ro, en },
  datetimeFormats: { ro: short, en: short },
})

export function setLocale(locale: Locale) {
  i18n.global.locale.value = locale
  document.documentElement.lang = locale
  try {
    localStorage.setItem(KEY, locale)
  } catch {
    // storage blocked: the choice lasts until the tab closes
  }
}

document.documentElement.lang = i18n.global.locale.value
