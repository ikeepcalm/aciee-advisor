<template>
  <main class="page mx-auto max-w-md pt-12 text-center md:pt-24">
    <p class="mono text-6xl font-medium text-copper">403</p>
    <h1 class="mt-4 font-sans text-base font-normal">{{ $t('nav.forbidden') }}</h1>
    <RouterLink :to="{ name: 'home' }" class="btn btn-primary mt-6">{{
      $t('nav.home')
    }}</RouterLink>
  </main>
</template>
