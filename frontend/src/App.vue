<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  BookOpen,
  CalendarDays,
  CircleUser,
  FileText,
  GraduationCap,
  KeyRound,
  MessageSquareWarning,
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
import UserAvatar from '@/components/UserAvatar.vue'
import AppNotice from '@/components/AppNotice.vue'
import { shortName } from '@/person'
import { describeError } from '@/api'

const { t, locale } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const accountOpen = ref(false)
const nameDraft = ref('')
const nameNote = ref('')
const nameError = ref('')

watch(accountOpen, (open) => {
  if (!open) return
  nameDraft.value = auth.user?.displayName ?? ''
  nameNote.value = ''
  nameError.value = ''
})

async function saveName() {
  nameNote.value = ''
  nameError.value = ''
  try {
    await auth.rename(nameDraft.value)
    nameNote.value = t('auth.nameSaved')
  } catch (e) {
    nameError.value = describeError(t, e)
  }
}

watch(
  () => route.fullPath,
  () => (accountOpen.value = false),
)

const tabs = computed(() => {
  const items = [
    { to: '/courses', label: t('nav.courses'), icon: BookOpen },
    { to: '/professors', label: t('nav.professors'), icon: GraduationCap },
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

const feedbackLink = computed(() => ({
  name: 'feedback',
  query: { from: route.name === 'feedback' ? route.query.from : route.fullPath },
}))

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
  <div class="flex min-h-svh flex-col">
    <header
      class="sticky top-0 z-30 border-b border-line bg-board md:bg-board/85 md:backdrop-blur md:supports-backdrop-filter:bg-board/70"
    >
      <div class="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4 md:px-6">
        <RouterLink
          to="/"
          class="flex min-h-11 shrink-0 items-center gap-2 whitespace-nowrap text-ink no-underline"
        >
          <BrandMark class="size-7" />
          <span class="display text-lg leading-none"
            >ACIEE <span class="text-mask">Advisor</span></span
          >
        </RouterLink>

        <nav class="ml-2 hidden items-center md:flex lg:ml-6 lg:gap-1" :aria-label="t('nav.menu')">
          <RouterLink
            v-for="tab in tabs"
            :key="tab.to"
            :to="tab.to"
            class="nav-tab relative flex min-h-11 items-center px-2 text-sm font-medium whitespace-nowrap text-ink no-underline hover:text-mask lg:px-3"
            :class="{
              'text-mask after:absolute after:inset-x-2 after:bottom-1 lg:after:inset-x-3 after:h-0.5 after:bg-copper':
                active(tab.to),
            }"
            :aria-current="active(tab.to) ? 'page' : undefined"
          >
            <span :class="{ hl: tab.to === '/rate' }">{{ tab.label }}</span>
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
              class="absolute -inset-px cursor-pointer appearance-none opacity-0"
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
            class="btn btn-ghost hidden min-w-11 px-2 text-ink md:inline-flex lg:px-4"
            :aria-expanded="accountOpen"
            @click="accountOpen = true"
          >
            <UserAvatar
              v-if="auth.user"
              :name="auth.user.displayName"
              :email="auth.user.email"
              class="size-7 text-[0.6875rem]"
            />
            <CircleUser v-else class="size-5" />
            <span class="sr-only lg:hidden">{{
              auth.user ? t('nav.account') : t('auth.signIn')
            }}</span>
            <span class="hidden max-w-40 truncate lg:inline">{{
              auth.user ? shortName(auth.user.displayName, auth.user.email) : t('auth.signIn')
            }}</span>
          </button>
        </div>
      </div>
    </header>

    <RouterView class="flex-1" />

    <footer
      class="border-t border-dashed border-line pb-[calc(5rem+env(safe-area-inset-bottom))] md:pb-0"
    >
      <div
        class="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-x-6 gap-y-1 px-4 py-4 text-xs text-muted md:px-6"
      >
        <span>{{ t('feedback.footerNote') }}</span>
        <RouterLink
          :to="feedbackLink"
          class="inline-flex min-h-11 items-center gap-1.5 font-semibold text-mask"
        >
          <MessageSquareWarning class="size-4" aria-hidden="true" />
          {{ t('feedback.footerLink') }}
        </RouterLink>
      </div>
    </footer>

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
        <span
          class="flex items-center justify-center"
          :class="{ 'rate-blob bg-mark px-3 py-0.5 text-on-mark': tab.to === '/rate' }"
        >
          <component :is="tab.icon" class="size-5" />
        </span>
        <span class="max-w-full truncate px-0.5">{{ tab.label }}</span>
      </RouterLink>
      <button
        class="flex min-h-14 flex-col items-center justify-center gap-1 text-[0.6875rem] font-medium text-muted"
        :aria-expanded="accountOpen"
        @click="accountOpen = true"
      >
        <UserAvatar
          v-if="auth.user"
          :name="auth.user.displayName"
          :email="auth.user.email"
          class="size-5 text-[0.5625rem]"
        />
        <CircleUser v-else class="size-5" />
        <span class="max-w-full truncate px-0.5">{{
          auth.user ? t('nav.account') : t('auth.signIn')
        }}</span>
      </button>
    </nav>

    <AppSheet v-model="accountOpen" :title="t('nav.account')">
      <template v-if="auth.user">
        <div class="flex items-center gap-3">
          <UserAvatar
            :name="auth.user.displayName"
            :email="auth.user.email"
            class="size-10 text-sm"
          />
          <div class="min-w-0">
            <div class="font-semibold [overflow-wrap:anywhere]">
              {{ shortName(auth.user.displayName, auth.user.email) }}
            </div>
            <p class="mono text-xs break-all text-muted">
              {{ t('auth.signedInAs', { email: auth.user.email }) }}
            </p>
          </div>
        </div>
        <form class="field mt-4" @submit.prevent="saveName">
          <label for="display-name">{{ t('auth.displayName') }}</label>
          <div class="flex gap-2">
            <input
              id="display-name"
              v-model="nameDraft"
              class="input min-w-0 flex-1"
              maxlength="80"
              autocomplete="name"
              :placeholder="t('auth.displayNamePlaceholder')"
            />
            <button type="submit" class="btn btn-outline">{{ t('auth.saveName') }}</button>
          </div>
          <span class="text-xs font-normal text-muted">{{
            nameNote || t('auth.displayNameHint')
          }}</span>
          <AppNotice v-if="nameError" kind="error" class="my-0">{{ nameError }}</AppNotice>
        </form>
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
      <RouterLink
        :to="feedbackLink"
        class="mt-3 flex min-h-11 items-center gap-3 rounded-md px-2 text-sm font-medium text-ink no-underline hover:bg-board"
        @click="accountOpen = false"
      >
        <MessageSquareWarning class="size-5 text-copper" />
        {{ t('feedback.footerLink') }}
      </RouterLink>
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

<style scoped>
.rate-blob {
  border-radius: 47% 53% 44% 56% / 55% 46% 54% 45%;
}
</style>
