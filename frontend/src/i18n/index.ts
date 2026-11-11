import { nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
import { readStored, writeStored } from '@/storage'
import { animateSwap } from '@/viewTransition'
import ro from './ro.json'

export const LOCALES = ['ro', 'en', 'uk', 'ru'] as const
export type Locale = (typeof LOCALES)[number]

export const LOCALE_NAMES: Record<Locale, string> = {
  ro: 'Română',
  en: 'English',
  uk: 'Українська',
  ru: 'Русский',
}

const KEY = 'locale'
const short = { short: { day: 'numeric', month: 'short', year: 'numeric' } } as const
const loaders: Record<Exclude<Locale, 'ro'>, () => Promise<{ default: typeof ro }>> = {
  en: () => import('./en.json'),
  uk: () => import('./uk.json'),
  ru: () => import('./ru.json'),
}

const saved = readStored(KEY)
const initial: Locale = LOCALES.includes(saved as Locale) ? (saved as Locale) : 'ro'

export const i18n = createI18n({
  legacy: false,
  locale: initial,
  fallbackLocale: 'ro',
  messages: { ro } as Record<Locale, typeof ro>,
  datetimeFormats: { ro: short, en: short, uk: short, ru: short },
})

async function loadMessages(locale: Locale) {
  if (locale === 'ro' || i18n.global.availableLocales.includes(locale)) return
  i18n.global.setLocaleMessage(locale, (await loaders[locale]()).default)
}

export const localeReady = loadMessages(initial).catch(() => {})

export async function setLocale(locale: Locale) {
  if (i18n.global.locale.value === locale) return
  const loaded = await loadMessages(locale).then(
    () => true,
    () => false,
  )
  if (!loaded) return
  writeStored(KEY, locale)
  animateSwap('locale-swap', async () => {
    i18n.global.locale.value = locale
    document.documentElement.lang = locale
    await nextTick()
  })
}

document.documentElement.lang = initial
