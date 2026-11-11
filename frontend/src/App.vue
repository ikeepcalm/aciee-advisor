<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  BookOpen,
  CalendarDays,
  CircleUser,
  FileText,
  KeyRound,
  Moon,
  Settings,
  ShieldCheck,
  Star,
  Sun,
  Users,
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { LOCALE_NAMES, LOCALES, setLocale, type Locale } from '@/i18n'
import { isDark, setTheme, theme, type Theme } from '@/theme'
import BrandMark from '@/components/BrandMark.vue'
import SegmentedChips from '@/components/SegmentedChips.vue'
import AppSheet from '@/components/AppSheet.vue'

const { t, locale } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const accountOpen = ref(false)

watch(
  () => route.fullPath,
  () => (accountOpen.value = false),
)

const tabs = computed(() => {
  const items = [
    { to: '/courses', label: t('nav.courses'), icon: BookOpen },
    { to: '/timetable', label: t('nav.timetable'), icon: CalendarDays },
  ]
  if (auth.user) {
    items.push(
      { to: '/rate', label: t('nav.rate'), icon: Star },
      { to: '/group', label: t('nav.groups'), icon: Users },
    )
  }
  return items
})

const more = computed(() => {
  const user = auth.user
  if (!user) {
    return []
  }
  const items = [
    { to: '/me/submissions', label: t('nav.submissions'), icon: FileText },
    { to: '/me/recovery', label: t('recovery.title'), icon: KeyRound },
  ]
  if (user.roles.includes('moderator') || user.roles.includes('admin')) {
    items.push({ to: '/moderation', label: t('nav.moderation'), icon: ShieldCheck })
  }
  if (user.roles.includes('admin')) {
    items.push({ to: '/admin', label: t('nav.admin'), icon: Settings })
  }
  return items
})

const locales = LOCALES.map((value) => ({ value, label: LOCALE_NAMES[value] }))
const themes = computed(() =>
  (['system', 'light', 'dark'] as Theme[]).map((value) => ({ value, label: t(`prefs.${value}`) })),
)
const themeModel = computed({ get: () => theme.value, set: (v) => setTheme(v as Theme) })
const localeModel = computed({ get: () => locale.value, set: (v) => setLocale(v as Locale) })

function active(to: string) {
  return route.path.startsWith(to)
}

async function signOut() {
  await auth.signOut().catch(() => {})
  accountOpen.value = false
  await router.push('/')
}
</script>

