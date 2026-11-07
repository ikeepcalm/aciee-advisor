<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import { api } from '@/api'

const { t } = useI18n()
const enabled = ref(false)

onMounted(async () => {
  enabled.value = await api<{ microsoft: boolean }>('GET', '/api/auth/providers').then(
    (providers) => providers.microsoft,
    () => false,
  )
})
</script>

<template>
  <template v-if="enabled">
    <p class="or">{{ t('auth.or') }}</p>
    <Button as="a" href="/api/auth/oauth2/microsoft" :label="t('auth.microsoft')" severity="secondary" outlined fluid />
  </template>
</template>

<style scoped>
.or {
  text-align: center;
  opacity: 0.7;
  margin: 1rem 0 0.5rem;
}
</style>
