<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Check, Copy, Download, Loader2 } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import { describeError, safeNext } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { createMaster, localMaster, restoreMaster, vaultExists } from '@/crypto/tokens'
import { parseRecoveryCode, recoveryCode } from '@/crypto/vault'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userId = useAuthStore().user!.id
const mode = ref<'loading' | 'show' | 'create' | 'restore'>('loading')
const code = ref('')
const saved = ref(false)
const input = ref('')
const error = ref('')
const busy = ref(false)
const copied = ref(false)
let newMaster: Uint8Array<ArrayBuffer> | null = null

onMounted(async () => {
  try {
    const master = await localMaster(userId)
    if (master) {
      code.value = recoveryCode(master)
      mode.value = 'show'
    } else if (await vaultExists()) {
      mode.value = 'restore'
    } else {
      newMaster = crypto.getRandomValues(new Uint8Array(16))
      code.value = recoveryCode(newMaster)
      mode.value = 'create'
    }
  } catch (e) {
    error.value = describeError(t, e)
  }
})

function download() {
  const link = document.createElement('a')
  link.href = URL.createObjectURL(
    new Blob([`ACIEE Advisor\n${code.value}\n`], { type: 'text/plain' }),
  )
  link.download = 'aciee-cod-recuperare.txt'
  link.click()
  URL.revokeObjectURL(link.href)
}

async function copy() {
  try {
    await navigator.clipboard.writeText(code.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    copied.value = false
  }
}

async function act(action: () => Promise<void>) {
  error.value = ''
  busy.value = true
  try {
    await action()
  } catch (e) {
    error.value = describeError(t, e)
  } finally {
    busy.value = false
  }
}

const afterRecovery = () => router.push(safeNext(route.query, '/rate'))

const confirmCreate = () =>
  act(async () => {
    await createMaster(userId, newMaster!)
    await afterRecovery()
  })

const restore = () =>
  act(async () => {
    const master = parseRecoveryCode(input.value)
    if (!master) {
      error.value = t('recovery.badFormat')
    } else if (!(await restoreMaster(userId, master))) {
      error.value = t('recovery.wrongCode')
    } else {
      await afterRecovery()
    }
  })
</script>

<template>
  <main class="page-narrow">
    <RouterLink
      v-if="mode === 'show'"
      :to="{ name: 'rate' }"
      class="inline-flex min-h-11 items-center gap-1 self-start text-sm no-underline hover:underline"
    >
      <ArrowLeft class="size-4" aria-hidden="true" /> {{ t('rate.title') }}
    </RouterLink>
    <h1 class="h1">{{ mode === 'create' ? t('recovery.saveTitle') : t('recovery.title') }}</h1>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <p v-if="mode === 'loading' && !error" class="lede flex items-center gap-2 text-sm">
      <Loader2 class="size-4 animate-spin" aria-hidden="true" /> {{ t('common.loading') }}
    </p>
    <template v-if="mode === 'show' || mode === 'create'">
      <p class="lede text-sm">{{ t('recovery.why') }}</p>
      <p
        class="mono mt-4 rounded-lg border border-dashed border-copper bg-panel p-4 text-center text-lg leading-relaxed font-medium [overflow-wrap:anywhere] select-all"
      >
        {{ code }}
      </p>
      <div class="mt-3 flex flex-wrap gap-2">
        <button class="btn btn-outline flex-1 whitespace-nowrap" @click="download">
          <Download class="size-4 shrink-0" aria-hidden="true" /> {{ t('recovery.download') }}
        </button>
        <button class="btn btn-outline flex-1 whitespace-nowrap" @click="copy">
          <component :is="copied ? Check : Copy" class="size-4 shrink-0" aria-hidden="true" />
          <span aria-live="polite">{{ copied ? t('recovery.copied') : t('recovery.copy') }}</span>
        </button>
      </div>
      <div v-if="mode === 'create'" class="mt-6 grid gap-3">
        <label class="card flex min-h-14 cursor-pointer items-center gap-3 text-sm font-medium">
          <input v-model="saved" type="checkbox" class="check" />
          {{ t('recovery.savedIt') }}
        </label>
        <button class="btn btn-primary w-full" :disabled="!saved || busy" @click="confirmCreate">
          <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
          {{ t('recovery.continue') }}
        </button>
      </div>
    </template>
    <template v-if="mode === 'restore'">
      <p class="lede text-sm">{{ t('recovery.restoreWhy') }}</p>
      <form class="card mt-6 grid gap-4" @submit.prevent="restore">
        <label class="field">
          {{ t('recovery.yourCode') }}
          <input
            v-model="input"
            class="input mono text-sm"
            placeholder="____-____-____-____-____-____-__"
            autocomplete="off"
            autocapitalize="characters"
            spellcheck="false"
            required
          />
        </label>
        <button type="submit" class="btn btn-primary w-full" :disabled="busy">
          <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
          {{ t('recovery.restore') }}
        </button>
        <details class="text-sm">
          <summary class="flex min-h-11 cursor-pointer items-center font-medium text-mask">
            {{ t('recovery.lostTitle') }}
          </summary>
          <p class="text-muted">{{ t('recovery.lostBody') }}</p>
        </details>
      </form>
    </template>
  </main>
</template>
