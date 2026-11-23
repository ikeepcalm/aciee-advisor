<script setup lang="ts">
import DeadEndPage from '@/components/DeadEndPage.vue'
</script>

<template>
  <DeadEndPage code="403" :title="$t('nav.forbidden')">
    <RouterLink :to="{ name: 'home' }" class="btn btn-primary col-span-2 min-h-12 text-base">
      {{ $t('nav.home') }}
    </RouterLink>
  </DeadEndPage>
</template>
