<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Copy, Download, Loader2 } from 'lucide-vue-next'
import AppNotice from '@/components/AppNotice.vue'
import { describeError } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { createMaster, localMaster, restoreMaster, vaultExists } from '@/crypto/tokens'
import { parseRecoveryCode, recoveryCode } from '@/crypto/vault'

const { t } = useI18n()
const router = useRouter()
const userId = useAuthStore().user!.id
const mode = ref<'loading' | 'show' | 'create' | 'restore'>('loading')
const code = ref('')
const saved = ref(false)
const input = ref('')
const error = ref('')
const busy = ref(false)
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

function copy() {
  navigator.clipboard.writeText(code.value).catch(() => {})
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

const confirmCreate = () =>
  act(async () => {
    await createMaster(userId, newMaster!)
    await router.push({ name: 'rate' })
  })

const restore = () =>
  act(async () => {
    const master = parseRecoveryCode(input.value)
    if (!master) {
      error.value = t('recovery.badFormat')
    } else if (!(await restoreMaster(userId, master))) {
      error.value = t('recovery.wrongCode')
    } else {
      await router.push({ name: 'rate' })
    }
  })
</script>

<template>
  <main class="page max-w-md">
    <span class="eyebrow">{{ t('rate.title') }}</span>
    <h1 class="h1">{{ t('recovery.title') }}</h1>
    <AppNotice v-if="error" kind="error">{{ error }}</AppNotice>
    <template v-if="mode === 'show' || mode === 'create'">
      <p class="lede text-sm">{{ t('recovery.yourCode') }}</p>
      <p
        class="mono mt-4 rounded-lg border border-dashed border-copper bg-panel p-4 text-center text-lg leading-relaxed font-medium [overflow-wrap:anywhere] select-all"
      >
        {{ code }}
      </p>
      <div class="mt-3 flex flex-wrap gap-2">
        <button class="btn btn-outline" @click="download">
          <Download class="size-4" aria-hidden="true" /> {{ t('recovery.download') }}
        </button>
        <button class="btn btn-ghost" @click="copy">
          <Copy class="size-4" aria-hidden="true" /> {{ t('recovery.copy') }}
        </button>
      </div>
      <p class="mt-5 text-sm text-muted">{{ t('recovery.explain') }}</p>
      <div v-if="mode === 'create'" class="card mt-6 grid gap-4">
        <label class="inline-flex items-center gap-3 text-sm font-medium">
          <input v-model="saved" type="checkbox" class="check" />
          {{ t('recovery.savedIt') }}
        </label>
        <button class="btn btn-primary w-full" :disabled="!saved || busy" @click="confirmCreate">
          <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
          {{ t('recovery.continue') }}
        </button>
      </div>
    </template>
    <form v-if="mode === 'restore'" class="card mt-6 grid gap-4" @submit.prevent="restore">
      <label class="field">
        {{ t('recovery.enterCode') }}
        <input
          v-model="input"
          class="input mono text-sm"
          placeholder="____-____-____-____-____-____-__"
          autocomplete="off"
        />
      </label>
      <button type="submit" class="btn btn-primary w-full" :disabled="busy">
        <Loader2 v-if="busy" class="size-4 animate-spin" aria-hidden="true" />
        {{ t('recovery.restore') }}
      </button>
      <details class="text-sm">
        <summary class="cursor-pointer font-medium text-mask">
          {{ t('recovery.lostTitle') }}
        </summary>
        <p class="mt-2 text-muted">{{ t('recovery.lostBody') }}</p>
      </details>
    </form>
  </main>
</template>
