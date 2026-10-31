<template>
  <main>
    <p>{{ $t('nav.forbidden') }}</p>
    <RouterLink :to="{ name: 'home' }">{{ $t('nav.home') }}</RouterLink>
  </main>
</template>
