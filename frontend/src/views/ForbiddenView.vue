<template>
  <main class="page max-w-md text-center">
    <p class="mono text-6xl font-medium text-copper">403</p>
    <p class="mt-4">{{ $t('nav.forbidden') }}</p>
    <RouterLink :to="{ name: 'home' }" class="btn btn-outline mt-6">{{
      $t('nav.home')
    }}</RouterLink>
  </main>
</template>