<template>
  <div class="flex min-h-dvh flex-col">
    <header
      class="sticky top-0 z-30 border-b border-line bg-board/85 backdrop-blur supports-backdrop-filter:bg-board/70"
    >
      <div class="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4 md:px-6">
        <RouterLink to="/" class="flex min-h-11 items-center gap-2 text-ink no-underline">
          <BrandMark class="size-7" />
          <span class="display text-lg leading-none"
            >ACIEE <span class="text-mask">Advisor</span></span
          >
        </RouterLink>

        <nav class="ml-6 hidden items-center gap-1 md:flex" :aria-label="t('nav.menu')">
          <RouterLink
            v-for="tab in tabs"
            :key="tab.to"
            :to="tab.to"
            class="nav-tab relative flex min-h-11 items-center px-3 text-sm font-medium text-ink no-underline hover:text-mask"
            :class="{
              'text-mask after:absolute after:inset-x-3 after:bottom-1 after:h-0.5 after:bg-copper':
                active(tab.to),
            }"
            :aria-current="active(tab.to) ? 'page' : undefined"
          >
            {{ tab.label }}
          </RouterLink>
        </nav>

        <div class="ml-auto flex items-center gap-1">
          <label
            class="mono relative flex min-h-11 min-w-11 cursor-pointer items-center justify-center rounded-full border border-line bg-panel px-3 text-[0.6875rem] font-medium uppercase has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-copper"
          >
            <span aria-hidden="true">{{ locale }}</span>
            <select
              v-model="localeModel"
              :aria-label="t('prefs.language')"
              class="absolute inset-0 cursor-pointer appearance-none opacity-0"
            >
              <option v-for="l in locales" :key="l.value" :value="l.value" :lang="l.value">
                {{ l.label }}
              </option>
            </select>
          </label>
          <button
            class="btn btn-ghost btn-icon text-ink"
            :aria-label="t('prefs.theme') + ': ' + t(isDark ? 'prefs.light' : 'prefs.dark')"
            @click="setTheme(isDark ? 'light' : 'dark', { x: $event.clientX, y: $event.clientY })"
          >
            <Sun v-if="isDark" class="size-5" />
            <Moon v-else class="size-5" />
          </button>
          <button
            class="btn btn-ghost hidden text-ink md:inline-flex"
            :aria-expanded="accountOpen"
            @click="accountOpen = true"
          >
            <CircleUser class="size-5" />
            <span class="max-w-40 truncate">{{
              auth.user ? auth.user.email.split('@')[0] : t('auth.signIn')
            }}</span>
          </button>
        </div>
      </div>
    </header>

    <RouterView class="flex-1 pb-20 md:pb-0" />

    <nav
      class="fixed inset-x-0 bottom-0 z-30 grid border-t border-line bg-panel pb-[env(safe-area-inset-bottom)] md:hidden"
      :style="{ gridTemplateColumns: `repeat(${tabs.length + 1}, minmax(0, 1fr))` }"
      :aria-label="t('nav.menu')"
    >
      <RouterLink
        v-for="tab in tabs"
        :key="tab.to"
        :to="tab.to"
        class="nav-tab relative flex min-h-14 flex-col items-center justify-center gap-1 text-[0.6875rem] font-medium text-muted no-underline"
        :class="{
          'text-mask before:absolute before:inset-x-5 before:top-0 before:h-0.5 before:bg-copper':
            active(tab.to),
        }"
        :aria-current="active(tab.to) ? 'page' : undefined"
      >
        <component :is="tab.icon" class="size-5" />
        {{ tab.label }}
      </RouterLink>
      <button
        class="flex min-h-14 flex-col items-center justify-center gap-1 text-[0.6875rem] font-medium text-muted"
        :aria-expanded="accountOpen"
        @click="accountOpen = true"
      >
        <CircleUser class="size-5" />
        {{ auth.user ? t('nav.account') : t('auth.signIn') }}
      </button>
    </nav>

    <AppSheet v-model="accountOpen" :title="t('nav.account')">
      <template v-if="auth.user">
        <p class="mono text-xs break-all text-muted">
          {{ t('auth.signedInAs', { email: auth.user.email }) }}
        </p>
        <nav class="mt-3 grid gap-1">
          <RouterLink
            v-for="item in more"
            :key="item.to"
            :to="item.to"
            class="flex min-h-11 items-center gap-3 rounded-md px-2 text-sm font-medium text-ink no-underline hover:bg-board"
          >
            <component :is="item.icon" class="size-5 text-copper" />
            {{ item.label }}
          </RouterLink>
        </nav>
        <button class="btn btn-outline mt-3 w-full" @click="signOut">
          {{ t('auth.signOut') }}
        </button>
      </template>
      <template v-else>
        <p class="text-sm text-muted">{{ t('auth.signInLede') }}</p>
        <RouterLink :to="{ name: 'signin' }" class="btn btn-primary mt-4 w-full">{{
          t('auth.signInAction')
        }}</RouterLink>
        <RouterLink :to="{ name: 'signup' }" class="btn btn-outline mt-2 w-full">{{
          t('auth.signUp')
        }}</RouterLink>
      </template>
      <hr class="my-5 border-line" />
      <div class="field">
        {{ t('prefs.language') }}
        <SegmentedChips v-model="localeModel" :options="locales" />
      </div>
      <div class="field mt-4">
        {{ t('prefs.theme') }}
        <SegmentedChips v-model="themeModel" :options="themes" />
      </div>
    </AppSheet>
  </div>
</template>
