<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
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
  <main>
    <h1>{{ t('recovery.title') }}</h1>
    <Message v-if="error" severity="error">{{ error }}</Message>
    <template v-if="mode === 'show' || mode === 'create'">
      <p>{{ t('recovery.yourCode') }}</p>
      <p class="code">{{ code }}</p>
      <p>{{ t('recovery.explain') }}</p>
      <p>
        <Button :label="t('recovery.download')" severity="secondary" @click="download" />
        <Button :label="t('recovery.copy')" severity="secondary" link @click="copy" />
      </p>
      <template v-if="mode === 'create'">
        <p>
          <Checkbox v-model="saved" input-id="saved" binary />
          <label for="saved"> {{ t('recovery.savedIt') }}</label>
        </p>
        <Button
          :label="t('recovery.continue')"
          :disabled="!saved"
          :loading="busy"
          fluid
          @click="confirmCreate"
        />
      </template>
    </template>
    <form v-if="mode === 'restore'" @submit.prevent="restore">
      <p>{{ t('recovery.enterCode') }}</p>
      <InputText
        v-model="input"
        placeholder="____-____-____-____-____-____-__"
        autocomplete="off"
        fluid
      />
      <Button type="submit" :label="t('recovery.restore')" :loading="busy" fluid />
      <details>
        <summary>{{ t('recovery.lostTitle') }}</summary>
        <p>{{ t('recovery.lostBody') }}</p>
      </details>
    </form>
  </main>
</template>

<style scoped>
.code {
  font-family: ui-monospace, monospace;
  font-size: 1.25rem;
  letter-spacing: 0.05em;
  padding: 0.75rem;
  border: 1px solid currentColor;
  border-radius: 0.5rem;
  text-align: center;
  word-break: break-all;
}

form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
</style>
